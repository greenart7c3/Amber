package com.greenart7c3.nostrsigner.models

enum class SignerType {
    CONNECT,
    SIGN_MESSAGE,
    SIGN_EVENT,
    NIP04_ENCRYPT,
    NIP04_DECRYPT,
    NIP44_ENCRYPT,
    NIP44_DECRYPT,
    GET_PUBLIC_KEY,
    DECRYPT_ZAP_EVENT,
    INVALID,
}
