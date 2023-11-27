package com.greenart7c3.nostrsigner.models

import androidx.compose.runtime.MutableState
import com.vitorpamplona.quartz.encoders.HexKey

data class Permission(
    val type: String,
    val kind: Int?,
    var checked: Boolean = true
) {
    override fun toString(): String {
        return when (type) {
            "nip04_encrypt" -> {
                "Encrypt data using nip 4"
            }
            "nip04_decrypt" -> {
                "Decrypt data using nip 4"
            }
            "nip44_decrypt" -> {
                "Decrypt data using nip 44"
            }
            "nip44_encrypt" -> {
                "Encrypt data using nip 44"
            }
            "decrypt_zap_event" -> {
                "Decrypt private zaps"
            }
            "sign_event" -> {
                when (kind) {
                    22242 -> "Client authentication"
                    else -> "Event kind $kind"
                }
            }
            else -> type
        }
    }
}

class IntentData(
    val data: String,
    val name: String,
    val type: SignerType,
    val pubKey: HexKey,
    val id: String,
    val callBackUrl: String?,
    val compression: CompressionType,
    val returnType: ReturnType,
    val permissions: List<Permission>?,
    val currentAccount: String,
    val checked: MutableState<Boolean>,
    val rememberMyChoice: MutableState<Boolean>
)

enum class SignerType {
    SIGN_EVENT,
    NIP04_ENCRYPT,
    NIP04_DECRYPT,
    NIP44_ENCRYPT,
    NIP44_DECRYPT,
    GET_PUBLIC_KEY,
    DECRYPT_ZAP_EVENT
}

enum class ReturnType {
    SIGNATURE,
    EVENT
}

enum class CompressionType {
    NONE,
    GZIP
}
