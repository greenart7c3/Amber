package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.LocalBunkerRelay
import com.greenart7c3.nostrsigner.desktop.core.LocalBunkerRelay.Status
import com.greenart7c3.nostrsigner.desktop.core.OkHttpWebSocket
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.io.File
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * The full NIP-46 bunker round trip over the local WebSocket relay: Amber's
 * own relay client stack (`NostrClient` + `OkHttpWebSocket`) and the bunker
 * engine against [LocalBunkerRelay], with this test playing the client app.
 * No public relay, no network.
 */
class BunkerOverLocalRelayTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun isolateDataDir() {
            val tmp = File.createTempFile("amber-local-e2e", "").apply {
                delete()
                mkdirs()
                deleteOnExit()
            }
            System.setProperty("user.home", tmp.absolutePath)
        }
    }

    /** The client app: same production wiring as `BunkerE2eTest`, pointed at the local relay only. */
    private class Client(
        relay: NormalizedRelayUrl,
        val keyPair: KeyPair,
    ) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val signer = NostrSignerInternal(keyPair)
        val responses = MutableStateFlow<List<BunkerResponse>>(emptyList())

        private val httpClient = OkHttpClient.Builder().pingInterval(10, TimeUnit.SECONDS).build()
        val client = NostrClient(
            object : WebsocketBuilder {
                override fun build(
                    url: NormalizedRelayUrl,
                    out: WebSocketListener,
                ) = OkHttpWebSocket(url, { httpClient }, out)
            },
            scope,
        )

        init {
            client.addConnectionListener(
                object : RelayConnectionListener {
                    override suspend fun onIncomingMessage(
                        relay: IRelayClient,
                        msgStr: String,
                        msg: Message,
                    ) {
                        if (msg is EventMessage && msg.event.kind == NostrConnectEvent.KIND) {
                            scope.launch {
                                runCatching {
                                    val decrypted = signer.decrypt(msg.event.content, msg.event.pubKey)
                                    responses.value += JacksonMapper.mapper.readValue(decrypted, BunkerResponse::class.java)
                                }
                            }
                        }
                    }
                },
            )
            client.subscribe(
                UUID.randomUUID().toString(),
                mapOf(
                    relay to listOf(
                        Filter(
                            kinds = listOf(NostrConnectEvent.KIND),
                            tags = mapOf("p" to listOf(signer.keyPair.pubKey.toHexKey())),
                            since = TimeUtils.now() - 5,
                        ),
                    ),
                ),
            )
            client.connect()
        }

        suspend fun send(
            signerPubKey: String,
            relay: NormalizedRelayUrl,
            requestJson: String,
        ): Boolean {
            val encrypted = signer.nip44Encrypt(requestJson, signerPubKey)
            val event = signer.signerSync.sign<Event>(
                TimeUtils.now(),
                NostrConnectEvent.KIND,
                arrayOf(arrayOf("p", signerPubKey)),
                encrypted,
            )
            return client.publishAndConfirm(event, setOf(relay), timeoutInSeconds = 10)
        }

        fun stop() {
            client.disconnect()
            scope.cancel()
        }
    }

    @Test
    fun bunkerRoundTripOverLocalRelay() = runBlocking {
        LocalBunkerRelay.stop()
        assertEquals(Status.RUNNING, LocalBunkerRelay.start(0))
        val relay = LocalBunkerRelay.relayUrl() ?: error("relay did not start")
        // Local-only defaults: the new bunker's URI then references the local relay alone.
        SettingsStore.update { it.copy(defaultRelays = listOf(relay.url), localBunkerServer = true) }

        val account = AccountManager.addAccount(KeyPair(), name = "local-relay-e2e")
        val engine = AmberDesktop.engine
        engine.start()
        val bunkerUri = engine.createBunkerConnection(account, "local-relay-e2e-app", emptyList())
        assertEquals(listOf("relay=${relay.url}"), bunkerUri.substringAfter("?").substringBefore("&secret=").split("&"))
        val signerPubKey = bunkerUri.removePrefix("bunker://").substringBefore("?")
        val secret = bunkerUri.substringAfter("secret=")

        val client = Client(relay, KeyPair())

        // 1. connect — arrives at the engine through its subscription on the local relay.
        assertTrue(
            client.send(signerPubKey, relay, """{"id":"c1","method":"connect","params":["$signerPubKey","$secret"]}"""),
        )
        withTimeout(30.seconds) { engine.pending.first { it.isNotEmpty() } }
        engine.approve(engine.pending.value.first(), RememberType.ALWAYS).join()
        val ack = withTimeout(30.seconds) {
            client.responses.first { list -> list.any { it.id == "c1" } }
        }.first { it.id == "c1" }
        assertEquals("ack", ack.result)

        // 2. get_public_key — granted automatically by the connect approval.
        assertTrue(client.send(signerPubKey, relay, """{"id":"g1","method":"get_public_key","params":[]}"""))
        val gpk = withTimeout(30.seconds) {
            client.responses.first { list -> list.any { it.id == "g1" } }
        }.first { it.id == "g1" }
        assertEquals(account.hexKey, gpk.result)

        client.stop()
        engine.updateFilter()
        AmberDesktop.client.disconnect()
        LocalBunkerRelay.stop()
    }

    @Test
    fun portChangeIsRefusedWhileAnAppUsesTheRelay() = runBlocking {
        // Configure the port first, exactly as the settings UI does: the guard
        // compares saved connections against the configured relay URL.
        val probe = ServerSocket(0)
        val port1 = probe.localPort
        probe.close()
        LocalBunkerRelay.stop()
        SettingsStore.update { it.copy(defaultRelays = emptyList(), localBunkerServer = true, localBunkerPort = port1) }
        assertEquals(Status.RUNNING, LocalBunkerRelay.start(AmberDesktop.localBunkerPort()))
        val relay = LocalBunkerRelay.relayUrl() ?: error("relay did not start")

        val account = AccountManager.addAccount(KeyPair(), name = "port-guard")
        val engine = AmberDesktop.engine
        engine.start()
        engine.createBunkerConnection(account, "port-guard-app", emptyList())

        val probe2 = ServerSocket(0)
        val port2 = probe2.localPort
        probe2.close()

        // A saved connection still lists the relay: the port must not move.
        assertEquals("d_local_bunker_port_in_use", AmberDesktop.changeLocalBunkerPort(port2))
        assertEquals(port1, LocalBunkerRelay.boundPort)
        assertEquals(relay.url, LocalBunkerRelay.relayUrl()?.url)

        val app = AmberDesktop.store(account.npub).apps.value.first()
        AmberDesktop.store(account.npub).delete(app.app.key)
        // Freed, the port change restarts the relay on the new port.
        assertNull(AmberDesktop.changeLocalBunkerPort(port2))
        assertEquals(port2, LocalBunkerRelay.boundPort)
        assertEquals("ws://127.0.0.1:$port2/", LocalBunkerRelay.relayUrl()?.url)

        engine.updateFilter()
        AmberDesktop.client.disconnect()
        LocalBunkerRelay.stop()
    }
}
