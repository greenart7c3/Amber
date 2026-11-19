package com.greenart7c3.nostrsigner.desktop.core

import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.hasAmbiguousRelayTags
import com.greenart7c3.nostrsigner.common.nip44v3.Nip44v3
import com.greenart7c3.nostrsigner.common.psbt.PsbtSighashPolicy
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapRequestBuilder
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import kotlin.io.encoding.Base64

// [sealPreview]: a seal in an encrypt/decrypt payload, opened for the preview (openSealForPreview).
internal data class ComputedResult(val result: String, val preview: String, val sealPreview: String = "")

/**
 * The answer to [request] for [acc] (the NIP-46 and local socket paths share it) and
 * what the approval card shows. Throws when the request cannot be fulfilled.
 */
internal suspend fun computeResult(
    request: BunkerRequest,
    type: SignerType,
    acc: DesktopAccount,
): ComputedResult = when (type) {
    SignerType.CONNECT -> ComputedResult("ack", "")
    SignerType.GET_PUBLIC_KEY -> ComputedResult(acc.hexKey, acc.npub)
    SignerType.PING -> ComputedResult("pong", "")
    SignerType.SIGN_EVENT -> signEventResult(request as BunkerRequestSign, acc)
    SignerType.NIP04_ENCRYPT, SignerType.NIP44_ENCRYPT -> encryptResult(request, type, acc)
    SignerType.NIP04_DECRYPT, SignerType.NIP44_DECRYPT -> decryptResult(request, type, acc)
    SignerType.NIP44_V3_ENCRYPT, SignerType.NIP44_V3_DECRYPT -> nip44v3Result(request, type, acc)
    SignerType.DECRYPT_ZAP_EVENT -> {
        val zapEvent = Event.fromJson(request.params.first()) as ZapRequestEvent
        val decrypted = PrivateZapRequestBuilder().decryptZapEvent(zapEvent, acc.signer.signerSync).toJson()
        ComputedResult(decrypted, decrypted)
    }
    SignerType.SIGN_PSBT -> {
        val psbt = request.params.first()
        PsbtSighashPolicy.requireCommitsToAllOutputs(psbt, acc.signer.keyPair.pubKey)
        ComputedResult(acc.signer.signPsbt(psbt), psbt)
    }
    else -> throw IllegalArgumentException("Unsupported request type $type")
}

private suspend fun signEventResult(request: BunkerRequestSign, acc: DesktopAccount): ComputedResult {
    val template = request.event
    check(!hasAmbiguousRelayTags(template.kind, template.tags)) { "refusing a multi-relay auth event" }
    return ComputedResult(acc.signer.sign(template).toJson(), template.toJson())
}

// params: [pubkey, plaintext]
private suspend fun encryptResult(request: BunkerRequest, type: SignerType, acc: DesktopAccount): ComputedResult {
    val pubKey = request.params.first()
    val message = request.params.getOrElse(1) { "" }
    val cipher = if (type == SignerType.NIP04_ENCRYPT) acc.nip04Encrypt(message, pubKey) else acc.nip44Encrypt(message, pubKey)
    return ComputedResult(cipher, message, openSealForPreview(message, acc))
}

// params: [pubkey, ciphertext]
private suspend fun decryptResult(request: BunkerRequest, type: SignerType, acc: DesktopAccount): ComputedResult {
    val pubKey = request.params.first()
    val ciphertext = request.params.getOrElse(1) { "" }
    val plain = if (type == SignerType.NIP04_DECRYPT) acc.nip04Decrypt(ciphertext, pubKey) else acc.nip44Decrypt(ciphertext, pubKey)
    return ComputedResult(plain, plain, openSealForPreview(plain, acc))
}

// NIP-46 layout: [pubkey, kind, scope, base64 plaintext or the v3 payload]
private fun nip44v3Result(request: BunkerRequest, type: SignerType, acc: DesktopAccount): ComputedResult {
    val pubKey = request.params.first().hexToByteArray()
    val kind = request.params.getOrNull(1)?.toIntOrNull() ?: throw IllegalArgumentException("kind is required for nip44v3")
    val scope = request.params.getOrElse(2) { "" }
    val payload = request.params.getOrElse(3) { "" }
    val privKey = acc.signer.keyPair.privKey!!
    return if (type == SignerType.NIP44_V3_ENCRYPT) {
        val plainBytes = Base64.decode(payload)
        ComputedResult(Nip44v3.encrypt(plainBytes, privKey, pubKey, kind, scope), plainBytes.toString(Charsets.UTF_8))
    } else {
        val plainBytes = Nip44v3.decrypt(payload, privKey, pubKey, kind, scope)
        ComputedResult(Base64.encode(plainBytes), plainBytes.toString(Charsets.UTF_8))
    }
}
