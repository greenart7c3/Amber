package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Full NIP-46 round-trip over a real public relay: the desktop engine plays
 * the signer role while this test plays the client app (connect via a
 * bunker:// URI, then get_public_key auto-approved by the granted
 * permissions). Network-dependent, so it only runs when AMBER_E2E is set.
 */
class BunkerE2eTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun isolateDataDir() {
            val tmp = File.createTempFile("amber-e2e", "").apply {
                delete()
                mkdirs()
                deleteOnExit()
            }
            System.setProperty("user.home", tmp.absolutePath)
        }
    }

    private class Client(relay: NormalizedRelayUrl, val keyPair: KeyPair) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val signer = NostrSignerInternal(keyPair)
        val responses = MutableStateFlow<List<BunkerResponse>>(emptyList())

        private val httpClient = OkHttpClient.Builder().pingInterval(10, TimeUnit.SECONDS).build()
        val client = NostrClient(
            object : WebsocketBuilder {
                override fun build(url: NormalizedRelayUrl, out: WebSocketListener) = BasicOkHttpWebSocket(url, { httpClient }, out)
            },
            scope,
        )

        init {
            client.addConnectionListener(
                object : RelayConnectionListener {
                    override fun onIncomingMessage(relay: IRelayClient, msgStr: String, msg: Message) {
                        if (msg is EventMessage && msg.event.kind == NostrConnectEvent.KIND) {
                            scope.launch {
                                runCatching {
                                    val decrypted = signer.decrypt(msg.event.content, msg.event.pubKey)
                                    val response = JacksonMapper.mapper.readValue(decrypted, BunkerResponse::class.java)
                                    responses.value = responses.value + response
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

        suspend fun send(signerPubKey: String, relay: NormalizedRelayUrl, requestJson: String): Boolean {
            val encrypted = signer.nip44Encrypt(requestJson, signerPubKey)
            val event = signer.signerSync.sign<com.vitorpamplona.quartz.nip01Core.core.Event>(
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
    fun bunkerConnectAndGetPublicKeyOverRelay() = runBlocking {
        assumeTrue("Set AMBER_E2E=1 to run the relay round-trip test", System.getenv("AMBER_E2E") != null)

        val relay = RelayUrlNormalizer.normalize("wss://nos.lol/")
        SettingsStore.update { it.copy(defaultRelays = listOf(relay.url)) }

        // Signer side.
        val account = AccountManager.addAccount(KeyPair(), name = "e2e")
        val engine = AmberDesktop.engine
        engine.start()
        val bunkerUri = engine.createBunkerConnection(account, "e2e-app", listOf(relay))
        val signerPubKey = bunkerUri.removePrefix("bunker://").substringBefore("?")
        val secret = bunkerUri.substringAfter("secret=")

        // Client side.
        val client = Client(relay, KeyPair())
        delay(3000) // let both subscriptions settle

        // 1. connect — requires the user's approval in the UI.
        val connectJson = """{"id":"e2e-connect","method":"connect","params":["$signerPubKey","$secret"]}"""
        assertEquals(true, client.send(signerPubKey, relay, connectJson))

        withTimeout(30_000) { engine.pending.first { it.isNotEmpty() } }
        val pendingRequest = engine.pending.value.first()
        assertEquals("e2e-connect", pendingRequest.request.id)
        engine.approve(pendingRequest, RememberType.ALWAYS)

        val ack = withTimeout(30_000) {
            client.responses.first { list -> list.any { it.id == "e2e-connect" } }
        }.first { it.id == "e2e-connect" }
        assertEquals("ack", ack.result)

        // 2. get_public_key — granted automatically on connect, no UI involved.
        val gpkJson = """{"id":"e2e-gpk","method":"get_public_key","params":[]}"""
        assertEquals(true, client.send(signerPubKey, relay, gpkJson))

        val gpk = withTimeout(30_000) {
            client.responses.first { list -> list.any { it.id == "e2e-gpk" } }
        }.first { it.id == "e2e-gpk" }
        assertEquals(account.hexKey, gpk.result)

        client.stop()
    }
}
