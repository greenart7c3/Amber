package com.greenart7c3.nostrsigner.shared

/** The NIP-46 bunker RPC methods a signer can be asked to perform. */
enum class BunkerMethod {
    CONNECT,
    SIGN_EVENT,
    NIP04_ENCRYPT,
    NIP04_DECRYPT,
    NIP44_ENCRYPT,
    NIP44_DECRYPT,
    NIP44_V3_ENCRYPT,
    NIP44_V3_DECRYPT,
    GET_PUBLIC_KEY,
    DECRYPT_ZAP_EVENT,
    PING,
    SWITCH_RELAYS,
    SIGN_PSBT,
    LOGOUT,
    INVALID,
}

fun bunkerMethodOf(method: String): BunkerMethod = when (method) {
    "connect" -> BunkerMethod.CONNECT
    "sign_event" -> BunkerMethod.SIGN_EVENT
    "get_public_key" -> BunkerMethod.GET_PUBLIC_KEY
    "nip04_encrypt" -> BunkerMethod.NIP04_ENCRYPT
    "nip04_decrypt" -> BunkerMethod.NIP04_DECRYPT
    "nip44_encrypt" -> BunkerMethod.NIP44_ENCRYPT
    "nip44_decrypt" -> BunkerMethod.NIP44_DECRYPT
    "nip44v3_encrypt" -> BunkerMethod.NIP44_V3_ENCRYPT
    "nip44v3_decrypt" -> BunkerMethod.NIP44_V3_DECRYPT
    "decrypt_zap_event" -> BunkerMethod.DECRYPT_ZAP_EVENT
    "ping" -> BunkerMethod.PING
    "switch_relays" -> BunkerMethod.SWITCH_RELAYS
    "sign_psbt" -> BunkerMethod.SIGN_PSBT
    "logout" -> BunkerMethod.LOGOUT
    else -> BunkerMethod.INVALID
}
