package com.greenart7c3.nostrsigner.desktop.core

import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Desktop counterpart of the Android `SecureCryptoHelper`: private keys are
 * encrypted at rest with an AES-256 master key.
 *
 * Where that master key lives depends on the security mode:
 * - Default: in a Java KeyStore (PKCS12) file whose password is kept in the
 *   OS credential store (macOS Keychain, Windows Credential Manager,
 *   freedesktop Secret Service) when available, falling back to an
 *   owner-only password file (see [KeystorePassword]).
 * - Passphrase lock enabled ([PassphraseLock]): the master key exists on
 *   disk only wrapped under a key derived from the user's passphrase; it is
 *   installed here at unlock time and evicted on lock.
 */
object DesktopKeyStore {
    private const val KEY_ALIAS = "AMBER_AES_KEY"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12 // 96 bits
    private const val TAG_SIZE = 128 // bits
    private val mutex = Mutex()

    private val keyStoreFile: File get() = File(AppDirs.dataDir, "amber.keystore")
    private val passwordFile: File get() = File(AppDirs.dataDir, "keystore.pass")

    private var cachedKey: SecretKey? = null
    private var sourceDescription: String? = null

    /** Where the master key/its password is kept, for display in Settings. */
    val passwordSourceDescription: String?
        get() = sourceDescription

    class LockedException : IllegalStateException("The key store is locked. Unlock it with your passphrase first.")

    suspend fun encrypt(plainText: String): String = mutex.withLock { encryptString(plainText) }

    suspend fun decrypt(encryptedText: String): String = mutex.withLock { decryptString(encryptedText) }

    /**
     * Synchronous AES-256-GCM encryption with the master key, for the storage
     * layer (which reads/writes files on its own threads). Requires the master
     * key to be available — throws [LockedException] when the passphrase lock
     * is engaged and the app has not been unlocked.
     */
    fun encryptString(plainText: String): String {
        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteBuffer.allocate(iv.size + cipherText.size)
        combined.put(iv)
        combined.put(cipherText)
        return Base64.getEncoder().withoutPadding().encodeToString(combined.array())
    }

    fun decryptString(encryptedText: String): String {
        val key = getOrCreateSecretKey()
        val data = Base64.getDecoder().decode(encryptedText)
        val buffer = ByteBuffer.wrap(data)
        val iv = ByteArray(IV_SIZE).also { buffer.get(it) }
        val cipherText = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    // ----- master key management (used by PassphraseLock) -----

    /** Installs an unwrapped master key (unlock, or right after enabling the lock). */
    internal fun installMasterKey(key: SecretKey, source: String) {
        cachedKey = key
        sourceDescription = source
    }

    /** Evicts the in-memory master key (lock). */
    internal fun clearMasterKey() {
        cachedKey = null
        sourceDescription = null
    }

    /**
     * Returns the master key for wrapping under a passphrase, creating it
     * through the regular keystore path when it does not exist yet. Must only
     * be called while the passphrase lock is disabled or unlocked.
     */
    internal suspend fun masterKeyForWrapping(): SecretKey = mutex.withLock {
        cachedKey ?: loadOrCreateFromKeystore()
    }

    /**
     * Deletes the unprotected key copies (PKCS12 keystore + its password in
     * the OS credential store / password file) after the passphrase lock has
     * taken ownership of the master key.
     */
    internal fun removeUnprotectedCopies() {
        keyStoreFile.delete()
        FilePasswordStore(passwordFile).delete()
        OsCredentialStore().delete()
    }

    /**
     * Re-creates the unprotected storage (keystore + credential-store/file
     * password) from [key] when the passphrase lock is removed.
     */
    internal suspend fun recreateUnprotectedStore(key: SecretKey): Unit = mutex.withLock {
        val resolved = KeystorePassword.resolve(
            osStore = OsCredentialStore(),
            fileStore = FilePasswordStore(passwordFile),
            keystoreExists = false,
            opens = { false },
        )
        writeKeystore(key, resolved.password.toCharArray())
        cachedKey = key
        sourceDescription = resolved.source.description
    }

    // ----- keystore-backed path (passphrase lock disabled) -----

    private fun loadKeyStore(password: CharArray): KeyStore {
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStoreFile.inputStream().use { keyStore.load(it, password) }
        return keyStore
    }

    private fun canOpen(password: String): Boolean = try {
        loadKeyStore(password.toCharArray())
        true
    } catch (_: Exception) {
        false
    }

    private fun writeKeystore(key: SecretKey, password: CharArray) {
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, password)
        keyStore.setEntry(KEY_ALIAS, KeyStore.SecretKeyEntry(key), KeyStore.PasswordProtection(password))
        keyStoreFile.outputStream().use { keyStore.store(it, password) }
        AppDirs.restrictToOwner(keyStoreFile)
    }

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        cachedKey?.let { return it }
        if (PassphraseLock.isEnabled()) {
            throw LockedException()
        }
        return loadOrCreateFromKeystore()
    }

    /** True when the master key is in memory (or loadable without a passphrase). */
    fun isMasterKeyAvailable(): Boolean = cachedKey != null || !PassphraseLock.isEnabled()

    private fun loadOrCreateFromKeystore(): SecretKey {
        cachedKey?.let { return it }

        val resolved = KeystorePassword.resolve(
            osStore = OsCredentialStore(),
            fileStore = FilePasswordStore(passwordFile),
            keystoreExists = keyStoreFile.exists(),
            opens = ::canOpen,
        )
        sourceDescription = resolved.source.description
        AmberLogger.d("DesktopKeyStore", "Keystore password source: ${resolved.source.description}")
        val password = resolved.password.toCharArray()

        if (keyStoreFile.exists()) {
            val keyStore = loadKeyStore(password)
            val entry = keyStore.getEntry(KEY_ALIAS, KeyStore.PasswordProtection(password)) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                cachedKey = entry.secretKey
                return entry.secretKey
            }
            // Keystore exists but the entry is missing (should not happen);
            // fall through and add a fresh key to the same store.
            val key = generateKey()
            keyStore.setEntry(KEY_ALIAS, KeyStore.SecretKeyEntry(key), KeyStore.PasswordProtection(password))
            keyStoreFile.outputStream().use { keyStore.store(it, password) }
            AppDirs.restrictToOwner(keyStoreFile)
            cachedKey = key
            return key
        }

        val key = generateKey()
        writeKeystore(key, password)
        cachedKey = key
        return key
    }

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        return keyGenerator.generateKey()
    }
}
