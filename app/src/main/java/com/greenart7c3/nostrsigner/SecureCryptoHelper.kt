package com.greenart7c3.nostrsigner

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.greenart7c3.nostrsigner.data.AccountRepository
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
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SecureCryptoHelper {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    private lateinit var appContext: Context

    private lateinit var accountRepository: AccountRepository

    /**
     * Called from Amber's constructor, before anything can reach the key: Room converters
     * and DAOs encrypt without a context at hand. [application] is the Application itself,
     * whose base context (and so its applicationContext) is not attached yet at that point.
     */
    fun install(application: Context, accounts: AccountRepository) {
        appContext = application
        accountRepository = accounts
    }

    private const val FIRST_KEY_ALIAS = "AMBER_AES_KEY"
    private const val SECOND_KEY_ALIAS = "AMBER_AES_KEY_B"
    private const val TAG = "SecureCryptoHelper"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE_BYTES = 12
    private const val TAG_SIZE_BITS = 128
    private const val KEY_CHECK_PROBE = "amber-key-check"

    private const val MAX_SWEEP_PASSES = 3

    private const val MAX_CIPHER_ATTEMPTS = 3
    private const val PRUNED_RETRY_DELAY_MS = 10L
    private val mutex = Mutex()

    private class KeyHandle(val alias: String, val key: SecretKey, val previousKeyWhileRotating: SecretKey?)

    /**
     * An AndroidKeyStore [SecretKey] is only a reference (the material stays in the
     * TEE/StrongBox), but fetching it costs a KeyStore load plus binder round-trips to the
     * keystore daemon, milliseconds each. Fetched per cipher operation, that lookup per
     * envelope-encrypted field dominated DAO load times.
     */
    @Suppress("NoComments")
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

    private fun encryptWithKey(key: SecretKey, plainText: String): String = retryPrunedOperation {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv

        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val combined = ByteBuffer.allocate(iv.size + cipherText.size)
        combined.put(iv)
        combined.put(cipherText)

        Base64.encodeToString(combined.array(), Base64.NO_WRAP)
    }

    /**
     * Non-suspending equivalent of [decrypt]. See [encryptBlocking] for the
     * rationale.
     */
    fun decryptBlocking(encryptedText: String): String = withFreshKeyRetry { handle -> decryptWithHandle(handle, encryptedText) }

    private fun decryptWithHandle(handle: KeyHandle, encryptedText: String): String = try {
        decryptWithKey(handle.key, encryptedText)
    } catch (e: AEADBadTagException) {
        decryptWithKey(handle.previousKeyWhileRotating ?: throw e, encryptedText)
    }

    private fun decryptWithKey(key: SecretKey, encryptedText: String): String {
        val data = Base64.decode(encryptedText, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(data)

        val iv = ByteArray(IV_SIZE_BYTES).also { buffer.get(it) }
        val cipherText = ByteArray(buffer.remaining()).also { buffer.get(it) }

        return retryPrunedOperation {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(TAG_SIZE_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }
    }

    /**
     * Runs [block] again when keystore2 finalized the operation underneath
     * it. The daemon caps how many cipher operations can be open at once
     * across every app on the device (so one pruning can be followed by
     * another while it clears) and prunes an open one when it runs out,
     * after which `doFinal` fails with `INVALID_OPERATION_HANDLE` ("Call on
     * finalized operation with outcome: Pruned", public code 15 /
     * `ERROR_KEY_OPERATION_EXPIRED`). The key and the ciphertext are
     * untouched, so a fresh [Cipher], built by every attempt of [block],
     * succeeds. Every other failure, including the last attempt's, propagates
     * unchanged. Retries are not logged: a failure that survives them still
     * surfaces through the caller.
     */
    @Suppress("NoComments")
    internal fun <T> retryPrunedOperation(
        isPruned: (Throwable) -> Boolean = { it.isPrunedKeystoreOperation() },
        block: () -> T,
    ): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                if (attempt >= MAX_CIPHER_ATTEMPTS || !isPruned(e)) throw e
                Thread.sleep(PRUNED_RETRY_DELAY_MS * attempt)
                attempt++
            }
        }
    }

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
        val context = appContext
        val keyStore = getKeyStore()
        val alias = resolveActiveAlias(context, keyStore)
        val key = keyStore.secretKey(alias) ?: generateSecretKey(context, alias, KeystorePolicy.requireUnlockedDevice(context))
        return KeyHandle(alias, key, keyStore.secretKey(otherAlias(alias)))
    }

    private fun resolveActiveAlias(context: Context, keyStore: KeyStore): String {
        val recorded = if (KeystorePolicy.activeAlias(context) == SECOND_KEY_ALIAS) SECOND_KEY_ALIAS else FIRST_KEY_ALIAS
        val other = otherAlias(recorded)
        if (!keyStore.containsAlias(recorded) && keyStore.containsAlias(other)) {
            AmberLog.w(TAG, "Active Keystore alias $recorded is missing, adopting $other")
            if (!KeystorePolicy.commitActiveAlias(context, other)) {
                AmberLog.w(TAG, "Failed to persist adopted Keystore alias $other")
            }
            return other
        }
        return recorded
    }

    private fun otherAlias(alias: String): String = if (alias == SECOND_KEY_ALIAS) FIRST_KEY_ALIAS else SECOND_KEY_ALIAS

    private fun KeyStore.secretKey(alias: String): SecretKey? = (getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun getKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    @Suppress("NoComments")
    private fun generateSecretKey(context: Context, alias: String, requireUnlockedDevice: Boolean): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val paramsBuilder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

        // GHSA-8844-q5vh-9j8f, L1: the opt-in policy makes the Keystore refuse key use
        // while the device is locked, so a process running behind the lock screen cannot
        // decrypt stored account keys. It never prompts per operation (that would break
        // background NIP-46 signing). setUnlockedDeviceRequired exists from API 28.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && requireUnlockedDevice) {
            paramsBuilder.setUnlockedDeviceRequired(true)
            try {
                if (context.hasStrongBox()) {
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
     * `application` tables' envelope-encrypted `secret` / `localKey` columns,
     * GHSA-5fjp-ghh8-wch8 — see `ApplicationEntityCrypto.kt`).
     *
     * The Keystore `setUnlockedDeviceRequired` flag is set at key-generation
     * time, so changing the opt-in policy needs a new key. No step deletes a
     * key that still protects data (GHSA-wv7c-hq2j-ppc5):
     *
     * 1. The new key is generated under the free one of the two aliases, and
     *    proven by a round trip; the old one is untouched. An install that never
     *    rotated has no recorded alias and uses the first.
     * 2. One synchronous preferences commit makes the new alias active and
     *    records the policy. Before it lands nothing has changed; after it,
     *    new writes use the new key and reads fall back to the old one. A
     *    failed commit still updates the in-memory values, so they are
     *    restored before the new key is discarded.
     * 3. [finishRotation] moves each value to the new key with
     *    compare-and-set writes, and deletes the old key only after a full
     *    pass finds nothing left to move and nothing it failed to read.
     *
     * Only a value neither key can read (a GCM tag mismatch or malformed data)
     * lets a sweep move past it; any other failure, such as the device being
     * locked under the policy or a keystore error, keeps the previous key. If the
     * recorded alias has lost its key while the other still holds one, the
     * survivor is adopted rather than a fresh key minted, which would strand
     * everything encrypted under it. A cached key found unusable is re-fetched
     * once; an `AEADBadTagException` is a data error, never a stale key.
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
            val key = generateSecretKey(context, oldAlias, requireUnlockedDevice)
            if (!KeystorePolicy.commitActiveAlias(context, oldAlias, requireUnlockedDevice)) {
                throw IOException("Failed to persist the key policy")
            }
            cachedHandle = KeyHandle(oldAlias, key, null)
            return@withLock
        }

        if (keyStore.containsAlias(newAlias)) {
            check(finishRotation(context, keyStore, oldAlias, oldKey, newAlias)) {
                "A previous key rotation is still incomplete"
            }
        }

        val newKey = try {
            generateSecretKey(context, newAlias, requireUnlockedDevice).also { key ->
                check(decryptWithKey(key, encryptWithKey(key, KEY_CHECK_PROBE)) == KEY_CHECK_PROBE) { "New key failed a round trip" }
            }
        } catch (e: Exception) {
            discardAlias(keyStore, newAlias)
            throw e
        }

        val previousPolicy = KeystorePolicy.requireUnlockedDevice(context)
        if (!KeystorePolicy.commitActiveAlias(context, newAlias, requireUnlockedDevice)) {
            KeystorePolicy.commitActiveAlias(context, oldAlias, previousPolicy)
            discardAlias(keyStore, newAlias)
            throw IOException("Failed to persist the new active key alias")
        }
        cachedHandle = KeyHandle(newAlias, newKey, oldKey)

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

    private suspend fun finishRotation(
        context: Context,
        keyStore: KeyStore,
        activeAlias: String,
        activeKey: SecretKey,
        previousAlias: String,
    ): Boolean {
        val previousKey = keyStore.secretKey(previousAlias)
        if (previousKey != null && !sweepUntilSettled(context, activeKey, previousKey)) return false
        keyStore.deleteEntry(previousAlias)
        cachedHandle = KeyHandle(activeAlias, activeKey, null)
        return true
    }

    private enum class SweepOutcome {
        SETTLED,
        CHANGED,
        FAILED,
    }

    private suspend fun sweepUntilSettled(
        context: Context,
        current: SecretKey,
        previous: SecretKey,
    ): Boolean {
        repeat(MAX_SWEEP_PASSES) {
            when (sweepPass(context, current, previous)) {
                SweepOutcome.SETTLED -> return true
                SweepOutcome.FAILED -> return false
                SweepOutcome.CHANGED -> Unit
            }
        }
        return false
    }

    private suspend fun sweepPass(
        context: Context,
        current: SecretKey,
        previous: SecretKey,
    ): SweepOutcome {
        val result = try {
            sweep(context, current, previous)
        } catch (e: Exception) {
            AmberLog.w(TAG, "Key rotation: sweep failed", e)
            return SweepOutcome.FAILED
        }
        return when {
            result.failed > 0 -> {
                AmberLog.w(TAG, "Key rotation: ${result.failed} value(s) could not be moved to the new key")
                SweepOutcome.FAILED
            }

            result.rewritesAttempted == 0 -> SweepOutcome.SETTLED

            else -> SweepOutcome.CHANGED
        }
    }

    private class SweepResult {
        var rewritesAttempted = 0
        var failed = 0
    }

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
                    result.rewritesAttempted++
                    try {
                        replace(raw, migration.ciphertext)
                    } catch (e: Exception) {
                        result.failed++
                        AmberLog.w(TAG, "Key rotation: failed to write a value", e)
                    }
                }
            }
        }

        val accounts = SavedAccounts.all(context)
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
        move(WebDavSettings.encryptedPassword(context)) { expected, ciphertext ->
            WebDavSettings.replaceEncryptedPassword(context, expected, ciphertext)
        }
        for ((npub) in accounts) {
            val daos = accountRepository.daos(npub)
            for (row in daos.applicationDao.getAllApplicationsRaw()) {
                move(row.secret) { expected, ciphertext -> daos.applicationWriteDao.replaceSecretRaw(row.key, expected, ciphertext) > 0 }
                move(row.localKey) { expected, ciphertext -> daos.applicationWriteDao.replaceLocalKeyRaw(row.key, expected, ciphertext) > 0 }
            }
        }
        return result
    }

    internal sealed interface CiphertextMigration {
        data object Current : CiphertextMigration

        data object Unreadable : CiphertextMigration

        data class Rewrite(val ciphertext: String) : CiphertextMigration

        data class Failed(val error: Exception) : CiphertextMigration
    }

    internal fun migrateCiphertext(
        raw: String,
        decryptCurrent: (String) -> String,
        decryptPrevious: (String) -> String,
        encryptCurrent: (String) -> String,
    ): CiphertextMigration {
        val currentKeyError = try {
            decryptCurrent(raw)
            null
        } catch (e: Exception) {
            e
        }
        return when {
            currentKeyError == null -> CiphertextMigration.Current
            !currentKeyError.isWrongKeyOrMalformed() -> CiphertextMigration.Failed(currentKeyError)
            else -> migrateFromPreviousKey(raw, decryptPrevious, encryptCurrent)
        }
    }

    private fun migrateFromPreviousKey(
        raw: String,
        decryptPrevious: (String) -> String,
        encryptCurrent: (String) -> String,
    ): CiphertextMigration {
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

/**
 * Whether [this] is keystore2 having pruned an in-flight operation, which is
 * transient and worth retrying, as opposed to a real cipher or key failure.
 * Older keystore versions surface it as a [ProviderException] carrying the
 * same cause. Top-level so a unit test can classify a cause chain it builds
 * itself: `android.security.KeyStoreException` has no public constructor and
 * is missing from the stub `android.jar` JVM tests compile against.
 */
@Suppress("NoComments")
internal fun Throwable.isPrunedKeystoreOperation(
    isExpiredKeystoreError: (Throwable) -> Boolean = { it.isExpiredKeystoreError() },
): Boolean {
    if (this !is IllegalBlockSizeException && this !is ProviderException) return false
    var current: Throwable? = this
    while (current != null) {
        if (isExpiredKeystoreError(current)) return true
        val next = current.cause
        current = if (next === current) null else next
    }
    return false
}

@Suppress("NoComments")
private fun Throwable.isExpiredKeystoreError(): Boolean {
    // android.security.KeyStoreException arrived with the keystore2 API
    // (API 33), which is also where pruning open operations began.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    if (this !is android.security.KeyStoreException) return false
    return isTransientFailure ||
        getNumericErrorCode() == android.security.KeyStoreException.ERROR_KEY_OPERATION_EXPIRED
}

fun Context.hasStrongBox(): Boolean {
    val isMediaTekWithBrokenStrongBox = Build.HARDWARE.lowercase().contains("mt") ||
        Build.BOARD.lowercase().contains("mt") ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.SOC_MANUFACTURER.lowercase().contains("mediatek"))

    return !isMediaTekWithBrokenStrongBox &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
}
