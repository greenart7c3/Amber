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

object SecureCryptoHelper {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "AMBER_AES_KEY"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12 // 96 bits
    private const val TAG_SIZE = 128 // bits

    private var secretKey: SecretKey? = null

    fun encrypt(plainText: String): String {
        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)

        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv // System-generated, allowed

        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val combined = ByteBuffer.allocate(iv.size + cipherText.size)
        combined.put(iv)
        combined.put(cipherText)

        return Base64.encodeToString(combined.array(), Base64.NO_WRAP)
    }

    fun decrypt(encryptedText: String): String {
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
        secretKey?.let { return it }

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            secretKey = entry.secretKey
            return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val params = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                if (Amber.instance.hasStrongBox()) params.setIsStrongBoxBacked(true)
                keyGenerator.init(params.build())
            } catch (_: Exception) {
                params.setIsStrongBoxBacked(false)
                keyGenerator.init(params.build())
            }
        } else {
            keyGenerator.init(params.build())
        }

        secretKey = keyGenerator.generateKey()
        return secretKey!!
    }
}

fun Context.hasStrongBox(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
    packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
