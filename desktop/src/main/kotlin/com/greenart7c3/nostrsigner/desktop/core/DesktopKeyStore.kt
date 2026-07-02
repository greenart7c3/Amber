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
 * encrypted at rest with an AES-256 key held in a Java KeyStore (PKCS12)
 * file. The keystore password lives in the OS credential store (macOS
 * Keychain, Windows Credential Manager, or the freedesktop Secret Service)
 * when one is available, so the data directory alone is not enough to
 * unlock the keys; systems without a secret daemon fall back to an
 * owner-only password file next to the keystore (see [KeystorePassword]).
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
    private var cachedSource: PasswordStore? = null

    /** Where the keystore password is kept, for display in Settings. */
    val passwordSourceDescription: String?
        get() = cachedSource?.description

    suspend fun encrypt(plainText: String): String = mutex.withLock {
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

    suspend fun decrypt(encryptedText: String): String = mutex.withLock {
        val key = getOrCreateSecretKey()
        val data = Base64.getDecoder().decode(encryptedText)
        val buffer = ByteBuffer.wrap(data)

        val iv = ByteArray(IV_SIZE).also { buffer.get(it) }
        val cipherText = ByteArray(buffer.remaining()).also { buffer.get(it) }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_SIZE, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val plainBytes = cipher.doFinal(cipherText)
        return String(plainBytes, Charsets.UTF_8)
    }

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

    private fun getOrCreateSecretKey(): SecretKey {
        cachedKey?.let { return it }

        val resolved = KeystorePassword.resolve(
            osStore = OsCredentialStore(),
            fileStore = FilePasswordStore(passwordFile),
            keystoreExists = keyStoreFile.exists(),
            opens = ::canOpen,
        )
        cachedSource = resolved.source
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

        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, password)
        val key = generateKey()
        keyStore.setEntry(KEY_ALIAS, KeyStore.SecretKeyEntry(key), KeyStore.PasswordProtection(password))
        keyStoreFile.outputStream().use { keyStore.store(it, password) }
        AppDirs.restrictToOwner(keyStoreFile)
        cachedKey = key
        return key
    }

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        return keyGenerator.generateKey()
    }
}
