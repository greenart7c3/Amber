package com.greenart7c3.nostrsigner.shared

/** Encrypts/decrypts local secrets (private keys, connection keys) at rest using a platform-backed key. */
expect object SecureCryptoHelper {
    suspend fun encrypt(plainText: String): String

    suspend fun decrypt(encryptedText: String): String
}
