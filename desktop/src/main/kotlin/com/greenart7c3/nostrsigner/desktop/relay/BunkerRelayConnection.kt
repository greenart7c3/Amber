package com.greenart7c3.nostrsigner.desktop.relay

import com.greenart7c3.nostrsigner.shared.BunkerSigningEngine
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Default relay set a freshly set-up bunker listens on; matches typical bunker:// connection strings. */
val DEFAULT_BUNKER_RELAYS: List<String> = listOf(
    "wss://relay.damus.io",
    "wss://relay.nostr.band",
    "wss://nos.lol",
)

/** Owns the relay connection for the desktop bunker: subscribes for kind-24133 requests, hands them to [engine], publishes replies. */
class BunkerRelayConnection(
    private val accountPubKey: String,
    private val engine: BunkerSigningEngine,
    private val scope: CoroutineScope,
    relayUrls: List<String> = DEFAULT_BUNKER_RELAYS,
) : RelayConnectionListener {
    val relays: Set<NormalizedRelayUrl> = relayUrls.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()

    private val httpClient = OkHttpClient.Builder().build()
    private val client = NostrClient(BasicOkHttpWebSocket.Builder { httpClient }, scope)
    private val subId = UUID.randomUUID().toString()

    fun start() {
        client.addConnectionListener(this)
        client.subscribe(
            subId,
            relays.associateWith {
                listOf(Filter(kinds = listOf(NostrConnectEvent.KIND), tags = mapOf("p" to listOf(accountPubKey))))
            },
        )
        client.connect()
    }

    fun stop() {
        client.unsubscribe(subId)
        client.disconnect()
        client.removeConnectionListener(this)
    }

    override fun onIncomingMessage(relay: IRelayClient, msgStr: String, msg: Message) {
        if (msg is EventMessage && msg.subId == subId) {
            scope.launch {
                val response = engine.handleIncomingEvent(msg.event.pubKey, msg.event.content) ?: return@launch
                client.publishAndConfirm(response, relays, timeoutInSeconds = 5)
            }
        }
    }
}
