package com.greenart7c3.nostrsigner.desktop.core

import com.greenart7c3.nostrsigner.desktop.core.DesktopMasterKey.getOrCreateSecretKey
import com.greenart7c3.nostrsigner.desktop.core.DesktopMasterKey.mutex
import java.nio.ByteBuffer
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
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
 *   installed in [DesktopMasterKey] at unlock time and evicted on lock.
 */
object DesktopKeyStore {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12 // 96 bits
    private const val TAG_SIZE = 128 // bits

    // ':' is outside the Base64 alphabet, so legacy values can never match.
    private const val BOUND_PREFIX = "v2:"

    class LockedException : IllegalStateException("The key store is locked. Unlock it with your passphrase first.")

    suspend fun encrypt(plainText: String, context: String): String = mutex.withLock { encryptString(plainText, context) }

    suspend fun decrypt(encryptedText: String, context: String): String = mutex.withLock { decryptString(encryptedText, context) }

    /** [encryptBytes] for secrets the caller wipes afterward (private keys). */
    suspend fun encrypt(plain: ByteArray, context: String): String = mutex.withLock { encryptBytes(plain, context) }

    /** [decryptBytes]: the caller owns the result and should wipe it after use. */
    suspend fun decryptToBytes(encryptedText: String, context: String): ByteArray = mutex.withLock { decryptBytes(encryptedText, context) }

    /**
     * Synchronous AES-256-GCM encryption with the master key, for the storage
     * layer (which reads/writes files on its own threads). Requires the master
     * key to be available — throws [LockedException] when the passphrase lock
     * is engaged and the app has not been unlocked.
     *
     * [context] names what the value is (e.g. "npub1…/privkey") and is
     * authenticated as GCM associated data: a ciphertext only decrypts under
     * the context it was written for, so someone who can edit the data
     * directory cannot move one account's key, seed words or database into
     * another account's slot.
     */
    fun encryptString(plainText: String, context: String): String {
        val plain = plainText.toByteArray(Charsets.UTF_8)
        return try {
            encryptBytes(plain, context)
        } finally {
            plain.fill(0)
        }
    }

    /** Byte-level [encryptString]; [plain] is left untouched for the caller to wipe. */
    fun encryptBytes(plain: ByteArray, context: String): String {
        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad(context))
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain)
        val combined = ByteBuffer.allocate(iv.size + cipherText.size)
        combined.put(iv)
        combined.put(cipherText)
        return BOUND_PREFIX + Base64.getEncoder().withoutPadding().encodeToString(combined.array())
    }

    /**
     * Decrypts a value written by [encryptString] for the same [context].
     * Unbound ciphertexts (a format only pre-release builds ever wrote) are
     * always rejected: without a released predecessor there is nothing to
     * migrate, so refusing them cannot strand a real install.
     */
    fun decryptString(encryptedText: String, context: String): String {
        val plain = decryptBytes(encryptedText, context)
        return try {
            String(plain, Charsets.UTF_8)
        } finally {
            plain.fill(0)
        }
    }

    /** Byte-level [decryptString], so a secret never has to pass through an unwipeable String. */
    fun decryptBytes(encryptedText: String, context: String): ByteArray {
        require(isBound(encryptedText)) { "Refusing an unbound ciphertext for $context" }
        val key = getOrCreateSecretKey()
        val data = Base64.getDecoder().decode(encryptedText.substring(BOUND_PREFIX.length))
        val buffer = ByteBuffer.wrap(data)
        val iv = ByteArray(IV_SIZE).also { buffer.get(it) }
        val cipherText = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))
        cipher.updateAAD(aad(context))
        return cipher.doFinal(cipherText)
    }

    /** Whether [encryptedText] carries the context binding (current format). */
    fun isBound(encryptedText: String): Boolean = encryptedText.startsWith(BOUND_PREFIX)

    private fun aad(context: String): ByteArray = "amber-desktop:v2:$context".toByteArray(Charsets.UTF_8)
}

/**
 * An AES key whose bytes can actually be wiped: the JDK's SecretKeySpec does
 * not implement destroy() (it throws DestroyFailedException), so a master key
 * held in one would stay readable in memory after a lock until the GC reuses
 * the space. [getEncoded] hands out copies, as the SecretKey contract requires.
 */
internal class WipeableAesKey(raw: ByteArray) : SecretKey {
    private val key = raw.copyOf()

    @Volatile
    private var destroyed = false

    override fun getAlgorithm(): String = "AES"

    override fun getFormat(): String = "RAW"

    override fun getEncoded(): ByteArray {
        check(!destroyed) { "The key has been destroyed" }
        return key.copyOf()
    }

    override fun destroy() {
        destroyed = true
        key.fill(0)
    }

    override fun isDestroyed(): Boolean = destroyed

    companion object {
        /** Copies [source] (and wipes the intermediate encoding). */
        fun copyOf(source: SecretKey): WipeableAesKey {
            val raw = source.encoded
            return try {
                WipeableAesKey(raw)
            } finally {
                raw.fill(0)
            }
        }
    }
}
