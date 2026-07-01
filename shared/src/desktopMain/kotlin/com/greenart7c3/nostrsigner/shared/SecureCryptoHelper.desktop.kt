package com.greenart7c3.nostrsigner.shared

import com.github.javakeyring.Keyring
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * OS-native-keychain-backed implementation: a generated AES-256 master key is stored in the
 * platform credential store (Windows Credential Manager, macOS Keychain, or Linux Secret
 * Service/libsecret via [Keyring]), and secrets are AES/GCM-encrypted at rest with it — the
 * desktop analogue of the Android target's Keystore-backed key.
 *
 * Requires a running Secret Service provider (e.g. gnome-keyring, KWallet) on Linux; throws
 * [com.github.javakeyring.BackendNotSupportedException] if none is available.
 */
actual object SecureCryptoHelper {
    private const val KEYRING_DOMAIN = "Amber Bunker"
    private const val KEYRING_ACCOUNT = "master-key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_SIZE = 128
    private val mutex = Mutex()

    actual suspend fun encrypt(plainText: String): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val key = getOrCreateMasterKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combined = ByteBuffer.allocate(iv.size + cipherText.size)
            combined.put(iv)
            combined.put(cipherText)
            Base64.getEncoder().encodeToString(combined.array())
        }
    }

    actual suspend fun decrypt(encryptedText: String): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val key = getOrCreateMasterKey()
            val data = Base64.getDecoder().decode(encryptedText)
            val buffer = ByteBuffer.wrap(data)

            val iv = ByteArray(IV_SIZE).also { buffer.get(it) }
            val cipherText = ByteArray(buffer.remaining()).also { buffer.get(it) }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }
    }

    private fun getOrCreateMasterKey(): SecretKey = Keyring.create().use { keyring ->
        val existing = runCatching { keyring.getPassword(KEYRING_DOMAIN, KEYRING_ACCOUNT) }.getOrNull()
        val keyBytes = if (existing != null) {
            Base64.getDecoder().decode(existing)
        } else {
            val generated = KeyGenerator.getInstance("AES").apply { init(256, SecureRandom()) }.generateKey()
            keyring.setPassword(KEYRING_DOMAIN, KEYRING_ACCOUNT, Base64.getEncoder().encodeToString(generated.encoded))
            generated.encoded
        }
        SecretKeySpec(keyBytes, "AES")
    }
}
