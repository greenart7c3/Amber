package com.greenart7c3.nostrsigner

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SecureCryptoHelper {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "AMBER_AES_KEY"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12 // 96 bits
    private const val TAG_SIZE = 128 // bits
    private val mutex = Mutex()

    suspend fun encrypt(plainText: String): String = mutex.withLock {
        encryptWithKey(getOrCreateSecretKey(), plainText)
    }

    suspend fun decrypt(encryptedText: String): String = mutex.withLock {
        decryptWithKey(getOrCreateSecretKey(), encryptedText)
    }

    /**
     * Non-suspending equivalent of [encrypt] for callers that cannot suspend
     * (Room [Migration.migrate] callbacks, [getByKeySync] synchronous DAO
     * reads). The AndroidKeyStore Cipher instance is thread-safe to obtain and
     * use; the suspend variant's [mutex] only guards against concurrent
     * in-flight cipher init within coroutines and is intentionally omitted
     * here so blocking callers do not need a [kotlinx.coroutines.runBlocking]
     * bridge.
     */
    fun encryptBlocking(plainText: String): String {
        return encryptWithKey(getOrCreateSecretKey(), plainText)
    }

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
    fun decryptBlocking(encryptedText: String): String {
        return decryptWithKey(getOrCreateSecretKey(), encryptedText)
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

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        return generateSecretKey(Amber.instance.settings.requireUnlockedDevice)
    }

    private fun getKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun generateSecretKey(requireUnlockedDevice: Boolean): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val paramsBuilder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
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
                AmberLog.w("SecureCryptoHelper", "StrongBox generation failed, falling back to TEE", e)
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
     * Rotates the [KEY_ALIAS] Keystore key, re-encrypting every stored secret
     * (per-account DataStore keys, app DataStore PIN, and WebDAV password).
     *
     * The Keystore `setUnlockedDeviceRequired` flag is set at key-generation
     * time, so toggling the opt-in policy requires destroying and recreating
     * the key. The rotation is safe-by-design: all secrets are decrypted
     * with the old key BEFORE the old key is deleted, so a mid-rotation crash
     * leaves the old key intact (the delete is the only destructive step and
     * it runs after all decryptions succeed).
     *
     * Uses internal non-locking cipher methods ([encryptWithKey]/
     * [decryptWithKey]) and raw DataStore helpers to avoid mutex reentrancy
     * (Kotlin's [Mutex] is not reentrant).
     */
    suspend fun rotateKey(context: Context, requireUnlockedDevice: Boolean) = mutex.withLock {
        val keyStore = getKeyStore()
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateSecretKey(requireUnlockedDevice)
            return@withLock
        }

        // 1. Get the old key and decrypt all stored secrets (before deleting).
        val oldEntry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        val oldKey = oldEntry.secretKey

        val accounts = LocalPreferences.allSavedAccounts(context)
        val decryptedAccountKeys = mutableMapOf<String, Pair<String?, String?>>()

        for (acc in accounts) {
            val rawPrivKey = DataStoreAccess.getEncryptedRaw(context, acc.npub, DataStoreAccess.NOSTR_PRIVKEY)
            val rawSeedWords = DataStoreAccess.getEncryptedRaw(context, acc.npub, DataStoreAccess.SEED_WORDS)
            decryptedAccountKeys[acc.npub] = Pair(
                rawPrivKey?.let {
                    try {
                        decryptWithKey(oldKey, it)
                    } catch (_: Exception) {
                        null
                    }
                },
                rawSeedWords?.let {
                    try {
                        decryptWithKey(oldKey, it)
                    } catch (_: Exception) {
                        null
                    }
                },
            )
        }

        val rawPin = DataStoreAccess.getPinRaw(context)
        val decryptedPin = rawPin?.let {
            try {
                decryptWithKey(oldKey, it)
            } catch (_: Exception) {
                null
            }
        }

        val encryptedWebDavPassword = LocalPreferences.getWebDavPasswordEncrypted(context)
        val decryptedWebDavPassword = if (encryptedWebDavPassword.isNotBlank()) {
            try {
                decryptWithKey(oldKey, encryptedWebDavPassword)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        // 2. Delete the old key and generate a new one with the new policy.
        keyStore.deleteEntry(KEY_ALIAS)
        val newKey = generateSecretKey(requireUnlockedDevice)

        // 3. Re-encrypt and store all secrets with the new key.
        for ((npub, keys) in decryptedAccountKeys) {
            keys.first?.let {
                DataStoreAccess.saveEncryptedRaw(context, npub, DataStoreAccess.NOSTR_PRIVKEY, encryptWithKey(newKey, it))
            }
            keys.second?.let {
                DataStoreAccess.saveEncryptedRaw(context, npub, DataStoreAccess.SEED_WORDS, encryptWithKey(newKey, it))
            }
        }
        decryptedPin?.let {
            DataStoreAccess.savePinRaw(context, encryptWithKey(newKey, it))
        }
        decryptedWebDavPassword?.let {
            LocalPreferences.saveWebDavPasswordEncrypted(context, encryptWithKey(newKey, it))
        }

        AmberLog.d("SecureCryptoHelper", "Key rotation complete (requireUnlockedDevice=$requireUnlockedDevice)")
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
