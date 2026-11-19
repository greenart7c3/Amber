package com.greenart7c3.nostrsigner.desktop.core

import com.greenart7c3.nostrsigner.common.BUNKER_REPLY_ATTEMPTS
import com.greenart7c3.nostrsigner.common.EncryptionType
import com.greenart7c3.nostrsigner.common.retryWithBackoff
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils

// Publishing a reply to a NIP-46 client.

internal suspend fun BunkerEngine.sendResponse(
    account: DesktopAccount,
    signerPrivKey: String,
    localKey: String,
    encryptionType: EncryptionType,
    bunkerResponse: BunkerResponse,
    relays: List<NormalizedRelayUrl>,
    attempts: Int = BUNKER_REPLY_ATTEMPTS,
): Boolean {
    val store = AmberDesktop.store(account.npub)
    if (relays.isEmpty()) {
        store.addLog(localKey, "bunker response", "No relays to send the response to")
        return false
    }

    val connSigner = if (signerPrivKey.isNotEmpty()) {
        NostrSignerInternal(KeyPair(privKey = signerPrivKey.hexToByteArray()))
    } else {
        null
    }

    val plainText = JacksonMapper.mapper.writeValueAsString(bunkerResponse)

    suspend fun buildEvent(): Event {
        val encryptedContent = if (encryptionType == EncryptionType.NIP44) {
            connSigner?.nip44Encrypt(plainText, localKey) ?: account.nip44Encrypt(plainText, localKey)
        } else {
            connSigner?.nip04Encrypt(plainText, localKey) ?: account.nip04Encrypt(plainText, localKey)
        }
        return connSigner?.signerSync?.sign(
            TimeUtils.now(),
            NostrConnectEvent.KIND,
            arrayOf(arrayOf("p", localKey)),
            encryptedContent,
        ) ?: account.signSync(
            TimeUtils.now(),
            NostrConnectEvent.KIND,
            arrayOf(arrayOf("p", localKey)),
            encryptedContent,
        )
    }

    val publish: suspend () -> Boolean = {
        client.publishAndConfirm(
            event = buildEvent(),
            relayList = relays.toSet(),
            timeoutInSeconds = 5,
            untilFirstAccept = true,
        )
    }

    val success = retryWithBackoff(maxRetries = attempts, block = publish)

    val sanitized = "id=${bunkerResponse.id} ${bunkerResponse.error?.let { "error=$it" } ?: "ok"} sent=$success"
    relays.forEach { store.addLog(it.url, "bunker response", sanitized) }
    return success
}
