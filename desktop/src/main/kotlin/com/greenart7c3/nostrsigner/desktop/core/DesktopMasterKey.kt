package com.greenart7c3.nostrsigner.desktop.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The lifecycle of the AES-256 master key [DesktopKeyStore] encrypts with: loaded from the
 * PKCS12 keystore before a passphrase is set, installed and evicted by [PassphraseLock] after.
 *
 * Invariants:
 * - `masterKeyForWrapping` (which creates the key through the keystore path when none exists)
 *   must only be called while the passphrase lock is disabled or unlocked.
 * - [PassphraseLock.enable] writes the wrapped-key blob before deleting the unprotected copies,
 *   so a crash in between leaves both on disk. The blob's presence means the lock is enabled,
 *   so `cleanupUnprotectedCopiesIfWrapped` deletes any remaining unprotected copy (keystore file,
 *   password file, OS credential entry); it runs whenever key storage initializes.
 * - The keystore is replaced atomically ([AppDirs.writeAtomically]): a crash mid-write must never
 *   corrupt the only keystore, which would mean total key loss.
 */
object DesktopMasterKey {
    private const val KEY_ALIAS = "AMBER_AES_KEY"
    internal val mutex = Mutex()

    private val keyStoreFile: File get() = File(AppDirs.dataDir, "amber.keystore")
    private val passwordFile: File get() = File(AppDirs.dataDir, "keystore.pass")

    private var cachedKey: SecretKey? = null
    private var sourceDescription: String? = null

    /** Where the master key/its password is kept, for display in Settings. */
    val passwordSourceDescription: String?
        get() = sourceDescription

    internal fun installMasterKey(key: SecretKey, source: String) {
        cachedKey = key
        sourceDescription = source
    }

    internal fun clearMasterKey() {
        runCatching { (cachedKey as? WipeableAesKey)?.destroy() }
        cachedKey = null
        sourceDescription = null
    }

    internal suspend fun masterKeyForWrapping(): SecretKey = mutex.withLock {
        cachedKey ?: loadOrCreateFromKeystore()
    }

    internal fun removeUnprotectedCopies() {
        keyStoreFile.delete()
        FilePasswordStore(passwordFile).delete()
        OsCredentialStore().delete()
    }

    internal fun cleanupUnprotectedCopiesIfWrapped() {
        if (!PassphraseLock.isEnabled()) return
        if (keyStoreFile.exists() || passwordFile.exists() || OsCredentialStore().load() != null) {
            AmberLogger.d("DesktopKeyStore", "Deleting unprotected key copies left behind by an interrupted passphrase setup")
            removeUnprotectedCopies()
        }
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

    private fun writeKeystore(key: SecretKey, password: CharArray) {
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, password)
        keyStore.setEntry(KEY_ALIAS, KeyStore.SecretKeyEntry(SecretKeySpec(key.encoded, "AES")), KeyStore.PasswordProtection(password))
        storeKeystoreAtomically(keyStore, password)
    }

    private fun storeKeystoreAtomically(keyStore: KeyStore, password: CharArray) {
        val bytes = ByteArrayOutputStream().also { keyStore.store(it, password) }.toByteArray()
        AppDirs.writeAtomically(keyStoreFile, bytes)
    }

    @Synchronized
    internal fun getOrCreateSecretKey(): SecretKey {
        cachedKey?.let { return it }
        cleanupUnprotectedCopiesIfWrapped()
        if (PassphraseLock.isEnabled()) {
            throw DesktopKeyStore.LockedException()
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

        val key = if (keyStoreFile.exists()) {
            loadOrAddKey(password)
        } else {
            generateKey().also { writeKeystore(it, password) }
        }
        cachedKey = key
        return key
    }

    private fun loadOrAddKey(password: CharArray): SecretKey {
        val keyStore = loadKeyStore(password)
        val entry = keyStore.getEntry(KEY_ALIAS, KeyStore.PasswordProtection(password)) as? KeyStore.SecretKeyEntry
        if (entry != null) return WipeableAesKey.copyOf(entry.secretKey)
        val key = generateKey()
        keyStore.setEntry(KEY_ALIAS, KeyStore.SecretKeyEntry(SecretKeySpec(key.encoded, "AES")), KeyStore.PasswordProtection(password))
        storeKeystoreAtomically(keyStore, password)
        return key
    }

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        return WipeableAesKey.copyOf(keyGenerator.generateKey())
    }
}
