package com.greenart7c3.nostrsigner.desktop.core

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.HKDFParameters

/**
 * Mandatory passphrase lock: the master AES key exists on disk only wrapped
 * (AES-256-GCM) under a key-encryption key derived from the user's
 * passphrase with Argon2id. The passphrase itself is never stored anywhere.
 *
 * When the OS credential store works (macOS Keychain, Windows Credential
 * Manager, Secret Service), the key-encryption key also mixes in a random
 * 256-bit secret kept there, so unwrapping needs both the passphrase and
 * that entry: a copy of the data directory alone (a backup, a synced
 * folder, a stolen disk) cannot be brute-forced offline, however weak the
 * passphrase. Without a usable credential store the passphrase is the only
 * factor; installs gain the second factor at the next unlock once the store
 * becomes available.
 *
 * Fresh installs set the passphrase before the app is usable ([Status.DISABLED]
 * is only ever that transient pre-setup state); there is no way back to
 * unprotected storage.
 *
 * While unlocked, the unwrapped master key (and the decrypted account keys)
 * live in this process's memory; locking evicts them and disconnects the
 * relay client.
 */
object PassphraseLock {
    enum class Status {
        /** Passphrase not configured yet (first run); setup is required. */
        DISABLED,

        /** Passphrase configured, master key not in memory. Nothing can be signed. */
        LOCKED,

        /** Passphrase configured and entered; master key available until lock(). */
        UNLOCKED,
    }

    /**
     * The wrapped key needs the OS credential store's secret, but it could not
     * be read (keyring locked or unavailable, entry deleted). Distinct from a
     * wrong passphrase so the user is told what to fix.
     */
    class OsSecretUnavailableException(val storeDescription: String) : IllegalStateException("Amber's unlock secret could not be read from $storeDescription")

    val state = MutableStateFlow(if (isEnabled()) Status.LOCKED else Status.DISABLED)

    /** Shortest passphrase accepted when setting or changing it. */
    const val MIN_PASSPHRASE_LENGTH = 12

    /**
     * Argon2id cost parameters, persisted per blob so they can evolve safely:
     * a blob written with weaker parameters than [targetKdfParams] is rewrapped
     * at the next unlock.
     */
    data class KdfParams(
        val memoryKb: Int = 262144, // 256 MB, about 1 s on a current desktop CPU
        val iterations: Int = 3,
        val parallelism: Int = 4,
    ) {
        fun isWeakerThan(other: KdfParams): Boolean = memoryKb < other.memoryKb || iterations < other.iterations

        companion object {
            /**
             * The defaults, with the memory cost scaled down when the JVM heap
             * could not comfortably hold it; never below the original 64 MB.
             */
            fun recommended(maxHeapBytes: Long = Runtime.getRuntime().maxMemory()): KdfParams {
                val heapMb = maxHeapBytes / (1024 * 1024)
                val memoryKb = when {
                    heapMb >= 1024 -> 262144
                    heapMb >= 512 -> 131072
                    else -> 65536
                }
                return KdfParams(memoryKb = memoryKb)
            }
        }
    }

    /** Cost new and rehashed blobs get; tests lower it to keep Argon2 fast. */
    internal var targetKdfParams: KdfParams = KdfParams.recommended()

    /**
     * On-disk wrapped master key.
     * - v1: key-encryption key = Argon2id(passphrase); header not authenticated.
     * - v2: the header (version, KDF parameters, [osSecret]) is GCM associated
     *   data, so it cannot be downgraded, and every ciphertext the master key
     *   protects must be context-bound (see [requiresBoundCiphertexts]).
     */
    private data class WrappedKeyBlob(
        val version: Int = 1,
        val salt: String,
        val memoryKb: Int,
        val iterations: Int,
        val parallelism: Int,
        val iv: String,
        val cipherText: String,
        /** The key-encryption key also mixes in the secret from [osSecretStore]. */
        val osSecret: Boolean = false,
    ) {
        val params: KdfParams get() = KdfParams(memoryKb, iterations, parallelism)
    }

    private const val CURRENT_VERSION = 2
    private const val OS_SECRET_SIZE = 32

    private val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    private val mutex = Mutex()
    private var autoLockJob: Job? = null

    private val blobFile: File get() = File(AppDirs.dataDir, "master.key.enc")

    /** Holds the second unlock factor; tests swap in an in-memory store. */
    internal var osSecretStore: PasswordStore = OsCredentialStore(account = OS_SECRET_ACCOUNT)

    @Volatile
    private var cachedBlobVersion: Int? = null

    fun isEnabled(): Boolean = blobFile.exists()

    fun isLocked(): Boolean = state.value == Status.LOCKED

    /**
     * True once the install has been migrated to the v2 format: from then on
     * unbound ciphertexts and plaintext account databases are rejected, so
     * an attacker with write access to the data directory cannot plant them.
     */
    fun requiresBoundCiphertexts(): Boolean {
        cachedBlobVersion?.let { return it >= CURRENT_VERSION }
        val version = readBlob()?.version ?: return false
        cachedBlobVersion = version
        return version >= CURRENT_VERSION
    }

    /**
     * Turns the lock on: wraps the current master key (creating one if this
     * is a fresh install) under the passphrase and deletes every unprotected
     * copy. The app stays unlocked afterwards.
     */
    suspend fun enable(passphrase: CharArray, params: KdfParams = targetKdfParams) = mutex.withLock {
        check(!isEnabled()) { "A passphrase is already set" }
        val key = DesktopKeyStore.masterKeyForWrapping()
        // Load every account database and bind the account keys while the
        // old formats are still accepted: the v2 blob makes them strict.
        AmberDesktop.loadAllStores()
        AccountManager.bindKeyCiphertexts()
        val osSecret = obtainOsSecret()
        writeBlob(key, passphrase, params, osSecret)
        DesktopKeyStore.removeUnprotectedCopies()
        DesktopKeyStore.installMasterKey(key, sourceDescription(osSecret != null))
        // isEnabled() is now true, so re-encrypt every account's database at rest.
        AmberDesktop.rewriteAllStores()
        state.value = Status.UNLOCKED
        scheduleAutoLock()
    }

    /**
     * Verifies the passphrase and installs the master key. Throws
     * [OsSecretUnavailableException] when the credential store's secret is
     * needed but cannot be read.
     */
    suspend fun unlock(passphrase: CharArray): Boolean = mutex.withLock {
        if (!isEnabled()) return true
        val blob = readBlob() ?: return false
        val key = unwrap(blob, passphrase) ?: return false
        DesktopKeyStore.installMasterKey(key, sourceDescription(blob.osSecret))
        upgrade(blob, key, passphrase)
        state.value = Status.UNLOCKED
        scheduleAutoLock()
        return true
    }

    /** Checks the passphrase without installing the master key. */
    suspend fun verify(passphrase: CharArray): Boolean = mutex.withLock {
        val blob = readBlob() ?: return false
        val key = unwrap(blob, passphrase) ?: return false
        key.destroy()
        true
    }

    /**
     * Evicts all key material from memory and disconnects from the relays.
     * Incoming NIP-46 requests cannot be decrypted (let alone signed) until
     * the next unlock.
     */
    fun lock() {
        if (!isEnabled()) return
        autoLockJob?.cancel()
        DesktopKeyStore.clearMasterKey()
        AmberDesktop.evictAllAccounts()
        AmberDesktop.engine.dropPending("signer locked") { true }
        AmberDesktop.disconnectIntentionally()
        state.value = Status.LOCKED
    }

    /** Rewraps the master key under a new passphrase; false when [old] is wrong. */
    suspend fun changePassphrase(old: CharArray, new: CharArray, params: KdfParams = targetKdfParams): Boolean = mutex.withLock {
        val blob = readBlob() ?: return false
        val key = unwrap(blob, old) ?: return false
        // Writing a v2 blob makes the data strict, so finish an interrupted
        // migration first.
        if (blob.version < CURRENT_VERSION) migrateData()
        val osSecret = obtainOsSecret()
        writeBlob(key, new, params, osSecret)
        DesktopKeyStore.installMasterKey(key, sourceDescription(osSecret != null))
        state.value = Status.UNLOCKED
        scheduleAutoLock()
        return true
    }

    /** Restarts the auto-lock countdown; call on user/signing activity. */
    fun touch() {
        if (state.value == Status.UNLOCKED) scheduleAutoLock()
    }

    private fun scheduleAutoLock() {
        autoLockJob?.cancel()
        val minutes = SettingsStore.settings.value.autoLockMinutes
        if (minutes <= 0) return
        autoLockJob = AmberDesktop.applicationIOScope.launch {
            delay(minutes * 60_000L)
            AmberLogger.d("PassphraseLock", "Auto-locking after $minutes minutes")
            lock()
        }
    }

    // ----- wrapping crypto -----

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, params: KdfParams): ByteArray {
        val generator = Argon2BytesGenerator()
        generator.init(
            Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withMemoryAsKB(params.memoryKb)
                .withIterations(params.iterations)
                .withParallelism(params.parallelism)
                .build(),
        )
        val out = ByteArray(32)
        // Encode straight into a heap byte buffer: an intermediate String
        // would be an immutable copy whose characters could never be wiped.
        val encoder = Charsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        val encoded = ByteBuffer.allocate((passphrase.size * encoder.maxBytesPerChar()).toInt().coerceAtLeast(1))
        val encodeResult = encoder.encode(CharBuffer.wrap(passphrase), encoded, true)
        if (encodeResult.isError) encodeResult.throwException()
        if (encoder.flush(encoded).isOverflow) error("Passphrase encoding overflowed its buffer")
        encoded.flip()
        // BouncyCastle needs the password as a standalone exact-size array;
        // extract it from the buffer's backing array. Both byte arrays are
        // wiped below, so no unwipeable copy of the passphrase remains.
        val passphraseBytes = ByteArray(encoded.remaining()).also { encoded.get(it) }
        try {
            generator.generateBytes(passphraseBytes, out)
        } finally {
            passphraseBytes.fill(0)
            encoded.array().fill(0)
        }
        return out
    }

    /**
     * After an unlock: migrates a v1 install to the bound v2 format, adds the
     * credential-store secret once the store works, and rehashes a blob whose
     * Argon2id cost is below [targetKdfParams]. Never blocks the unlock — on
     * failure the old blob stays valid and the next unlock retries.
     */
    private fun upgrade(blob: WrappedKeyBlob, key: SecretKey, passphrase: CharArray) {
        try {
            val weakKdf = blob.params.isWeakerThan(targetKdfParams)
            if (blob.version >= CURRENT_VERSION && blob.osSecret && !weakKdf) return
            val osSecret = obtainOsSecret()
            if (blob.version >= CURRENT_VERSION && osSecret == null && !weakKdf) return
            // The data is rewritten before the blob: a crash in between leaves
            // a v1 blob, which still accepts the already-bound data.
            if (blob.version < CURRENT_VERSION) migrateData()
            writeBlob(key, passphrase, if (weakKdf) targetKdfParams else blob.params, osSecret)
            DesktopKeyStore.installMasterKey(key, sourceDescription(osSecret != null))
            AmberLogger.i("PassphraseLock", "Upgraded the wrapped master key (second factor: ${osSecret != null}, rehashed: $weakKdf)")
        } catch (e: Exception) {
            AmberLogger.e("PassphraseLock", "Failed to upgrade the wrapped master key", e)
        }
    }

    /** Rewrites the account keys and databases in the context-bound format. */
    private fun migrateData() {
        AmberDesktop.loadAllStores()
        AccountManager.bindKeyCiphertexts()
        AmberDesktop.rewriteAllStores()
    }

    /**
     * Returns the credential-store secret, creating it on first use, or null
     * when the store is unavailable. A new secret is only trusted once it
     * reads back, so a store that accepts writes it cannot return never
     * locks the user out.
     */
    private fun obtainOsSecret(): ByteArray? {
        loadOsSecret()?.let { return it }
        val fresh = ByteArray(OS_SECRET_SIZE).also { SecureRandom().nextBytes(it) }
        val encoded = Base64.getEncoder().withoutPadding().encodeToString(fresh)
        if (!osSecretStore.store(encoded)) return null
        val readBack = loadOsSecret()
        return if (readBack != null && readBack.contentEquals(fresh)) readBack else null
    }

    private fun loadOsSecret(): ByteArray? = osSecretStore.load()
        ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
        ?.takeIf { it.size == OS_SECRET_SIZE }

    private fun sourceDescription(osSecret: Boolean): String = if (osSecret) {
        "$SOURCE_DESCRIPTION + a secret in ${osSecretStore.description}"
    } else {
        SOURCE_DESCRIPTION
    }

    private fun readBlob(): WrappedKeyBlob? = if (!blobFile.exists()) {
        null
    } else {
        try {
            mapper.readValue<WrappedKeyBlob>(blobFile.readText())
        } catch (e: Exception) {
            AmberLogger.e("PassphraseLock", "Corrupt wrapped-key blob", e)
            null
        }
    }

    /** Authenticates the blob header, so neither the version nor the factors can be downgraded. */
    private fun headerAad(version: Int, params: KdfParams, osSecret: Boolean): ByteArray = "amber-master-key:v$version:${params.memoryKb}:${params.iterations}:${params.parallelism}:$osSecret".toByteArray(Charsets.UTF_8)

    /** Argon2id(passphrase), combined with the credential-store secret via HKDF-SHA256 when present. */
    private fun deriveKek(passphrase: CharArray, salt: ByteArray, params: KdfParams, osSecret: ByteArray?): ByteArray {
        val stretched = deriveKey(passphrase, salt, params)
        if (osSecret == null) return stretched
        return try {
            val hkdf = HKDFBytesGenerator(SHA256Digest())
            hkdf.init(HKDFParameters(stretched, osSecret, "amber-desktop-kek-v2".toByteArray(Charsets.UTF_8)))
            ByteArray(32).also { hkdf.generateBytes(it, 0, it.size) }
        } finally {
            stretched.fill(0)
        }
    }

    private fun writeBlob(key: SecretKey, passphrase: CharArray, params: KdfParams, osSecret: ByteArray?) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val kek = wipeableKek(deriveKek(passphrase, salt, params, osSecret))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, kek)
        cipher.updateAAD(headerAad(CURRENT_VERSION, params, osSecret != null))
        val raw = key.encoded
        val cipherText = cipher.doFinal(raw)
        raw.fill(0)
        kek.destroy()

        val encoder = Base64.getEncoder().withoutPadding()
        val blob = WrappedKeyBlob(
            version = CURRENT_VERSION,
            salt = encoder.encodeToString(salt),
            memoryKb = params.memoryKb,
            iterations = params.iterations,
            parallelism = params.parallelism,
            iv = encoder.encodeToString(cipher.iv),
            cipherText = encoder.encodeToString(cipherText),
            osSecret = osSecret != null,
        )
        AppDirs.writeAtomically(blobFile, mapper.writeValueAsBytes(blob))
        cachedBlobVersion = CURRENT_VERSION
    }

    private fun unwrap(blob: WrappedKeyBlob, passphrase: CharArray): WipeableAesKey? {
        // Checked before the (slow) Argon2 run, and reported separately from a
        // wrong passphrase.
        val osSecret = if (blob.osSecret) {
            loadOsSecret() ?: throw OsSecretUnavailableException(osSecretStore.description)
        } else {
            null
        }
        val decoder = Base64.getDecoder()
        val kek = wipeableKek(deriveKek(passphrase, decoder.decode(blob.salt), blob.params, osSecret))
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(128, decoder.decode(blob.iv)))
            if (blob.version >= CURRENT_VERSION) cipher.updateAAD(headerAad(blob.version, blob.params, blob.osSecret))
            val plain = cipher.doFinal(decoder.decode(blob.cipherText))
            val key = WipeableAesKey(plain) // copies the input
            plain.fill(0)
            key
        } catch (_: Exception) {
            null // wrong passphrase or tampered header (GCM tag mismatch)
        } finally {
            kek.destroy()
        }
    }

    /** Moves derived key bytes into a [WipeableAesKey] (SecretKeySpec would keep an unwipeable copy). */
    private fun wipeableKek(derived: ByteArray): WipeableAesKey = try {
        WipeableAesKey(derived)
    } finally {
        derived.fill(0)
    }

    // ----- test support -----

    /** Writes a pre-v2 blob (no second factor, unauthenticated header), to exercise the migration. */
    internal fun writeLegacyBlobForTest(key: SecretKey, passphrase: CharArray, params: KdfParams) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val kek = wipeableKek(deriveKey(passphrase, salt, params))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, kek)
        val cipherText = cipher.doFinal(key.encoded)
        kek.destroy()
        val encoder = Base64.getEncoder().withoutPadding()
        val blob = WrappedKeyBlob(
            version = 1,
            salt = encoder.encodeToString(salt),
            memoryKb = params.memoryKb,
            iterations = params.iterations,
            parallelism = params.parallelism,
            iv = encoder.encodeToString(cipher.iv),
            cipherText = encoder.encodeToString(cipherText),
        )
        AppDirs.writeAtomically(blobFile, mapper.writeValueAsBytes(blob))
        cachedBlobVersion = 1
    }

    private const val SOURCE_DESCRIPTION = "your passphrase (Argon2id, never stored)"
    const val OS_SECRET_ACCOUNT = "master-key-secret"
}
