package com.greenart7c3.nostrsigner.common

/** The operation a request asks the signer for, on every request path of both apps. */
enum class SignerType {
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
    INVALID,
    SWITCH_RELAYS,
    SIGN_PSBT,
    LOGOUT,
}

/** How a NIP-46 client encrypts its requests, and so how the response is encrypted. */
enum class EncryptionType {
    NIP44,
    NIP04,
}
