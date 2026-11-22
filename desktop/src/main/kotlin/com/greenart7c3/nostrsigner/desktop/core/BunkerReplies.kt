package com.greenart7c3.nostrsigner.desktop.core

import com.greenart7c3.nostrsigner.common.BUNKER_REPLY_ATTEMPTS
import com.greenart7c3.nostrsigner.common.ConnectionReplyKey
import com.greenart7c3.nostrsigner.common.EncryptionType
import com.greenart7c3.nostrsigner.common.Nip46ReplyKey
import com.greenart7c3.nostrsigner.common.nip46ReplyLog
import com.greenart7c3.nostrsigner.common.publishNip46Reply
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse

// Publishing a reply to a NIP-46 client; building and retrying it is the common publishNip46Reply.

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

    val key = if (signerPrivKey.isNotEmpty()) ConnectionReplyKey(signerPrivKey) else account.replyKey()
    val success = publishNip46Reply(bunkerResponse, localKey, encryptionType, key, attempts) { event ->
        client.publishAndConfirm(event = event, relayList = relays.toSet(), timeoutInSeconds = 5, untilFirstAccept = true)
    }

    val logLine = "${nip46ReplyLog(bunkerResponse)} sent=$success"
    relays.forEach { store.addLog(it.url, "bunker response", logLine) }
    return success
}

// The account key, for a connection from before per-connection keys.
private fun DesktopAccount.replyKey(): Nip46ReplyKey = object : Nip46ReplyKey {
    override suspend fun nip04Encrypt(plainText: String, toPubKey: String): String = this@replyKey.nip04Encrypt(plainText, toPubKey)

    override suspend fun nip44Encrypt(plainText: String, toPubKey: String): String = this@replyKey.nip44Encrypt(plainText, toPubKey)

    override fun sign(createdAt: Long, kind: Int, tags: Array<Array<String>>, content: String): Event = signSync(createdAt, kind, tags, content)
}
