package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.PrivateZapEncryptedDataKind
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent

internal fun privateZapPreview(zapRequest: String?, account: Account): EncryptedDataKind? {
    if (zapRequest == null) return null
    val decrypted = try {
        account.decryptZapEvent(zapRequest)
    } catch (e: SecurityException) {
        throw e
    } catch (_: Exception) {
        null
    }
    return decrypted?.let(::PrivateZapEncryptedDataKind)
}

internal suspend fun previewOf(
    plaintext: String,
    requestInput: String,
    operationOutput: String,
    account: Account,
    onParseError: (String) -> Unit,
): EncryptedDataKind = try {
    when {
        plaintext.startsWith("{") -> {
            val event = AmberEvent.fromJson(plaintext)
            if (event.kind == SealEvent.KIND) {
                EventEncryptedDataKind(event, sealPreview(event, account, onParseError), operationOutput)
            } else {
                EventEncryptedDataKind(event, null, operationOutput)
            }
        }
        plaintext.startsWith("[") -> TagArrayEncryptedDataKind(JacksonMapper.fromJsonToTagArray(plaintext), operationOutput)
        else -> ClearTextEncryptedDataKind(requestInput, operationOutput)
    }
} catch (e: Exception) {
    onParseError("Error parsing ${if (plaintext.startsWith("{")) "JSON" else "TagArray"}: ${e.message}")
    ClearTextEncryptedDataKind(requestInput, operationOutput)
}

private suspend fun sealPreview(seal: AmberEvent, account: Account, onParseError: (String) -> Unit): EncryptedDataKind {
    val decrypted = try {
        account.crypto.decrypt(seal.content, account.hexKey)
    } catch (_: Exception) {
        seal.content
    }
    return try {
        when {
            decrypted.startsWith("{") -> EventEncryptedDataKind(AmberEvent.fromJson(decrypted), null, decrypted)
            decrypted.startsWith("[") -> TagArrayEncryptedDataKind(JacksonMapper.fromJsonToTagArray(decrypted), decrypted)
            else -> ClearTextEncryptedDataKind(seal.content, decrypted)
        }
    } catch (e: Exception) {
        onParseError("Error parsing TagArray: ${e.message}")
        ClearTextEncryptedDataKind(seal.content, decrypted)
    }
}
