package com.greenart7c3.nostrsigner.desktop.core

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * Optional passphrase lock: when enabled, the master AES key exists on disk
 * only wrapped (AES-256-GCM) under a key derived from the user's passphrase
 * with Argon2id. The passphrase itself is never stored anywhere — neither
 * the data directory nor the OS credential store is enough to decrypt the
 * account keys, which is the strongest protection a portable desktop app
 * can offer against same-user malware reading files at rest.
 *
 * While unlocked, the unwrapped master key (and the decrypted account keys)
 * live in this process's memory; locking evicts them and disconnects the
 * relay client.
 */
object PassphraseLock {
    enum class Status {
        /** No passphrase configured; the keystore + credential store path is used. */
        DISABLED,

        /** Passphrase configured, master key not in memory. Nothing can be signed. */
        LOCKED,

        /** Passphrase configured and entered; master key available until lock(). */
        UNLOCKED,
    }

    val state = MutableStateFlow(if (isEnabled()) Status.LOCKED else Status.DISABLED)

    /** Argon2id cost parameters, persisted per blob so they can evolve safely. */
    data class KdfParams(
        val memoryKb: Int = 65536, // 64 MB
        val iterations: Int = 3,
        val parallelism: Int = 2,
    )

    private data class WrappedKeyBlob(
        val version: Int = 1,
        val salt: String,
        val memoryKb: Int,
        val iterations: Int,
        val parallelism: Int,
        val iv: String,
        val cipherText: String,
    )

    private val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    private val mutex = Mutex()
    private var autoLockJob: Job? = null

    private val blobFile: File get() = File(AppDirs.dataDir, "master.key.enc")

    fun isEnabled(): Boolean = blobFile.exists()

    fun isLocked(): Boolean = state.value == Status.LOCKED

    /**
     * Turns the lock on: wraps the current master key (creating one if this
     * is a fresh install) under the passphrase and deletes every unprotected
     * copy. The app stays unlocked afterwards.
     */
    suspend fun enable(passphrase: CharArray, params: KdfParams = KdfParams()) = mutex.withLock {
        check(!isEnabled()) { "A passphrase is already set" }
        val key = DesktopKeyStore.masterKeyForWrapping()
        writeBlob(key, passphrase, params)
        DesktopKeyStore.removeUnprotectedCopies()
        DesktopKeyStore.installMasterKey(key, SOURCE_DESCRIPTION)
        // isEnabled() is now true, so re-encrypt every account's database at rest.
        AmberDesktop.rewriteAllStores()
        state.value = Status.UNLOCKED
        scheduleAutoLock()
    }

    /** Verifies the passphrase and installs the master key. */
    suspend fun unlock(passphrase: CharArray): Boolean = mutex.withLock {
        if (!isEnabled()) return true
        val key = unwrap(passphrase) ?: return false
        DesktopKeyStore.installMasterKey(key, SOURCE_DESCRIPTION)
        state.value = Status.UNLOCKED
        scheduleAutoLock()
        return true
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
        AmberDesktop.engine.pending.value = emptyList()
        AmberDesktop.client.disconnect()
        state.value = Status.LOCKED
    }

    /** Removes the lock, restoring the keystore + credential-store storage. */
    suspend fun disable() = mutex.withLock {
        check(state.value == Status.UNLOCKED) { "Unlock first" }
        val key = DesktopKeyStore.masterKeyForWrapping()
        DesktopKeyStore.recreateUnprotectedStore(key)
        blobFile.delete()
        // isEnabled() is now false, so rewrite every account's database as plaintext.
        AmberDesktop.rewriteAllStores()
        autoLockJob?.cancel()
        state.value = Status.DISABLED
    }

    /** Rewraps the master key under a new passphrase; false when [old] is wrong. */
    suspend fun changePassphrase(old: CharArray, new: CharArray, params: KdfParams = KdfParams()): Boolean = mutex.withLock {
        val key = unwrap(old) ?: return false
        writeBlob(key, new, params)
        DesktopKeyStore.installMasterKey(key, SOURCE_DESCRIPTION)
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
        val passphraseBytes = String(passphrase).toByteArray(Charsets.UTF_8)
        try {
            generator.generateBytes(passphraseBytes, out)
        } finally {
            passphraseBytes.fill(0)
        }
        return out
    }

    private fun writeBlob(key: SecretKey, passphrase: CharArray, params: KdfParams) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val derived = deriveKey(passphrase, salt, params)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derived, "AES"))
        val cipherText = cipher.doFinal(key.encoded)
        derived.fill(0)

        val encoder = Base64.getEncoder().withoutPadding()
        val blob = WrappedKeyBlob(
            salt = encoder.encodeToString(salt),
            memoryKb = params.memoryKb,
            iterations = params.iterations,
            parallelism = params.parallelism,
            iv = encoder.encodeToString(cipher.iv),
            cipherText = encoder.encodeToString(cipherText),
        )
        val tmp = File(blobFile.parentFile, "${blobFile.name}.tmp")
        tmp.writeText(mapper.writeValueAsString(blob))
        if (!tmp.renameTo(blobFile)) {
            blobFile.writeText(tmp.readText())
            tmp.delete()
        }
        AppDirs.restrictToOwner(blobFile)
    }

    private fun unwrap(passphrase: CharArray): SecretKey? {
        val blob = try {
            mapper.readValue<WrappedKeyBlob>(blobFile.readText())
        } catch (e: Exception) {
            AmberLogger.e("PassphraseLock", "Corrupt wrapped-key blob", e)
            return null
        }
        val decoder = Base64.getDecoder()
        val derived = deriveKey(
            passphrase,
            decoder.decode(blob.salt),
            KdfParams(blob.memoryKb, blob.iterations, blob.parallelism),
        )
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(128, decoder.decode(blob.iv)))
            SecretKeySpec(cipher.doFinal(decoder.decode(blob.cipherText)), "AES")
        } catch (_: Exception) {
            null // wrong passphrase (GCM tag mismatch)
        } finally {
            derived.fill(0)
        }
    }

    private const val SOURCE_DESCRIPTION = "your passphrase (Argon2id, never stored)"
}
