package com.greenart7c3.nostrsigner.cli

import com.fasterxml.jackson.databind.JsonNode
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * The signer side of NIP-46, as small as the CLI needs it: one connection key
 * answering kind-24133 requests for its own p tag, the account key doing the
 * actual signing. Runs against the local relay in the end-to-end tests, so
 * nothing touches the network.
 */
class FakeSigner(
    relayUrl: String,
    val connectionKey: KeyPair = KeyPair(),
    val accountKey: KeyPair = KeyPair(),
    private val secret: String = "",
) {
    enum class Mode {
        ANSWER,
        SILENT,
        REFUSE,
    }

    private val mapper = JacksonMapper.mapper
    private val signer = NostrSignerInternal(connectionKey)
    val connectionPubKey: String = connectionKey.pubKey.toHexKey()
    val accountPubKey: String = accountKey.pubKey.toHexKey()

    @Volatile
    var mode: Mode = Mode.ANSWER

    @Volatile
    var switchRelaysTo: List<String>? = null

    /** The requests this signer received, in order, for assertions. */
    val received = java.util.Collections.synchronizedList(mutableListOf<String>())

    private val ownSubscriptionEose = CountDownLatch(1)

    private val http = OkHttpClient.Builder().pingInterval(30, TimeUnit.SECONDS).build()
    private val socket: WebSocket = http.newWebSocket(
        Request.Builder().url(relayUrl).build(),
        object : WebSocketListener() {
            override fun onOpen(
                webSocket: WebSocket,
                response: Response,
            ) {
                val filter = mapper.createObjectNode()
                filter.putArray("kinds").add(NostrConnectEvent.KIND)
                filter.putArray("#p").add(connectionPubKey)
                webSocket.send("[\"REQ\",\"fake-signer\",$filter]")
            }

            override fun onMessage(
                webSocket: WebSocket,
                text: String,
            ) {
                receive(text)
            }
        },
    )

    init {
        check(ownSubscriptionEose.await(20, TimeUnit.SECONDS)) {
            "the fake signer could not subscribe to $relayUrl, and the test relay drops requests sent before the subscription"
        }
    }

    /** Approves a nostrconnect URI: the signer answers with the URI's secret. */
    fun approve(uri: String) {
        val clientPubKey = uri.substringAfter("nostrconnect://").substringBefore('?')
        val uriSecret = Regex("[?&]secret=([^&]+)").find(uri)?.groupValues?.get(1)
            ?.let { URLDecoder.decode(it, "utf-8") }
            ?: error("the pairing URI has no secret: $uri")
        publish(clientPubKey, UUID.randomUUID().toString(), uriSecret, null)
    }

    fun close() {
        runCatching { socket.close(1000, null) }
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }

    private fun receive(text: String) {
        val command = runCatching { mapper.readTree(text) }.getOrNull() ?: return
        when (command.get(0)?.asText("")) {
            "EOSE" -> ownSubscriptionEose.countDown()
            "EVENT" -> validRequestToThisSigner(command)?.let { (event, request) -> handle(event, request) }
        }
    }

    private fun validRequestToThisSigner(command: JsonNode): Pair<Event, JsonNode>? {
        val event = command.get(2)?.toString()?.let { Event.fromJsonOrNull(it) } ?: return null
        if (event.kind != NostrConnectEvent.KIND || !event.verify() || !event.notifies(connectionPubKey)) return null
        val request = runCatching {
            mapper.readTree(signer.signerSync.nip44Decrypt(event.content, event.pubKey))
        }.getOrNull() ?: return null
        return event to request
    }

    private fun handle(
        event: Event,
        request: JsonNode,
    ) {
        val id = request.get("id")?.asText("").orEmpty()
        val method = request.get("method")?.asText("").orEmpty()
        received += method
        if (mode == Mode.SILENT) return
        if (mode == Mode.REFUSE) {
            publish(event.pubKey, id, "", "user rejected")
            return
        }
        val params = request.get("params") ?: mapper.createArrayNode()
        handleMethod(event.pubKey, id, method, params)
    }

    private fun handleMethod(
        clientPubKey: String,
        id: String,
        method: String,
        params: JsonNode,
    ) {
        when (method) {
            "connect" -> handleConnect(clientPubKey, id, params)
            "get_public_key" -> publish(clientPubKey, id, accountPubKey, null)
            "ping" -> publish(clientPubKey, id, "pong", null)
            "switch_relays" -> publish(clientPubKey, id, encodedSwitchRelays(), null)
            "logout" -> publish(clientPubKey, id, "ack", null)
            "sign_event" -> publish(clientPubKey, id, signWithAccountKey(params.get(0)?.asText().orEmpty()), null)
            "nip44_encrypt" -> publish(clientPubKey, id, signer.signerSync.nip44Encrypt(params.get(1).asText(""), params.get(0).asText("")), null)
            "nip44_decrypt" -> publish(clientPubKey, id, signer.signerSync.nip44Decrypt(params.get(1).asText(""), params.get(0).asText("")), null)
            "nip04_encrypt" -> publish(clientPubKey, id, signer.signerSync.nip04Encrypt(params.get(1).asText(""), params.get(0).asText("")), null)
            "nip04_decrypt" -> publish(clientPubKey, id, signer.signerSync.nip04Decrypt(params.get(1).asText(""), params.get(0).asText("")), null)
            else -> publish(clientPubKey, id, "", "Unrecognized method: $method")
        }
    }

    private fun handleConnect(
        clientPubKey: String,
        id: String,
        params: JsonNode,
    ) {
        if (params.get(1)?.asText("") == secret) {
            publish(clientPubKey, id, "ack", null)
        } else {
            publish(clientPubKey, id, "", "wrong secret")
        }
    }

    private fun encodedSwitchRelays(): String? = switchRelaysTo?.joinToString(",", "[", "]") { "\"$it\"" }

    private fun signWithAccountKey(templateJson: String): String {
        val template = EventTemplate.fromJson(templateJson)
        return NostrSignerInternal(accountKey).signerSync.sign<Event>(template).toJson()
    }

    private fun publish(
        clientPubKey: String,
        id: String,
        result: String?,
        error: String?,
    ) {
        val response: JsonNode = mapper.createObjectNode().apply {
            put("id", id)
            if (result != null) put("result", result) else putNull("result")
            if (error != null) put("error", error) else putNull("error")
        }
        val encrypted = signer.signerSync.nip44Encrypt(response.toString(), clientPubKey)
        val event = signer.signerSync.sign<Event>(
            TimeUtils.now(),
            NostrConnectEvent.KIND,
            arrayOf(arrayOf("p", clientPubKey)),
            encrypted,
        )
        socket.send("[\"EVENT\",${event.toJson()}]")
    }
}
