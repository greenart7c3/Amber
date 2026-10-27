package com.greenart7c3.nostrsigner

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SecureCryptoHelper {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    // The wrapping key alternates between two aliases. Rotation creates the
    // new key under whichever alias is free, so the key that protects the
    // existing ciphertext is never deleted until every secret has been
    // durably re-encrypted (GHSA-wv7c-hq2j-ppc5). The alias in use is
    // recorded by LocalPreferences.commitActiveKeystoreAlias; installs that
    // predate it have never rotated and use KEY_ALIAS.
    private const val KEY_ALIAS = "AMBER_AES_KEY"
    private const val ALT_KEY_ALIAS = "AMBER_AES_KEY_B"
    private const val TAG = "SecureCryptoHelper"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12 // 96 bits
    private const val TAG_SIZE = 128 // bits
    private const val KEY_CHECK_PROBE = "amber-key-check"

    // A sweep that rewrote anything is repeated, so a value written with the
    // previous key while the sweep was running is still picked up.
    private const val MAX_SWEEP_PASSES = 3
    private val mutex = Mutex()

    /**
     * The key new ciphertext is written with ([key], stored under [alias]),
     * plus the other alias's key as a decrypt-only [fallback]. The fallback is
     * only non-null while a rotation is unfinished: values not yet moved to
     * the new key stay readable through it.
     */
    private class KeyHandle(val alias: String, val key: SecretKey, val fallback: SecretKey?)

    /**
     * Cached [KeyHandle]. An AndroidKeyStore [SecretKey] is a lightweight
     * reference (the material never leaves the TEE/StrongBox), but fetching
     * it costs a fresh KeyStore load plus `containsAlias`/`getEntry`
     * binder round-trips to the keystore daemon — milliseconds each,
     * sequential. Re-fetching it per cipher operation made every
     * envelope-encrypted DAO read pay a keystore lookup per field
     * (see ApplicationEntityCrypto.kt), which dominated load times.
     * Replaced by [rotateKey] and invalidated by [withFreshKeyRetry] when a
     * cached handle is found unusable.
     */
    @Volatile
    private var cachedHandle: KeyHandle? = null

    suspend fun encrypt(plainText: String): String = mutex.withLock {
        withFreshKeyRetry { handle -> encryptWithKey(handle.key, plainText) }
    }

    suspend fun decrypt(encryptedText: String): String = mutex.withLock {
        withFreshKeyRetry { handle -> decryptWithHandle(handle, encryptedText) }
    }

    /**
     * Non-suspending equivalent of [encrypt] for callers that cannot suspend
     * (Room [androidx.room.migration.Migration.migrate] callbacks, [com.greenart7c3.nostrsigner.database.ApplicationDao.getByKeySync] synchronous DAO
     * reads). The AndroidKeyStore Cipher instance is thread-safe to obtain and
     * use; the suspend variant's [mutex] only guards against concurrent
     * in-flight cipher init within coroutines and is intentionally omitted
     * here so blocking callers do not need a [kotlinx.coroutines.runBlocking]
     * bridge.
     */
    fun encryptBlocking(plainText: String): String = withFreshKeyRetry { handle -> encryptWithKey(handle.key, plainText) }

    private fun encryptWithKey(key: SecretKey, plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv

        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val combined = ByteBuffer.allocate(iv.size + cipherText.size)
        combined.put(iv)
        combined.put(cipherText)

        return Base64.encodeToString(combined.array(), Base64.NO_WRAP)
    }

    /**
     * Non-suspending equivalent of [decrypt]. See [encryptBlocking] for the
     * rationale.
     */
    fun decryptBlocking(encryptedText: String): String = withFreshKeyRetry { handle -> decryptWithHandle(handle, encryptedText) }

    private fun decryptWithHandle(handle: KeyHandle, encryptedText: String): String = try {
        decryptWithKey(handle.key, encryptedText)
    } catch (e: AEADBadTagException) {
        // Not written with the active key; during an unfinished rotation it
        // may still be under the previous one.
        decryptWithKey(handle.fallback ?: throw e, encryptedText)
    }

    private fun decryptWithKey(key: SecretKey, encryptedText: String): String {
        val data = Base64.decode(encryptedText, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(data)

        val iv = ByteArray(IV_SIZE).also { buffer.get(it) }
        val cipherText = ByteArray(buffer.remaining()).also { buffer.get(it) }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_SIZE, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val plainBytes = cipher.doFinal(cipherText)
        return String(plainBytes, Charsets.UTF_8)
    }

    /**
     * Runs [block] with the current key handle, retrying exactly once with a
     * re-fetched handle when the cached one has gone stale (the entry was
     * deleted or invalidated underneath us — e.g. a concurrent [rotateKey]
     * removing the previous key, or the opt-in unlocked-device policy
     * refusing key use while locked).
     * Data errors — chiefly GCM's AEADBadTagException for corrupt or
     * wrong-key ciphertext — are not stale-handle errors and propagate to the
     * caller on the first attempt, preserving the decryptField failure
     * contract in ApplicationEntityCrypto.kt.
     */
    private inline fun <T> withFreshKeyRetry(block: (KeyHandle) -> T): T {
        try {
            return block(getOrLoadHandle())
        } catch (e: InvalidKeyException) {
            AmberLog.w(TAG, "Cached Keystore key unusable, re-fetching once", e)
        } catch (e: KeyStoreException) {
            AmberLog.w(TAG, "Keystore failure with cached key, re-fetching once", e)
        } catch (e: UnrecoverableKeyException) {
            AmberLog.w(TAG, "Cached Keystore key unrecoverable, re-fetching once", e)
        } catch (e: ProviderException) {
            AmberLog.w(TAG, "Keystore provider failure, re-fetching key once", e)
        }
        cachedHandle = null
        return block(getOrLoadHandle())
    }

    private fun getOrLoadHandle(): KeyHandle {
        cachedHandle?.let { return it }
        return synchronized(this) {
            cachedHandle?.let { return it }
            val handle = loadHandle()
            cachedHandle = handle
            handle
        }
    }

    private fun loadHandle(): KeyHandle {
        val context = Amber.instance
        val keyStore = getKeyStore()
        val alias = resolveActiveAlias(context, keyStore)
        val key = keyStore.secretKey(alias) ?: generateSecretKey(alias, LocalPreferences.getRequireUnlockedDevice(context))
        return KeyHandle(alias, key, keyStore.secretKey(otherAlias(alias)))
    }

    /**
     * The alias new ciphertext must be written with. Normally the recorded
     * one; if that key is gone but the other alias still holds a key, adopt
     * the survivor rather than letting [loadHandle] mint a fresh key, which
     * would strand everything encrypted under the survivor.
     */
    private fun resolveActiveAlias(context: Context, keyStore: KeyStore): String {
        val recorded = if (LocalPreferences.getActiveKeystoreAlias(context) == ALT_KEY_ALIAS) ALT_KEY_ALIAS else KEY_ALIAS
        val other = otherAlias(recorded)
        if (!keyStore.containsAlias(recorded) && keyStore.containsAlias(other)) {
            AmberLog.w(TAG, "Active Keystore alias $recorded is missing, adopting $other")
            if (!LocalPreferences.commitActiveKeystoreAlias(context, other)) {
                AmberLog.w(TAG, "Failed to persist adopted Keystore alias $other")
            }
            return other
        }
        return recorded
    }

    private fun otherAlias(alias: String): String = if (alias == ALT_KEY_ALIAS) KEY_ALIAS else ALT_KEY_ALIAS

    private fun KeyStore.secretKey(alias: String): SecretKey? = (getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun getKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun generateSecretKey(alias: String, requireUnlockedDevice: Boolean): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val paramsBuilder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

        // Defense-in-depth (GHSA-8844-q5vh-9j8f, L1): opt-in toggle — when
        // enabled, require the device to be unlocked at the time the key
        // material is used, so a process that runs while the screen is
        // locked cannot decrypt stored account keys. Does not prompt for
        // biometrics per operation (which would break background NIP-46
        // signing); it only refuses key use while the device is locked.
        // Available from API 28 (KeyGenParameterSpec.Builder).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && requireUnlockedDevice) {
            paramsBuilder.setUnlockedDeviceRequired(true)
            try {
                if (Amber.instance.hasStrongBox()) {
                    paramsBuilder.setIsStrongBoxBacked(true)
                }
                keyGenerator.init(paramsBuilder.build())
                return keyGenerator.generateKey()
            } catch (e: Exception) {
                AmberLog.w(TAG, "StrongBox generation failed, falling back to TEE", e)
                paramsBuilder.setIsStrongBoxBacked(false)
                keyGenerator.init(paramsBuilder.build())
                return keyGenerator.generateKey()
            }
        } else {
            keyGenerator.init(paramsBuilder.build())
            return keyGenerator.generateKey()
        }
    }

    /**
     * Replaces the wrapping key with one generated under the requested
     * policy, re-encrypting every stored secret (per-account DataStore keys,
     * app DataStore PIN, WebDAV password, and the per-account Room
     * `application` tables' envelope-encrypted `secret` / `localKey` columns —
     * see `ApplicationEntityCrypto.kt`).
     *
     * The Keystore `setUnlockedDeviceRequired` flag is set at key-generation
     * time, so changing the opt-in policy needs a new key. No step deletes a
     * key that still protects data (GHSA-wv7c-hq2j-ppc5):
     *
     * 1. The new key is generated under the free alias; the old one is untouched.
     * 2. One synchronous preferences commit makes the new alias active and
     *    records the policy. Before it lands nothing has changed; after it,
     *    new writes use the new key and reads fall back to the old one.
     * 3. [finishRotation] moves each value to the new key with
     *    compare-and-set writes, and deletes the old key only after a full
     *    pass finds nothing left to move and nothing it failed to read.
     *
     * If step 3 cannot finish (process death, the keystore refusing a key),
     * both keys remain and [completePendingRotation] resumes at next start.
     * Throws only before step 2, leaving everything as it was.
     *
     * Uses internal non-locking cipher methods ([encryptWithKey]/
     * [decryptWithKey]) and raw DataStore/DAO helpers to avoid mutex
     * reentrancy (Kotlin's [Mutex] is not reentrant).
     */
    suspend fun rotateKey(context: Context, requireUnlockedDevice: Boolean): Unit = mutex.withLock {
        val keyStore = getKeyStore()
        val oldAlias = resolveActiveAlias(context, keyStore)
        val newAlias = otherAlias(oldAlias)
        val oldKey = keyStore.secretKey(oldAlias)
        if (oldKey == null) {
            // No key under either alias, so nothing is encrypted yet.
            val key = generateSecretKey(oldAlias, requireUnlockedDevice)
            if (!LocalPreferences.commitActiveKeystoreAlias(context, oldAlias, requireUnlockedDevice)) {
                throw IOException("Failed to persist the key policy")
            }
            cachedHandle = KeyHandle(oldAlias, key, null)
            return@withLock
        }

        // An earlier rotation never finished, so the free alias may still hold
        // the key for some values. It can only be replaced once nothing needs it.
        if (keyStore.containsAlias(newAlias)) {
            check(finishRotation(context, keyStore, oldAlias, oldKey, newAlias)) {
                "A previous key rotation is still incomplete"
            }
        }

        // 1. Generate the replacement and prove it works before relying on it.
        val newKey = try {
            generateSecretKey(newAlias, requireUnlockedDevice).also { key ->
                check(decryptWithKey(key, encryptWithKey(key, KEY_CHECK_PROBE)) == KEY_CHECK_PROBE) { "New key failed a round trip" }
            }
        } catch (e: Exception) {
            discardAlias(keyStore, newAlias)
            throw e
        }

        // 2. Switch. commit() updates the in-memory values even when the disk
        // write fails, so restore them before discarding the new key.
        val previousPolicy = LocalPreferences.getRequireUnlockedDevice(context)
        if (!LocalPreferences.commitActiveKeystoreAlias(context, newAlias, requireUnlockedDevice)) {
            LocalPreferences.commitActiveKeystoreAlias(context, oldAlias, previousPolicy)
            discardAlias(keyStore, newAlias)
            throw IOException("Failed to persist the new active key alias")
        }
        cachedHandle = KeyHandle(newAlias, newKey, oldKey)

        // 3. Move everything over and drop the old key.
        if (finishRotation(context, keyStore, newAlias, newKey, oldAlias)) {
            AmberLog.d(TAG, "Key rotation complete (requireUnlockedDevice=$requireUnlockedDevice)")
        } else {
            AmberLog.w(TAG, "Key rotation switched keys but kept the previous one; it will be retried at next start")
        }
    }

    /**
     * Resumes a rotation that was interrupted after the switch (or abandoned
     * before it), so the previous key does not outlive its data. Safe to call
     * on every start: it does nothing when only one alias exists.
     */
    suspend fun completePendingRotation(context: Context): Unit = mutex.withLock {
        val keyStore = getKeyStore()
        val activeAlias = resolveActiveAlias(context, keyStore)
        val previousAlias = otherAlias(activeAlias)
        if (!keyStore.containsAlias(previousAlias)) return@withLock
        val activeKey = keyStore.secretKey(activeAlias) ?: return@withLock
        if (finishRotation(context, keyStore, activeAlias, activeKey, previousAlias)) {
            AmberLog.d(TAG, "Completed a pending key rotation")
        } else {
            AmberLog.w(TAG, "Pending key rotation still incomplete; keeping the previous key")
        }
    }

    /**
     * Moves every value still encrypted under [previousAlias] onto
     * [activeKey], then deletes [previousAlias]. Returns true once it is
     * deleted; false (deleting nothing) if any value could not be checked or
     * moved, or values kept moving for [MAX_SWEEP_PASSES] passes.
     */
    private suspend fun finishRotation(
        context: Context,
        keyStore: KeyStore,
        activeAlias: String,
        activeKey: SecretKey,
        previousAlias: String,
    ): Boolean {
        val previousKey = keyStore.secretKey(previousAlias)
        if (previousKey != null) {
            // Sweeps until a pass changes nothing, at most MAX_SWEEP_PASSES times.
            val settled = (1..MAX_SWEEP_PASSES).any {
                val result = try {
                    sweep(context, activeKey, previousKey)
                } catch (e: Exception) {
                    AmberLog.w(TAG, "Key rotation: sweep failed", e)
                    return false
                }
                if (result.failed > 0) {
                    AmberLog.w(TAG, "Key rotation: ${result.failed} value(s) could not be moved to the new key")
                    return false
                }
                result.changed == 0
            }
            if (!settled) return false
        }
        keyStore.deleteEntry(previousAlias)
        cachedHandle = KeyHandle(activeAlias, activeKey, null)
        return true
    }

    private class SweepResult {
        var changed = 0
        var failed = 0
    }

    /** One pass over every stored ciphertext; see [migrateCiphertext]. */
    private suspend fun sweep(context: Context, current: SecretKey, previous: SecretKey): SweepResult {
        val result = SweepResult()

        suspend fun move(raw: String?, replace: suspend (expected: String, ciphertext: String) -> Boolean) {
            if (raw.isNullOrEmpty()) return
            val migration = migrateCiphertext(
                raw,
                decryptCurrent = { decryptWithKey(current, it) },
                decryptPrevious = { decryptWithKey(previous, it) },
                encryptCurrent = { encryptWithKey(current, it) },
            )
            when (migration) {
                CiphertextMigration.Current -> Unit
                CiphertextMigration.Unreadable -> AmberLog.w(TAG, "Key rotation: skipping a value neither key can decrypt")
                is CiphertextMigration.Failed -> {
                    result.failed++
                    AmberLog.w(TAG, "Key rotation: could not check a value", migration.error)
                }
                is CiphertextMigration.Rewrite -> {
                    // Counted even when the compare-and-set loses to a
                    // concurrent write, so another pass re-checks that value.
                    result.changed++
                    try {
                        replace(raw, migration.ciphertext)
                    } catch (e: Exception) {
                        result.failed++
                        AmberLog.w(TAG, "Key rotation: failed to write a value", e)
                    }
                }
            }
        }

        val accounts = LocalPreferences.allSavedAccounts(context)
        for ((npub) in accounts) {
            for (key in listOf(DataStoreAccess.NOSTR_PRIVKEY, DataStoreAccess.SEED_WORDS)) {
                move(DataStoreAccess.getEncryptedRaw(context, npub, key)) { expected, ciphertext ->
                    DataStoreAccess.replaceEncryptedRaw(context, npub, key, expected, ciphertext)
                }
            }
        }
        move(DataStoreAccess.getPinRaw(context)) { expected, ciphertext ->
            DataStoreAccess.replacePinRaw(context, expected, ciphertext)
        }
        move(LocalPreferences.getWebDavPasswordEncrypted(context)) { expected, ciphertext ->
            LocalPreferences.replaceWebDavPasswordEncrypted(context, expected, ciphertext)
        }
        // Per-account Room `application` tables (GHSA-5fjp-ghh8-wch8).
        for ((npub) in accounts) {
            val dao = Amber.instance.dao(npub)
            for (row in dao.getAllApplicationsRaw()) {
                move(row.secret) { expected, ciphertext -> dao.replaceSecretRaw(row.key, expected, ciphertext) > 0 }
                move(row.localKey) { expected, ciphertext -> dao.replaceLocalKeyRaw(row.key, expected, ciphertext) > 0 }
            }
        }
        return result
    }

    internal sealed interface CiphertextMigration {
        /** Already readable with the current key. */
        data object Current : CiphertextMigration

        /** Neither key can decrypt it, so deleting the previous key loses nothing. */
        data object Unreadable : CiphertextMigration

        data class Rewrite(val ciphertext: String) : CiphertextMigration

        /** A key could not be used right now; the previous key must be kept. */
        data class Failed(val error: Exception) : CiphertextMigration
    }

    /**
     * Decides what one stored value needs during a rotation. Only an error
     * that proves the ciphertext does not belong to a key (GCM tag mismatch,
     * malformed data) lets the sweep move on; any other failure — the device
     * is locked under the unlocked-device policy, a keystore error — is
     * [CiphertextMigration.Failed], which keeps the previous key alive.
     */
    internal fun migrateCiphertext(
        raw: String,
        decryptCurrent: (String) -> String,
        decryptPrevious: (String) -> String,
        encryptCurrent: (String) -> String,
    ): CiphertextMigration {
        try {
            decryptCurrent(raw)
            return CiphertextMigration.Current
        } catch (e: Exception) {
            if (!e.isWrongKeyOrMalformed()) return CiphertextMigration.Failed(e)
        }
        val plainText = try {
            decryptPrevious(raw)
        } catch (e: Exception) {
            return if (e.isWrongKeyOrMalformed()) CiphertextMigration.Unreadable else CiphertextMigration.Failed(e)
        }
        return try {
            CiphertextMigration.Rewrite(encryptCurrent(plainText))
        } catch (e: Exception) {
            CiphertextMigration.Failed(e)
        }
    }

    private fun Exception.isWrongKeyOrMalformed(): Boolean = this is AEADBadTagException ||
        this is IllegalArgumentException ||
        this is BufferUnderflowException

    private fun discardAlias(keyStore: KeyStore, alias: String) {
        try {
            keyStore.deleteEntry(alias)
        } catch (e: Exception) {
            AmberLog.w(TAG, "Failed to delete unused Keystore alias $alias", e)
        }
    }
}

fun Context.hasStrongBox(): Boolean {
    val isMediaTek = Build.HARDWARE.lowercase().contains("mt") ||
        Build.BOARD.lowercase().contains("mt") ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.SOC_MANUFACTURER.lowercase().contains("mediatek")) // usually mediatek contains broken strongbox support

    return !isMediaTek &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
}
