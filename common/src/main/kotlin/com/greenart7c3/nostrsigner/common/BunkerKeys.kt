package com.greenart7c3.nostrsigner.common

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto

/** A fresh private key for a per-connection NIP-46 key, in hex. */
fun generateBunkerPrivKey(): String = Nip01Crypto.privKeyCreate().toHexKey()

/** The public key of a per-connection private key, in hex; empty when [privKeyHex] is not a valid key. */
fun localPubKeyFromPrivKey(privKeyHex: String): String = try {
    KeyPair(privKey = privKeyHex.hexToByteArray()).pubKey.toHexKey()
} catch (_: Exception) {
    ""
}
