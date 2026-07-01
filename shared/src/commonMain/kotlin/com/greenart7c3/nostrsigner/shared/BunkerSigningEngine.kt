package com.greenart7c3.nostrsigner.shared

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip04Dm.crypto.EncryptedInfo
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/** A decrypted, parsed NIP-46 request together with the encoding it arrived in. */
private data class DecodedRequest(val bunkerRequest: BunkerRequest, val nip04: Boolean)

/**
 * Handles the NIP-46 "bunker" request/response cycle for a single account: decrypt an
 * incoming kind-24133 event, check permissions, perform the requested signing/encryption
 * operation, and produce the signed response event ready to publish.
 *
 * This is new, desktop-focused code — it mirrors the request/response semantics of
 * [com.greenart7c3.nostrsigner] Android's `BunkerRequestUtils`/`EventNotificationConsumer`
 * pipeline but does not attempt to replicate its Android-specific bookkeeping
 * (notifications, WorkManager, per-app relay/history persistence details).
 */
class BunkerSigningEngine(
    private val account: BunkerSigner,
    private val permissionStore: BunkerPermissionStore,
    private val approvalPort: BunkerApprovalPort,
    private val historyLogger: BunkerHistoryLogger? = null,
) {
    /** Decrypts, handles, and produces a signed response event for an incoming kind-24133 event; null if it should be ignored. */
    suspend fun handleIncomingEvent(senderPubKey: String, encryptedContent: String, appName: String? = null): Event? {
        if (encryptedContent.isEmpty()) return null

        val decoded = decode(senderPubKey, encryptedContent) ?: return null
        val request = decoded.bunkerRequest
        val method = bunkerMethodOf(request.method)
        val kind = if (method == BunkerMethod.SIGN_EVENT) signEventKind(request) else null

        val approved = resolveApproval(senderPubKey, appName, method, kind, request)
        historyLogger?.log(BunkerHistoryEntry(senderPubKey, method, kind, approved, TimeUtils.now()))

        val response = if (!approved) {
            BunkerResponse(request.id, "", "user rejected")
        } else {
            runCatching { perform(method, request, senderPubKey) }
                .fold(
                    onSuccess = { result -> BunkerResponse(request.id, result, null) },
                    onFailure = { error -> BunkerResponse(request.id, "", error.message ?: "signing failed") },
                )
        }

        return respond(senderPubKey, response, decoded.nip04)
    }

    private suspend fun decode(senderPubKey: String, encryptedContent: String): DecodedRequest? {
        val nip04 = EncryptedInfo.isNIP04(encryptedContent)
        val plainText = runCatching { account.decrypt(encryptedContent, senderPubKey) }.getOrNull() ?: return null
        val bunkerRequest = runCatching {
            JacksonMapper.mapper.readValue(plainText, BunkerRequest::class.java)
        }.getOrNull() ?: return null
        return DecodedRequest(bunkerRequest, nip04)
    }

    private suspend fun resolveApproval(
        senderPubKey: String,
        appName: String?,
        method: BunkerMethod,
        kind: Int?,
        request: BunkerRequest,
    ): Boolean {
        permissionStore.isApproved(senderPubKey, method, kind)?.let { return it }

        val decision = approvalPort.requestApproval(
            BunkerApprovalRequest(
                appPubKey = senderPubKey,
                appName = appName,
                method = method,
                kind = kind,
                payloadPreview = BunkerRequestPayload.payload(request),
            ),
        )
        if (decision.remember) {
            permissionStore.remember(senderPubKey, method, kind, decision.approved)
        }
        return decision.approved
    }

    private suspend fun perform(method: BunkerMethod, request: BunkerRequest, senderPubKey: String): String = when (method) {
        BunkerMethod.CONNECT -> "ack"
        BunkerMethod.GET_PUBLIC_KEY -> account.pubKey
        BunkerMethod.PING -> "pong"
        BunkerMethod.LOGOUT -> "ack"
        BunkerMethod.SIGN_EVENT -> signEvent(request)
        BunkerMethod.NIP04_ENCRYPT -> account.nip04Encrypt(BunkerRequestPayload.payload(request), counterparty(request, senderPubKey))
        BunkerMethod.NIP04_DECRYPT -> account.nip04Decrypt(BunkerRequestPayload.payload(request), counterparty(request, senderPubKey))
        BunkerMethod.NIP44_ENCRYPT -> account.nip44Encrypt(BunkerRequestPayload.payload(request), counterparty(request, senderPubKey))
        BunkerMethod.NIP44_DECRYPT -> account.nip44Decrypt(BunkerRequestPayload.payload(request), counterparty(request, senderPubKey))
        BunkerMethod.NIP44_V3_ENCRYPT, BunkerMethod.NIP44_V3_DECRYPT,
        BunkerMethod.DECRYPT_ZAP_EVENT, BunkerMethod.SWITCH_RELAYS, BunkerMethod.SIGN_PSBT,
        -> error("Unsupported method: ${request.method}")
        BunkerMethod.INVALID -> error("Unrecognized method: ${request.method}")
    }

    private fun counterparty(request: BunkerRequest, senderPubKey: String) = BunkerRequestPayload.counterpartyPubKey(request) ?: senderPubKey

    private fun signEventKind(request: BunkerRequest): Int? {
        val json = BunkerRequestPayload.payload(request)
        if (json.isEmpty()) return null
        return runCatching { JacksonMapper.mapper.readTree(json).get("kind")?.asInt() }.getOrNull()
    }

    private fun signEvent(request: BunkerRequest): String {
        val json = BunkerRequestPayload.payload(request)
        val node = JacksonMapper.mapper.readTree(json)
        val kind = node.get("kind")?.asInt() ?: error("sign_event request missing kind")
        val content = node.get("content")?.asText() ?: ""
        val createdAt = node.get("created_at")?.asLong() ?: TimeUtils.now()
        val tags = node.get("tags")?.map { tagNode ->
            tagNode.map { it.asText() }.toTypedArray()
        }?.toTypedArray() ?: arrayOf()

        val signed = account.signSync<Event>(createdAt, kind, tags, content)
        return signed.toJson()
    }

    private suspend fun respond(recipientPubKey: String, response: BunkerResponse, nip04: Boolean): Event {
        val plainText = JacksonMapper.mapper.writeValueAsString(response)
        val encryptedContent = if (nip04) account.nip04Encrypt(plainText, recipientPubKey) else account.nip44Encrypt(plainText, recipientPubKey)
        return account.signSync(
            TimeUtils.now(),
            NostrConnectEvent.KIND,
            arrayOf(arrayOf("p", recipientPubKey)),
            encryptedContent,
        )
    }
}
