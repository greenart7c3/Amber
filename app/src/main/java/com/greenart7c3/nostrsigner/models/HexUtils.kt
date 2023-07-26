package com.greenart7c3.nostrsigner.models

import com.greenart7c3.nostrsigner.service.KeyPair
import com.greenart7c3.nostrsigner.toShortenHex
import com.greenart7c3.nostrsigner.service.bechToBytes
import com.greenart7c3.nostrsigner.service.nip19.Nip19
import fr.acinq.secp256k1.Hex

/** Makes the distinction between String and Hex **/
typealias HexKey = String

fun ByteArray.toHexKey(): HexKey {
    return Hex.encode(this)
}

fun HexKey.hexToByteArray(): ByteArray {
    return Hex.decode(this)
}

fun HexKey.toDisplayHexKey(): String {
    return this.toShortenHex()
}

fun decodePublicKey(key: String): ByteArray {
    val parsed = Nip19.uriToRoute(key)
    val pubKeyParsed = parsed?.hex?.hexToByteArray()

    return if (key.startsWith("nsec")) {
        KeyPair(privKey = key.bechToBytes()).pubKey
    } else if (pubKeyParsed != null) {
        pubKeyParsed
    } else {
        Hex.decode(key)
    }
}

fun decodePublicKeyAsHexOrNull(key: String): HexKey? {
    return try {
        val parsed = Nip19.uriToRoute(key)
        val pubKeyParsed = parsed?.hex

        if (key.startsWith("nsec")) {
            KeyPair(privKey = key.bechToBytes()).pubKey.toHexKey()
        } else if (pubKeyParsed != null) {
            pubKeyParsed
        } else {
            Hex.decode(key).toHexKey()
        }
    } catch (e: Exception) {
        null
    }
}
