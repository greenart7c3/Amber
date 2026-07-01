package com.greenart7c3.nostrsigner.shared

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal

/** Thin, platform-agnostic wrapper around a Quartz [NostrSignerInternal] used by the bunker signing engine. */
class BunkerSigner(keyPair: KeyPair) {
    private val signer = NostrSignerInternal(keyPair)

    val pubKey: String get() = signer.pubKey

    suspend fun nip44Encrypt(plainText: String, toPublicKey: String): String = signer.nip44Encrypt(plainText, toPublicKey)

    suspend fun nip44Decrypt(cipherText: String, fromPublicKey: String): String = signer.nip44Decrypt(cipherText, fromPublicKey)

    suspend fun nip04Encrypt(plainText: String, toPublicKey: String): String = signer.nip04Encrypt(plainText, toPublicKey)

    suspend fun nip04Decrypt(cipherText: String, fromPublicKey: String): String = signer.nip04Decrypt(cipherText, fromPublicKey)

    /** Decrypts a NIP-46 payload, auto-detecting NIP-04 vs NIP-44 encoding. */
    suspend fun decrypt(encryptedContent: String, fromPublicKey: String): String = signer.decrypt(encryptedContent, fromPublicKey)

    /** Signs a raw event, mirroring [com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync.sign]. */
    fun <T : com.vitorpamplona.quartz.nip01Core.core.Event> signSync(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): T = signer.signerSync.sign(createdAt, kind, tags, content)
}
