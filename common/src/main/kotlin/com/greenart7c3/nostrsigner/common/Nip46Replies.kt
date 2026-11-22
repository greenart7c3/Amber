package com.greenart7c3.nostrsigner.common

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The key a NIP-46 reply is encrypted and signed with: the connection's own key
 * ([ConnectionReplyKey]), or the account's for a connection from before per-connection
 * keys (each app adapts its account, which checks key access first).
 */
interface Nip46ReplyKey {
    suspend fun nip04Encrypt(plainText: String, toPubKey: String): String

    suspend fun nip44Encrypt(plainText: String, toPubKey: String): String

    fun sign(createdAt: Long, kind: Int, tags: Array<Array<String>>, content: String): Event
}

/** A connection's own key ([privKeyHex], hex), which replies to its client. */
class ConnectionReplyKey(privKeyHex: String) : Nip46ReplyKey {
    private val signer = NostrSignerInternal(KeyPair(privKey = privKeyHex.hexToByteArray()))

    override suspend fun nip04Encrypt(plainText: String, toPubKey: String): String = signer.nip04Encrypt(plainText, toPubKey)

    override suspend fun nip44Encrypt(plainText: String, toPubKey: String): String = signer.nip44Encrypt(plainText, toPubKey)

    override fun sign(createdAt: Long, kind: Int, tags: Array<Array<String>>, content: String): Event = signer.signerSync.sign(createdAt, kind, tags, content)
}

/**
 * The kind-24133 event carrying [response] to [clientPubKey]: its JSON encrypted with
 * [encryptionType] (the one the request came in) and signed by [key], created now.
 */
suspend fun nip46ReplyEvent(
    response: BunkerResponse,
    clientPubKey: String,
    encryptionType: EncryptionType,
    key: Nip46ReplyKey,
): Event {
    val plainText = JacksonMapper.mapper.writeValueAsString(response)
    val content = if (encryptionType == EncryptionType.NIP44) key.nip44Encrypt(plainText, clientPubKey) else key.nip04Encrypt(plainText, clientPubKey)
    return key.sign(TimeUtils.now(), NostrConnectEvent.KIND, arrayOf(arrayOf("p", clientPubKey)), content)
}

/**
 * Publishes [response] to [clientPubKey] with up to [attempts] tries ([retryWithBackoff]),
 * each with a freshly built event, so a relay that refused a stale `created_at` may take
 * the next one. [publish] sends one event to the reply relays and says whether one
 * accepted it. True when the reply was published.
 */
suspend fun publishNip46Reply(
    response: BunkerResponse,
    clientPubKey: String,
    encryptionType: EncryptionType,
    key: Nip46ReplyKey,
    attempts: Int,
    publish: suspend (Event) -> Boolean,
): Boolean = retryWithBackoff(maxRetries = attempts) {
    publish(nip46ReplyEvent(response, clientPubKey, encryptionType, key))
}

/**
 * The log line for a reply: its id and error, never its result, which for a decrypt is
 * the plaintext.
 */
fun nip46ReplyLog(response: BunkerResponse): String = "id=${response.id} ${response.error?.let { "error=$it" } ?: "ok"}"
