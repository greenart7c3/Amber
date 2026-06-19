package com.greenart7c3.nostrsigner.models

import com.greenart7c3.nostrsigner.service.model.AmberEvent

interface EncryptedDataKind {
    val result: String
}

class ClearTextEncryptedDataKind(val text: String, override val result: String) : EncryptedDataKind

class TagArrayEncryptedDataKind(val tagArray: Array<Array<String>>, override val result: String) : EncryptedDataKind

class EventEncryptedDataKind(val event: AmberEvent, val sealEncryptedDataKind: EncryptedDataKind?, override val result: String) : EncryptedDataKind

class PrivateZapEncryptedDataKind(override val result: String) : EncryptedDataKind

fun EncryptedDataKind?.toPermissionType(isEncrypt: Boolean): String = when (this) {
    is ClearTextEncryptedDataKind -> if (isEncrypt) "ENCRYPT_CLEAR_TEXT" else "DECRYPT_CLEAR_TEXT"
    is TagArrayEncryptedDataKind -> if (isEncrypt) "ENCRYPT_TAG_ARRAY" else "DECRYPT_TAG_ARRAY"
    is EventEncryptedDataKind -> if (isEncrypt) "ENCRYPT_EVENT" else "DECRYPT_EVENT"
    is PrivateZapEncryptedDataKind -> "DECRYPT_ZAP_EVENT"
    null -> if (isEncrypt) "ENCRYPT_CLEAR_TEXT" else "DECRYPT_CLEAR_TEXT"
    else -> if (isEncrypt) "ENCRYPT_CLEAR_TEXT" else "DECRYPT_CLEAR_TEXT"
}

fun SignerType.toPermissionTypeString(encryptedData: EncryptedDataKind?): String = when (this) {
    SignerType.NIP04_ENCRYPT, SignerType.NIP44_ENCRYPT -> encryptedData.toPermissionType(isEncrypt = true)
    SignerType.NIP04_DECRYPT, SignerType.NIP44_DECRYPT -> encryptedData.toPermissionType(isEncrypt = false)
    SignerType.NIP44_V3_ENCRYPT, SignerType.NIP44_V3_DECRYPT -> this.toString()
    SignerType.DECRYPT_ZAP_EVENT -> "DECRYPT_ZAP_EVENT"
    else -> this.toString()
}

/**
 * The readable NIP-44 v3 plaintext. For v3 the data kind stores the real
 * plaintext in `text` and the Base64 wire value in `result`, so history/display
 * read `text` and never touch the wire encoding.
 */
fun EncryptedDataKind?.nip44v3Plaintext(): String = (this as? ClearTextEncryptedDataKind)?.text ?: this?.result ?: ""

val encryptDecryptSignerTypes = setOf(
    SignerType.NIP04_ENCRYPT,
    SignerType.NIP44_ENCRYPT,
    SignerType.NIP04_DECRYPT,
    SignerType.NIP44_DECRYPT,
    SignerType.NIP44_V3_ENCRYPT,
    SignerType.NIP44_V3_DECRYPT,
    SignerType.DECRYPT_ZAP_EVENT,
)

val encryptSignerTypes = setOf(
    SignerType.NIP04_ENCRYPT,
    SignerType.NIP44_ENCRYPT,
    SignerType.NIP44_V3_ENCRYPT,
)

/**
 * History rows for these request types store the ciphertext (encrypted form) in
 * `content` rather than the plaintext, so the value must never be truncated —
 * a partial ciphertext can no longer be decrypted on demand.
 */
val encryptDecryptHistoryTypeNames: Set<String> = encryptDecryptSignerTypes.mapTo(mutableSetOf()) { it.name }

/**
 * Determines the permission type string based on content string and operation direction.
 * For ENCRYPT: pass the plaintext to classify what kind of data is being encrypted.
 * For DECRYPT: pass the decrypted plaintext to classify what was decrypted.
 */
fun permissionTypeFromContent(content: String, isEncrypt: Boolean, signerType: SignerType): String = when {
    signerType == SignerType.DECRYPT_ZAP_EVENT -> "DECRYPT_ZAP_EVENT"
    content.startsWith("{") -> if (isEncrypt) "ENCRYPT_EVENT" else "DECRYPT_EVENT"
    content.startsWith("[") -> if (isEncrypt) "ENCRYPT_TAG_ARRAY" else "DECRYPT_TAG_ARRAY"
    else -> if (isEncrypt) "ENCRYPT_CLEAR_TEXT" else "DECRYPT_CLEAR_TEXT"
}
