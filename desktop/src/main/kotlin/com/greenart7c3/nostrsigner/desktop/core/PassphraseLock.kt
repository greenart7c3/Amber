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
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
     * On-disk wrapped master key (the only format the desktop app writes).
     * The header (version, KDF parameters, [osSecret]) is GCM associated
     * data, so it cannot be downgraded, and every ciphertext the master key
     * protects must be context-bound.
     */
    private data class WrappedKeyBlob(
        val version: Int = CURRENT_VERSION,
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

    fun isEnabled(): Boolean = blobFile.exists()

    fun isLocked(): Boolean = state.value == Status.LOCKED

    /**
     * Turns the lock on: wraps the current master key (creating one if this
     * is a fresh install) under the passphrase and deletes every unprotected
     * copy. The app stays unlocked afterward.
     */
    suspend fun enable(passphrase: CharArray, params: KdfParams = targetKdfParams) = mutex.withLock {
        check(!isEnabled()) { "A passphrase is already set" }
        val key = DesktopKeyStore.masterKeyForWrapping()
        // Load every account database before the blob exists: from then on
        // writes are strict, and the stores are re-encrypted from memory.
        AmberDesktop.loadAllStores()
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
        val (key, osSecret) = unwrap(blob, passphrase) ?: return false
        DesktopKeyStore.installMasterKey(key, sourceDescription(blob.osSecret))
        try {
            upgrade(blob, key, passphrase, osSecret)
        } finally {
            osSecret?.fill(0)
        }
        state.value = Status.UNLOCKED
        scheduleAutoLock()
        return true
    }

    /** Checks the passphrase without installing the master key. */
    suspend fun verify(passphrase: CharArray): Boolean = mutex.withLock {
        val blob = readBlob() ?: return false
        val (key, osSecret) = unwrap(blob, passphrase) ?: return false
        key.destroy()
        osSecret?.fill(0)
        true
    }

    /**
     * Evicts all key material from memory and disconnects from the relays.
     * Incoming NIP-46 requests cannot be decrypted (let alone signed) until
     * the next unlock.
     */
    // Blocks on purpose: the lock must have happened when this returns.
    @Suppress("RunBlocking")
    fun lock() {
        if (!isEnabled()) return
        // Waits for an unlock or passphrase change in flight (at most its
        // Argon2 run) instead of interleaving with it.
        runBlocking { lockUnderMutex() }
    }

    /**
     * Holds the same mutex as [unlock] and [changePassphrase], so a lock can
     * neither be undone by an unlock that was mid-way (state set back to
     * UNLOCKED, key reinstalled) nor wipe the key from under one.
     */
    private suspend fun lockUnderMutex() = mutex.withLock {
        if (!isEnabled()) return@withLock
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
        val (key, unwrapSecret) = unwrap(blob, old) ?: return false
        // Rewrap with the very secret that just unwrapped the blob: reading the
        // store again could fail now and silently drop the second factor, or
        // replace the secret the current blob still needs.
        val osSecret = unwrapSecret ?: obtainOsSecret()
        try {
            writeBlob(key, new, params, osSecret)
        } finally {
            osSecret?.fill(0)
        }
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
            delay(minutes.minutes)
            AmberLogger.d("PassphraseLock", "Auto-locking after $minutes minutes")
            // Suspends on the mutex, so an unlock that reschedules (and so
            // cancels) this timer meanwhile also cancels the stale lock.
            lockUnderMutex()
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
     * After an unlock: adds the credential-store secret once the store works,
     * and rehashes a blob whose Argon2id cost is below [targetKdfParams].
     * Never blocks the unlock — on failure the old blob stays valid and the
     * next unlock retries.
     */
    private fun upgrade(blob: WrappedKeyBlob, key: SecretKey, passphrase: CharArray, unwrapSecret: ByteArray?) {
        try {
            val weakKdf = blob.params.isWeakerThan(targetKdfParams)
            if (blob.osSecret && !weakKdf) return
            // A blob that has the second factor keeps the exact secret that
            // unwrapped it; only a blob without one asks the store for a new one.
            val osSecret = unwrapSecret?.copyOf() ?: obtainOsSecret()
            if (osSecret == null && !weakKdf) return
            try {
                writeBlob(key, passphrase, if (weakKdf) targetKdfParams else blob.params, osSecret)
            } finally {
                osSecret?.fill(0)
            }
            DesktopKeyStore.installMasterKey(key, sourceDescription(osSecret != null))
            AmberLogger.i("PassphraseLock", "Rehashed the wrapped master key (second factor: ${osSecret != null})")
        } catch (e: Exception) {
            AmberLogger.e("PassphraseLock", "Failed to rehash the wrapped master key", e)
        }
    }

    /**
     * Returns the credential-store secret, creating it on first use, or null
     * when the store is unavailable. Only for adding the second factor to a
     * blob that has none: it may replace the stored secret, which a blob
     * that already uses one still needs. A new secret is only trusted once it
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
            mapper.readValue<WrappedKeyBlob>(blobFile.readText()).takeIf { blob ->
                // The desktop app has no released predecessor: only the current
                // blob format is honored, and anything else is treated as a
                // tampered file rather than migrated.
                blob.version == CURRENT_VERSION
            } ?: run {
                AmberLogger.e("PassphraseLock", "Unsupported wrapped-key blob version; refusing it")
                null
            }
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
    }

    /**
     * The master key, plus the credential-store secret that unwrapped it (null
     * for a blob without the second factor), so a rewrap reuses that exact
     * secret; the caller wipes it.
     */
    private fun unwrap(blob: WrappedKeyBlob, passphrase: CharArray): Pair<WipeableAesKey, ByteArray?>? {
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
            cipher.updateAAD(headerAad(blob.version, blob.params, blob.osSecret))
            val plain = cipher.doFinal(decoder.decode(blob.cipherText))
            val key = WipeableAesKey(plain) // copies the input
            plain.fill(0)
            key to osSecret
        } catch (_: Exception) {
            osSecret?.fill(0)
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

    private const val SOURCE_DESCRIPTION = "your passphrase (Argon2id, never stored)"
    const val OS_SECRET_ACCOUNT = "master-key-secret"
}
