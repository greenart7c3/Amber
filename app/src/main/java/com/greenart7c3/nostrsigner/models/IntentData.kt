package com.greenart7c3.nostrsigner.models

class IntentData(
    val data: String,
    val name: String,
    val type: SignerType,
    val pubKey: HexKey
)

enum class SignerType {
    SIGN_EVENT,
    NIP04_ENCRYPT,
    NIP04_DECRYPT,
    NIP44_ENCRYPT,
    NIP44_DECRYPT
}
