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
}
