package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.models.toHexKey

class KeyPair(
    privKey: ByteArray? = null,
    pubKey: ByteArray? = null
) {
    val privKey: ByteArray
    val pubKey: ByteArray

    init {
        if (privKey == null) {
            this.privKey = CryptoUtils.privkeyCreate()
            this.pubKey = CryptoUtils.pubkeyCreate(this.privKey)
        } else {
            // as private key is provided, ignore the public key and set keys according to private key
            this.privKey = privKey
            this.pubKey = pubKey ?: CryptoUtils.pubkeyCreate(privKey)
        }
    }

    override fun toString(): String {
        return "KeyPair(privateKey=${privKey.toHexKey()}, publicKey=${pubKey.toHexKey()}"
    }
}
