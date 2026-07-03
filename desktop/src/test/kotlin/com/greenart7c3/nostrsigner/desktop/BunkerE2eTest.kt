package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
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
import org.junit.Assert.assertTrue
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

        /** pubkey of the sender of the last connect response (the signer's per-connection key). */
        val signerPubKey = MutableStateFlow<String?>(null)

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
                                    signerPubKey.value = msg.event.pubKey
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

    /**
     * Two logged-in accounts, each with its own bunker connection on the same
     * relay. A request to account B's connection must be answered with B's key,
     * and A's with A's — proving the multi-account subscription/routing works.
     */
    @Test
    fun twoAccountsRouteToTheCorrectKey() = runBlocking {
        assumeTrue("Set AMBER_E2E=1 to run the relay round-trip test", System.getenv("AMBER_E2E") != null)

        val relay = RelayUrlNormalizer.normalize("wss://nos.lol/")
        SettingsStore.update { it.copy(defaultRelays = listOf(relay.url)) }

        val accountA = AccountManager.addAccount(KeyPair(), name = "A")
        val accountB = AccountManager.addAccount(KeyPair(), name = "B")
        assertTrue(accountA.hexKey != accountB.hexKey)

        val engine = AmberDesktop.engine
        engine.start()

        val bunkerA = engine.createBunkerConnection(accountA, "app-A", listOf(relay))
        val bunkerB = engine.createBunkerConnection(accountB, "app-B", listOf(relay))
        val signerA = bunkerA.removePrefix("bunker://").substringBefore("?")
        val signerB = bunkerB.removePrefix("bunker://").substringBefore("?")
        val secretA = bunkerA.substringAfter("secret=")
        val secretB = bunkerB.substringAfter("secret=")
        assertTrue(signerA != signerB)

        val clientA = Client(relay, KeyPair())
        val clientB = Client(relay, KeyPair())
        delay(4000)

        // Connect + approve both.
        clientB.send(signerB, relay, """{"id":"cB","method":"connect","params":["$signerB","$secretB"]}""")
        clientA.send(signerA, relay, """{"id":"cA","method":"connect","params":["$signerA","$secretA"]}""")

        withTimeout(30_000) { engine.pending.first { it.size >= 2 } }
        engine.pending.value.toList().forEach { engine.approve(it, RememberType.ALWAYS) }

        withTimeout(30_000) { clientA.responses.first { l -> l.any { it.id == "cA" } } }
        withTimeout(30_000) { clientB.responses.first { l -> l.any { it.id == "cB" } } }

        // get_public_key on each connection returns that connection's account key.
        clientA.send(signerA, relay, """{"id":"gA","method":"get_public_key","params":[]}""")
        clientB.send(signerB, relay, """{"id":"gB","method":"get_public_key","params":[]}""")

        val gA = withTimeout(30_000) { clientA.responses.first { l -> l.any { it.id == "gA" } } }.first { it.id == "gA" }
        val gB = withTimeout(30_000) { clientB.responses.first { l -> l.any { it.id == "gB" } } }.first { it.id == "gB" }

        assertEquals(accountA.hexKey, gA.result)
        assertEquals(accountB.hexKey, gB.result)

        clientA.stop()
        clientB.stop()
    }

    /**
     * The nostrconnect:// flow real web apps use: the client publishes a URI,
     * Amber imports it, the user approves, Amber sends the connect ack from a
     * fresh per-connection key, and the client can then issue get_public_key
     * to that key.
     */
    @Test
    fun nostrConnectFlowOverRelay() = runBlocking {
        assumeTrue("Set AMBER_E2E=1 to run the relay round-trip test", System.getenv("AMBER_E2E") != null)

        val relay = RelayUrlNormalizer.normalize("wss://nos.lol/")
        SettingsStore.update { it.copy(defaultRelays = listOf(relay.url)) }

        val account = AccountManager.addAccount(KeyPair(), name = "nc")
        val engine = AmberDesktop.engine
        engine.start()

        val client = Client(relay, KeyPair())
        delay(2000)

        val clientPubKey = client.keyPair.pubKey.toHexKey()
        val secret = "nc-secret-123"
        val uri = "nostrconnect://$clientPubKey?relay=${relay.url}&secret=$secret&perms=sign_event:1,get_public_key&name=WebApp"

        // Amber imports the URI -> a pending CONNECT request appears.
        val error = engine.addNostrConnect(uri, account)
        assertEquals(null, error)
        withTimeout(10_000) { engine.pending.first { it.isNotEmpty() } }
        // Simulate the approval UI, which passes the requested permissions so
        // the granted perms (sign_event:1, get_public_key) are remembered.
        val connectReq = engine.pending.value.first()
        engine.approve(connectReq, RememberType.ALWAYS, connectReq.requestedPermissions)

        // The client receives the connect ack (result == secret) from the signer key.
        val ack = withTimeout(30_000) {
            client.responses.first { l -> l.any { it.result == secret } }
        }.first { it.result == secret }
        assertEquals(secret, ack.result)

        val signerKey = withTimeout(5_000) { client.signerPubKey.first { it != null } }!!

        // Follow-up request goes to the signer's per-connection key.
        client.send(signerKey, relay, """{"id":"nc-gpk","method":"get_public_key","params":[]}""")
        val gpk = withTimeout(30_000) {
            client.responses.first { l -> l.any { it.id == "nc-gpk" } }
        }.first { it.id == "nc-gpk" }
        assertEquals(account.hexKey, gpk.result)

        // sign_event kind 1 was granted in the connect perms, so it must be
        // signed automatically without landing in the approval queue.
        val eventJson = """{"kind":1,"content":"hello from a bunker","tags":[],"created_at":${TimeUtils.now()},"pubkey":"${account.hexKey}"}"""
        val escaped = eventJson.replace("\\", "\\\\").replace("\"", "\\\"")
        client.send(signerKey, relay, """{"id":"nc-sign","method":"sign_event","params":["$escaped"]}""")
        val signResp = withTimeout(30_000) {
            client.responses.first { l -> l.any { it.id == "nc-sign" } }
        }.first { it.id == "nc-sign" }
        assertTrue("sign_event should not have been rejected: ${signResp.error}", signResp.error.isNullOrEmpty())
        val signed = Event.fromJson(signResp.result!!)
        assertEquals(account.hexKey, signed.pubKey)
        assertEquals(1, signed.kind)
        assertTrue("signature must verify", signed.verify())

        client.stop()
    }
}
