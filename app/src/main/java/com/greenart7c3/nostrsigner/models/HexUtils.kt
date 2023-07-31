package com.greenart7c3.nostrsigner.models

import fr.acinq.secp256k1.Hex

/** Makes the distinction between String and Hex **/
typealias HexKey = String

fun ByteArray.toHexKey(): HexKey {
    return Hex.encode(this)
}

fun HexKey.hexToByteArray(): ByteArray {
    return Hex.decode(this)
}
