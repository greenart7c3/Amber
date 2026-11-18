package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent

/**
 * What an encrypt/decrypt approval shows, the same for intents and bunker requests:
 * [text] (the plaintext side: the data of an encrypt, the result of a decrypt) as an
 * event (a seal is opened for the preview only), a tag array or plain text. [data] is
 * the request's input and [result] the operation's output; [onParseError] gets the
 * message when [text] looks like JSON but does not parse.
 */
internal suspend fun previewOf(
    text: String,
    data: String,
    result: String,
    account: Account,
    onParseError: (String) -> Unit,
): EncryptedDataKind = try {
    when {
        text.startsWith("{") -> {
            val event = AmberEvent.fromJson(text)
            if (event.kind == SealEvent.KIND) {
                EventEncryptedDataKind(event, sealPreview(event, account, onParseError), result)
            } else {
                EventEncryptedDataKind(event, null, result)
            }
        }
        text.startsWith("[") -> TagArrayEncryptedDataKind(JacksonMapper.fromJsonToTagArray(text), result)
        else -> ClearTextEncryptedDataKind(data, result)
    }
} catch (e: Exception) {
    onParseError("Error parsing ${if (text.startsWith("{")) "JSON" else "TagArray"}: ${e.message}")
    ClearTextEncryptedDataKind(data, result)
}

/** The decrypted content of [seal]; the caller still gets the seal itself. */
private suspend fun sealPreview(seal: AmberEvent, account: Account, onParseError: (String) -> Unit): EncryptedDataKind {
    val decrypted = try {
        account.decrypt(seal.content, account.hexKey)
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
