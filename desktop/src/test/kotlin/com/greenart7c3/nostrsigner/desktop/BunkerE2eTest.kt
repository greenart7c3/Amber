package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.EncryptedContentType
import com.greenart7c3.nostrsigner.desktop.core.EncryptionScope
import com.greenart7c3.nostrsigner.desktop.core.OkHttpWebSocket
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import com.greenart7c3.nostrsigner.desktop.core.addNostrConnect
import com.greenart7c3.nostrsigner.desktop.core.approve
import com.greenart7c3.nostrsigner.desktop.core.createBunkerConnection
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
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
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
        private val SUBSCRIPTIONS_SETTLE = 3.seconds
        private const val MANUAL_SIGN_POLICY = 1

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
        val lastResponderConnectionKey = MutableStateFlow<String?>(null)

        private val httpClient = OkHttpClient.Builder().pingInterval(10, TimeUnit.SECONDS).build()
        val client = NostrClient(
            object : WebsocketBuilder {
                override fun build(url: NormalizedRelayUrl, out: WebSocketListener) = OkHttpWebSocket(url, { httpClient }, out)
            },
            scope,
        )

        init {
            client.addConnectionListener(
                object : RelayConnectionListener {
                    override suspend fun onIncomingMessage(relay: IRelayClient, msgStr: String, msg: Message) {
                        if (msg is EventMessage && msg.event.kind == NostrConnectEvent.KIND) {
                            scope.launch {
                                runCatching {
                                    val decrypted = signer.decrypt(msg.event.content, msg.event.pubKey)
                                    val response = JacksonMapper.mapper.readValue(decrypted, BunkerResponse::class.java)
                                    lastResponderConnectionKey.value = msg.event.pubKey
                                    responses.value += response
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
    fun bunkerConnectAndGetPublicKeyOverRelay() = runBlocking {
        assumeTrue("Set AMBER_E2E=1 to run the relay round-trip test", System.getenv("AMBER_E2E") != null)

        val relay = RelayUrlNormalizer.normalize("wss://nos.lol/")
        SettingsStore.update { it.copy(defaultRelays = listOf(relay.url)) }

        val account = AccountManager.addAccount(KeyPair(), name = "e2e")
        val engine = AmberDesktop.engine
        engine.start()
        val bunkerUri = engine.createBunkerConnection(account, "e2e-app", listOf(relay))
        val signerPubKey = bunkerUri.removePrefix("bunker://").substringBefore("?")
        val secret = bunkerUri.substringAfter("secret=")

        val client = Client(relay, KeyPair())
        delay(SUBSCRIPTIONS_SETTLE)

        val connectJson = """{"id":"e2e-connect","method":"connect","params":["$signerPubKey","$secret"]}"""
        assertEquals(true, client.send(signerPubKey, relay, connectJson))

        withTimeout(30.seconds) { engine.pending.first { it.isNotEmpty() } }
        val pendingRequest = engine.pending.value.first()
        assertEquals("connect waits for the user's approval", "e2e-connect", pendingRequest.request.id)
        engine.approve(pendingRequest, RememberType.ALWAYS).join()

        val ack = withTimeout(30.seconds) {
            client.responses.first { list -> list.any { it.id == "e2e-connect" } }
        }.first { it.id == "e2e-connect" }
        assertEquals("ack", ack.result)

        val gpkJson = """{"id":"e2e-gpk","method":"get_public_key","params":[]}"""
        assertEquals(true, client.send(signerPubKey, relay, gpkJson))

        val gpk = withTimeout(30.seconds) {
            client.responses.first { list -> list.any { it.id == "e2e-gpk" } }
        }.first { it.id == "e2e-gpk" }
        assertEquals("get_public_key is granted on connect, without a prompt", account.hexKey, gpk.result)

        client.stop()
    }

    /**
     * Regression: approving must publish the response even if the coroutine
     * that triggered it is canceled immediately afterward — which is exactly
     * what happens in the UI, where approving removes the request card from the
     * list and tears down its composition scope. The engine runs the work on
     * its own application scope, so a canceled caller must not stop delivery.
     */
    @Test
    fun approvalSurvivesCallerCancellation() = runBlocking {
        assumeTrue("Set AMBER_E2E=1 to run the relay round-trip test", System.getenv("AMBER_E2E") != null)

        val relay = RelayUrlNormalizer.normalize("wss://nos.lol/")
        SettingsStore.update { it.copy(defaultRelays = listOf(relay.url)) }

        val account = AccountManager.addAccount(KeyPair(), name = "cancel")
        val engine = AmberDesktop.engine
        engine.start()
        val bunkerUri = engine.createBunkerConnection(account, "cancel-app", listOf(relay))
        val signerPubKey = bunkerUri.removePrefix("bunker://").substringBefore("?")
        val secret = bunkerUri.substringAfter("secret=")

        val client = Client(relay, KeyPair())
        delay(3.seconds)

        client.send(signerPubKey, relay, """{"id":"cc","method":"connect","params":["$signerPubKey","$secret"]}""")
        withTimeout(30.seconds) { engine.pending.first { it.isNotEmpty() } }
        val req = engine.pending.value.first()

        approveFromAScopeTornDownRightAway(req)

        val ack = withTimeout(30.seconds) {
            client.responses.first { l -> l.any { it.id == "cc" } }
        }.first { it.id == "cc" }
        assertEquals("the ack arrives although the approving caller was canceled", "ack", ack.result)

        client.stop()
    }

    private suspend fun approveFromAScopeTornDownRightAway(request: PendingBunkerRequest) {
        val removedCardScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        removedCardScope.launch { AmberDesktop.engine.approve(request, RememberType.ALWAYS) }
        delay(50.milliseconds)
        removedCardScope.cancel()
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
        delay(4.seconds)

        clientB.send(signerB, relay, """{"id":"cB","method":"connect","params":["$signerB","$secretB"]}""")
        clientA.send(signerA, relay, """{"id":"cA","method":"connect","params":["$signerA","$secretA"]}""")

        withTimeout(30.seconds) { engine.pending.first { it.size >= 2 } }
        engine.pending.value.toList().map { engine.approve(it, RememberType.ALWAYS) }.joinAll()

        withTimeout(30.seconds) { clientA.responses.first { l -> l.any { it.id == "cA" } } }
        withTimeout(30.seconds) { clientB.responses.first { l -> l.any { it.id == "cB" } } }

        clientA.send(signerA, relay, """{"id":"gA","method":"get_public_key","params":[]}""")
        clientB.send(signerB, relay, """{"id":"gB","method":"get_public_key","params":[]}""")

        val gA = withTimeout(30.seconds) { clientA.responses.first { l -> l.any { it.id == "gA" } } }.first { it.id == "gA" }
        val gB = withTimeout(30.seconds) { clientB.responses.first { l -> l.any { it.id == "gB" } } }.first { it.id == "gB" }

        assertEquals("connection A answers with account A's key", accountA.hexKey, gA.result)
        assertEquals("connection B answers with account B's key", accountB.hexKey, gB.result)

        clientA.stop()
        clientB.stop()
    }

    /**
     * Switching the account on the connect card: a bunker:// connection
     * created under account A but approved for account B moves to B, keeps
     * its connection key, and from then on signs as B.
     */
    @Test
    fun connectCanBeApprovedForAnotherAccount() = runBlocking {
        assumeTrue("Set AMBER_E2E=1 to run the relay round-trip test", System.getenv("AMBER_E2E") != null)

        val relay = RelayUrlNormalizer.normalize("wss://nos.lol/")
        SettingsStore.update { it.copy(defaultRelays = listOf(relay.url)) }

        val accountA = AccountManager.addAccount(KeyPair(), name = "switch-A")
        val accountB = AccountManager.addAccount(KeyPair(), name = "switch-B")

        val engine = AmberDesktop.engine
        engine.start()
        val bunkerUri = engine.createBunkerConnection(accountA, "switch-app", listOf(relay))
        val signerPubKey = bunkerUri.removePrefix("bunker://").substringBefore("?")
        val secret = bunkerUri.substringAfter("secret=")

        val client = Client(relay, KeyPair())
        delay(3.seconds)

        client.send(signerPubKey, relay, """{"id":"sw-connect","method":"connect","params":["$signerPubKey","$secret"]}""")
        withTimeout(30.seconds) { engine.pending.first { list -> list.any { it.request.id == "sw-connect" } } }
        val req = engine.pending.value.first { it.request.id == "sw-connect" }
        assertTrue(req.canSwitchAccount)
        engine.approve(req, RememberType.ALWAYS, accountNpub = accountB.npub).join()

        val ack = withTimeout(30.seconds) { client.responses.first { l -> l.any { it.id == "sw-connect" } } }.first { it.id == "sw-connect" }
        assertEquals("ack", ack.result)
        assertTrue(AmberDesktop.store(accountA.npub).apps.value.none { it.app.name == "switch-app" })
        assertTrue(AmberDesktop.store(accountB.npub).apps.value.any { it.app.name == "switch-app" && it.app.pubKey == accountB.hexKey })

        client.send(signerPubKey, relay, """{"id":"sw-gpk","method":"get_public_key","params":[]}""")
        val gpk = withTimeout(30.seconds) { client.responses.first { l -> l.any { it.id == "sw-gpk" } } }.first { it.id == "sw-gpk" }
        assertEquals(accountB.hexKey, gpk.result)

        client.stop()
    }

    /**
     * Encrypt grants are per content type, like Android: approving a text
     * encryption "for this method only" auto-approves the next text, but a
     * tag array still needs approval; "all methods" then covers it too.
     */
    @Test
    fun encryptPermissionsAreScopedByContentType() = runBlocking {
        assumeTrue("Set AMBER_E2E=1 to run the relay round-trip test", System.getenv("AMBER_E2E") != null)

        val relay = RelayUrlNormalizer.normalize("wss://nos.lol/")
        SettingsStore.update { it.copy(defaultRelays = listOf(relay.url)) }

        val account = AccountManager.addAccount(KeyPair(), name = "scope")
        val engine = AmberDesktop.engine
        engine.start()
        val bunkerUri = engine.createBunkerConnection(account, "scope-app", listOf(relay))
        val signerPubKey = bunkerUri.removePrefix("bunker://").substringBefore("?")
        val secret = bunkerUri.substringAfter("secret=")
        val client = Client(relay, KeyPair())
        val peer = KeyPair().pubKey.toHexKey()
        delay(3.seconds)

        suspend fun request(json: String) {
            client.send(signerPubKey, relay, json)
        }
        suspend fun pendingFor(id: String) = withTimeout(30.seconds) {
            engine.pending.first { list -> list.any { it.request.id == id } }
        }.first { it.request.id == id }
        suspend fun responseFor(id: String) = withTimeout(30.seconds) {
            client.responses.first { l -> l.any { it.id == id } }
        }.first { it.id == id }

        request("""{"id":"sc-connect","method":"connect","params":["$signerPubKey","$secret"]}""")
        engine.approve(pendingFor("sc-connect"), RememberType.ALWAYS, signPolicy = MANUAL_SIGN_POLICY).join()
        responseFor("sc-connect")

        request("""{"id":"sc-t1","method":"nip44_encrypt","params":["$peer","hello"]}""")
        val t1 = pendingFor("sc-t1")
        assertEquals(EncryptedContentType.CLEAR_TEXT, t1.encryptedContent?.type)
        engine.approve(t1, RememberType.ALWAYS, encryptionScope = EncryptionScope.SPECIFIC).join()
        responseFor("sc-t1")
        val stored = AmberDesktop.store(account.npub).apps.value.first { it.app.name == "scope-app" }.permissions.map { it.type }
        assertTrue("remembered for clear text only: $stored", "ENCRYPT_CLEAR_TEXT" in stored && "NIP44_ENCRYPT" !in stored)

        request("""{"id":"sc-t2","method":"nip44_encrypt","params":["$peer","again"]}""")
        assertTrue("another text is answered without prompting", responseFor("sc-t2").result!!.isNotEmpty())

        request("""{"id":"sc-tags","method":"nip44_encrypt","params":["$peer","[[\"p\",\"$peer\"]]"]}""")
        val tags = pendingFor("sc-tags")
        assertEquals("a tag array is another content type, so it prompts", EncryptedContentType.TAG_ARRAY, tags.encryptedContent?.type)
        engine.approve(tags, RememberType.ALWAYS, encryptionScope = EncryptionScope.ALL).join()
        responseFor("sc-tags")

        request("""{"id":"sc-ev","method":"nip44_encrypt","params":["$peer","{\"kind\":1,\"content\":\"x\",\"tags\":[]}"]}""")
        assertTrue("the whole-NIP grant covers an event too", responseFor("sc-ev").result!!.isNotEmpty())

        client.stop()
    }

    /**
     * NIP-44 v3 grants are per context kind, like Android: "this kind only"
     * covers the next request of that kind but not another kind; "all kinds"
     * replaces them with a kind-less grant. A request without a kind is
     * rejected without prompting.
     */
    @Test
    fun nip44v3PermissionsAreScopedByKind() = runBlocking {
        assumeTrue("Set AMBER_E2E=1 to run the relay round-trip test", System.getenv("AMBER_E2E") != null)

        val relay = RelayUrlNormalizer.normalize("wss://nos.lol/")
        SettingsStore.update { it.copy(defaultRelays = listOf(relay.url)) }

        val account = AccountManager.addAccount(KeyPair(), name = "v3")
        val engine = AmberDesktop.engine
        engine.start()
        val bunkerUri = engine.createBunkerConnection(account, "v3-app", listOf(relay))
        val signerPubKey = bunkerUri.removePrefix("bunker://").substringBefore("?")
        val secret = bunkerUri.substringAfter("secret=")
        val client = Client(relay, KeyPair())
        val peer = KeyPair().pubKey.toHexKey()
        val plain = java.util.Base64.getEncoder().encodeToString("hi".toByteArray())
        delay(3.seconds)

        suspend fun request(json: String) {
            client.send(signerPubKey, relay, json)
        }
        suspend fun pendingFor(id: String) = withTimeout(30.seconds) {
            engine.pending.first { list -> list.any { it.request.id == id } }
        }.first { it.request.id == id }
        suspend fun responseFor(id: String) = withTimeout(30.seconds) {
            client.responses.first { l -> l.any { it.id == id } }
        }.first { it.id == id }
        fun v3Perms() = AmberDesktop.store(account.npub).apps.value.first { it.app.name == "v3-app" }
            .permissions.filter { it.type == "NIP44_V3_ENCRYPT" }.map { it.kind }

        request("""{"id":"v3-connect","method":"connect","params":["$signerPubKey","$secret"]}""")
        engine.approve(pendingFor("v3-connect"), RememberType.ALWAYS, signPolicy = MANUAL_SIGN_POLICY).join()
        responseFor("v3-connect")

        request("""{"id":"v3-a","method":"nip44v3_encrypt","params":["$peer","1","chat","$plain"]}""")
        val a = pendingFor("v3-a")
        assertEquals(1, a.kind)
        assertEquals("chat", a.nip44v3Scope)
        engine.approve(a, RememberType.ALWAYS, encryptionScope = EncryptionScope.SPECIFIC).join()
        responseFor("v3-a")
        assertEquals("this kind only remembers kind 1", listOf<Int?>(1), v3Perms())

        request("""{"id":"v3-b","method":"nip44v3_encrypt","params":["$peer","1","chat","$plain"]}""")
        assertTrue("the same kind is answered without prompting", responseFor("v3-b").result!!.isNotEmpty())

        request("""{"id":"v3-c","method":"nip44v3_encrypt","params":["$peer","7","","$plain"]}""")
        val anotherKindPrompt = pendingFor("v3-c")
        engine.approve(anotherKindPrompt, RememberType.ALWAYS, encryptionScope = EncryptionScope.ALL).join()
        responseFor("v3-c")
        assertEquals("all kinds replaces the per-kind grant with a kind-less one", listOf<Int?>(null), v3Perms())

        request("""{"id":"v3-d","method":"nip44v3_encrypt","params":["$peer","30023","","$plain"]}""")
        assertTrue("any kind is covered by the kind-less grant", responseFor("v3-d").result!!.isNotEmpty())

        request("""{"id":"v3-e","method":"nip44v3_encrypt","params":["$peer","","","$plain"]}""")
        assertEquals("a request without a kind is rejected up front", "kind is required for nip44v3", responseFor("v3-e").error)

        client.stop()
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
        delay(2.seconds)

        val clientPubKey = client.keyPair.pubKey.toHexKey()
        val secret = "nc-secret-123"
        val uri = "nostrconnect://$clientPubKey?relay=${relay.url}&secret=$secret&perms=sign_event:1,get_public_key&name=WebApp"

        val error = engine.addNostrConnect(uri, account)
        assertEquals("importing the URI queues a connect request", null, error)
        withTimeout(10.seconds) { engine.pending.first { it.isNotEmpty() } }
        val connectReq = engine.pending.value.first()
        val requestedSignEventAndGetPublicKey = connectReq.requestedPermissions
        engine.approve(connectReq, RememberType.ALWAYS, requestedSignEventAndGetPublicKey).join()

        val ack = withTimeout(30.seconds) {
            client.responses.first { l -> l.any { it.result == secret } }
        }.first { it.result == secret }
        assertEquals("the connect ack carries the secret", secret, ack.result)

        val connectionKey = withTimeout(5.seconds) { client.lastResponderConnectionKey.first { it != null } }!!

        client.send(connectionKey, relay, """{"id":"nc-gpk","method":"get_public_key","params":[]}""")
        val gpk = withTimeout(30.seconds) {
            client.responses.first { l -> l.any { it.id == "nc-gpk" } }
        }.first { it.id == "nc-gpk" }
        assertEquals("the per-connection key answers for the account", account.hexKey, gpk.result)

        val eventJson = """{"kind":1,"content":"hello from a bunker","tags":[],"created_at":${TimeUtils.now()},"pubkey":"${account.hexKey}"}"""
        val escaped = eventJson.replace("\\", "\\\\").replace("\"", "\\\"")
        client.send(connectionKey, relay, """{"id":"nc-sign","method":"sign_event","params":["$escaped"]}""")
        val signResp = withTimeout(30.seconds) {
            client.responses.first { l -> l.any { it.id == "nc-sign" } }
        }.first { it.id == "nc-sign" }
        assertTrue("sign_event kind 1, granted in the connect perms, is signed without a prompt: ${signResp.error}", signResp.error.isNullOrEmpty())
        val signed = Event.fromJson(signResp.result!!)
        assertEquals(account.hexKey, signed.pubKey)
        assertEquals(1, signed.kind)
        assertTrue("signature must verify", signed.verify())

        client.stop()
    }
}
