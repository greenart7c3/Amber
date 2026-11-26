package com.greenart7c3.nostrsigner.models

import com.greenart7c3.nostrsigner.common.nip44v3.Nip44v3
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal

/** NIP-04, NIP-44 and NIP-44 v3 encryption with the account key, each checking key access first. */
class AccountCrypto internal constructor(
    private val signer: NostrSignerInternal,
    private val requirePrivateKeyAccess: () -> Unit,
) {
    suspend fun nip44Encrypt(plainText: String, toPublicKey: String): String {
        requirePrivateKeyAccess()
        return signer.nip44Encrypt(plainText, toPublicKey)
    }

    suspend fun nip04Encrypt(plainText: String, toPublicKey: String): String {
        requirePrivateKeyAccess()
        return signer.nip04Encrypt(plainText, toPublicKey)
    }

    suspend fun nip44Decrypt(cipherText: String, fromPublicKey: String): String {
        requirePrivateKeyAccess()
        return signer.nip44Decrypt(cipherText, fromPublicKey)
    }

    suspend fun nip04Decrypt(cipherText: String, fromPublicKey: String): String {
        requirePrivateKeyAccess()
        return signer.nip04Decrypt(cipherText, fromPublicKey)
    }

    fun nip44v3Encrypt(plainText: ByteArray, toPublicKey: String, kind: Int, scope: String): String {
        requirePrivateKeyAccess()
        return Nip44v3.encrypt(plainText, signer.keyPair.privKey!!, toPublicKey.hexToByteArray(), kind, scope)
    }

    fun nip44v3Decrypt(cipherText: String, fromPublicKey: String, kind: Int, scope: String): ByteArray {
        requirePrivateKeyAccess()
        return Nip44v3.decrypt(cipherText, signer.keyPair.privKey!!, fromPublicKey.hexToByteArray(), kind, scope)
    }

    suspend fun decrypt(encryptedContent: String, fromPublicKey: String): String {
        requirePrivateKeyAccess()
        return signer.decrypt(encryptedContent, fromPublicKey)
    }
}
