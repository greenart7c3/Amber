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
        encryptBlocking(plainText)
    }

    suspend fun decrypt(encryptedText: String): String = mutex.withLock {
        decryptBlocking(encryptedText)
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
        val key = getOrCreateSecretKey()
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
        val key = getOrCreateSecretKey()
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

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val paramsBuilder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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
}

fun Context.hasStrongBox(): Boolean {
    val isMediaTek = Build.HARDWARE.lowercase().contains("mt") ||
        Build.BOARD.lowercase().contains("mt") ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.SOC_MANUFACTURER.lowercase().contains("mediatek")) // usually mediatek contains broken strongbox support

    return !isMediaTek &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
}
