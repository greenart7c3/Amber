package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.common.nip44v3Kind
import com.greenart7c3.nostrsigner.common.nip44v3Scope
import com.greenart7c3.nostrsigner.common.peerPubKeyParam
import com.greenart7c3.nostrsigner.common.zapRequestParam
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.PrivateZapEncryptedDataKind
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Encrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Encrypt
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException

internal suspend fun bunkerEncryptedDataKind(
    bunkerRequest: BunkerRequest,
    acc: Account,
    onParseError: (String) -> Unit,
): EncryptedDataKind? {
    if (bunkerRequest.method == "nip44v3_encrypt" || bunkerRequest.method == "nip44v3_decrypt") return nip44v3EncryptedDataKind(bunkerRequest, acc)
    if (bunkerRequest.method == "decrypt_zap_event") return privateZapPreview(bunkerRequest.zapRequestParam(), acc)
    return when (bunkerRequest) {
        is BunkerRequestNip44Decrypt -> acc.crypto.nip44Decrypt(bunkerRequest.ciphertext, bunkerRequest.pubKey).let {
            previewOf(it, bunkerRequest.ciphertext, it, acc, onParseError)
        }
        is BunkerRequestNip44Encrypt -> previewOf(bunkerRequest.message, bunkerRequest.message, acc.crypto.nip44Encrypt(bunkerRequest.message, bunkerRequest.pubKey), acc, onParseError)
        is BunkerRequestNip04Decrypt -> acc.crypto.nip04Decrypt(bunkerRequest.ciphertext, bunkerRequest.pubKey).let {
            previewOf(it, bunkerRequest.ciphertext, it, acc, onParseError)
        }
        is BunkerRequestNip04Encrypt -> previewOf(bunkerRequest.message, bunkerRequest.message, acc.crypto.nip04Encrypt(bunkerRequest.message, bunkerRequest.pubKey), acc, onParseError)
        else -> null
    }
}

private fun nip44v3EncryptedDataKind(
    bunkerRequest: BunkerRequest,
    acc: Account,
): EncryptedDataKind? {
    val kind = bunkerRequest.nip44v3Kind() ?: return null
    val scope = bunkerRequest.nip44v3Scope()
    val data = BunkerRequestUtils.getDataFromBunker(bunkerRequest)
    val pubKey = bunkerRequest.peerPubKeyParam() ?: return null
    return try {
        if (bunkerRequest.method == "nip44v3_encrypt") {
            val plainBytes = Base64.decode(data)
            val ciphertext = acc.crypto.nip44v3Encrypt(plainBytes, pubKey, kind, scope)
            ClearTextEncryptedDataKind(plainBytes.toString(Charsets.UTF_8), ciphertext)
        } else {
            val plainBytes = acc.crypto.nip44v3Decrypt(data, pubKey, kind, scope)
            ClearTextEncryptedDataKind(plainBytes.toString(Charsets.UTF_8), Base64.encode(plainBytes))
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }
}

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
