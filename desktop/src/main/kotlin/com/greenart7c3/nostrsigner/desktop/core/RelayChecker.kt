package com.greenart7c3.nostrsigner.desktop.core

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Validates a relay before adding it, mirroring the mobile `onAddRelay` flow:
 * publish a throwaway kind-24133 event with a fresh key and confirm the relay
 * both accepts it and serves it back through a `#p` subscription — proving it
 * is actually usable as a bunker relay, not just reachable.
 */
object RelayChecker {
    enum class Outcome {
        /** Relay accepted and returned the test event (or is already in use). */
        OK,

        /** Could not open a connection to the relay at all. */
        CANNOT_CONNECT,

        /** Connected, but the relay rejected the event or won't serve the filter. */
        FILTER_FAILED,
    }

    /** Mirrors the mobile `Amber.isPrivateIp`. */
    fun isPrivateIp(url: String): Boolean = url.contains("127.0.0.1") ||
        url.contains("localhost") ||
        url.contains("192.168.") ||
        (16..31).any { url.contains("172.$it.") } ||
        url.contains("10.")

    /**
     * Mirrors the mobile URL preparation: bare hosts get `wss://`, except
     * `.onion` and private addresses which get plain `ws://`.
     */
    fun normalizeUserInput(url: String): NormalizedRelayUrl? {
        val trimmed = url.trim()
        if (trimmed.isBlank() || trimmed == "/") return null
        return if (!trimmed.startsWith("wss://") && !trimmed.startsWith("ws://")) {
            if (trimmed.endsWith(".onion") || trimmed.endsWith(".onion/") || isPrivateIp(trimmed)) {
                RelayUrlNormalizer.normalizeOrNull("ws://$trimmed")
            } else {
                RelayUrlNormalizer.normalizeOrNull("wss://$trimmed")
            }
        } else {
            RelayUrlNormalizer.normalizeOrNull(trimmed)
        }
    }

    /** Relays already trusted by the user: defaults plus every connected app's. */
    private fun savedRelays(): Set<NormalizedRelayUrl> {
        val saved = mutableSetOf<NormalizedRelayUrl>()
        saved += SettingsStore.settings.value.normalizedDefaultRelays()
        AccountsStore.accounts.value.forEach { record ->
            AmberDesktop.store(record.npub).apps.value.forEach { app ->
                saved += app.app.normalizedRelays()
            }
        }
        return saved
    }

    suspend fun check(relay: NormalizedRelayUrl): Outcome {
        // Already used somewhere -> it has proven itself; skip the round-trip.
        if (relay in savedRelays()) return Outcome.OK

        val client = AmberDesktop.newClient()
        val signer = NostrSignerInternal(KeyPair())
        val pubKeyHex = signer.keyPair.pubKey.toHexKey()
        val encrypted = signer.signerSync.nip04Encrypt("Test bunker event", pubKeyHex)
        val signedEvent = signer.signerSync.sign<Event>(
            TimeUtils.now(),
            NostrConnectEvent.KIND,
            arrayOf(arrayOf("p", pubKeyHex)),
            encrypted,
        )

        val subId = UUID.randomUUID().toString().substring(0, 4)
        var connected = false
        var filterResult = false

        val listener = object : RelayConnectionListener {
            override fun onConnected(relay: IRelayClient, pingMillis: Int, compressed: Boolean) {
                connected = true
                super.onConnected(relay, pingMillis, compressed)
            }

            override fun onIncomingMessage(relay: IRelayClient, msgStr: String, msg: Message) {
                if (msg is EventMessage && msg.subId == subId && msg.event.id == signedEvent.id) {
                    filterResult = true
                }
                super.onIncomingMessage(relay, msgStr, msg)
            }
        }

        client.addConnectionListener(listener)
        try {
            client.connect()
            client.subscribe(
                subId,
                mapOf(
                    relay to listOf(
                        Filter(
                            kinds = listOf(NostrConnectEvent.KIND),
                            tags = mapOf("p" to listOf(pubKeyHex)),
                        ),
                    ),
                ),
            )

            val canContinue = withTimeoutOrNull(30_000) {
                while (!connected) delay(200)
                true
            }
            if (canContinue == null) return Outcome.CANNOT_CONNECT

            var published = false
            var attempts = 0
            while (!published && attempts < 3) {
                published = client.publishAndConfirm(signedEvent, setOf(relay))
                if (!published) {
                    attempts++
                    delay(1_000)
                }
            }
            if (!published) return Outcome.FILTER_FAILED

            var count = 0
            while (!filterResult && count < 10) {
                delay(1_000)
                count++
            }
            return if (filterResult) Outcome.OK else Outcome.FILTER_FAILED
        } finally {
            runCatching { client.unsubscribe(subId) }
            runCatching { client.disconnect() }
            client.removeConnectionListener(listener)
        }
    }
}
