package com.greenart7c3.nostrsigner.desktop

import com.fasterxml.jackson.databind.node.ObjectNode
import com.greenart7c3.nostrsigner.common.generateBunkerPrivKey
import com.greenart7c3.nostrsigner.common.localPubKeyFromPrivKey
import com.greenart7c3.nostrsigner.common.relays.LocalRelayServer.Status
import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.AppRecord
import com.greenart7c3.nostrsigner.desktop.core.AppWithPermissions
import com.greenart7c3.nostrsigner.desktop.core.LocalBunkerRelay
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.TimeUtils
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The local bunker relay's NIP-01 subset over real WebSockets on the loopback
 * interface: publish/ack, subscription replay, live fan-out, filter matching,
 * tampered events, malformed commands, close handling and shutdown.
 */
class LocalBunkerRelayTest {
    private val http = OkHttpClient()
    private var port = 0

    private val url: String get() = "ws://127.0.0.1:$port/"

    @Before
    fun startRelay() {
        LocalBunkerRelay.stop()
        assertEquals(Status.RUNNING, LocalBunkerRelay.start(0))
        port = LocalBunkerRelay.boundPort
        assertTrue(port > 0)
        assertNotNull(LocalBunkerRelay.relayUrl())
    }

    @After
    fun stopRelay() {
        LocalBunkerRelay.stop()
        assertNull(LocalBunkerRelay.relayUrl())
    }

    private class WsClient(
        http: OkHttpClient,
        url: String,
    ) : AutoCloseable {
        private val messages = LinkedBlockingQueue<String>()
        private val closed = CountDownLatch(1)
        val socket: WebSocket =
            http.newWebSocket(
                Request.Builder().url(url).build(),
                object : WebSocketListener() {
                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        messages.add(text)
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        closed.countDown()
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        closed.countDown()
                    }
                },
            )

        /** The next relay message, failing the test instead of hanging when the relay goes quiet. */
        fun next(timeoutMillis: Long = 5_000): String = messages.poll(timeoutMillis, TimeUnit.MILLISECONDS) ?: error("no relay message within ${timeoutMillis}ms")

        /** Asserts nothing else arrives: filters must not leak events to non-matching subscribers. */
        fun silence(timeoutMillis: Long = 400) {
            assertNull(messages.poll(timeoutMillis, TimeUnit.MILLISECONDS))
        }

        fun send(text: String): Boolean = socket.send(text)

        fun awaitClose() {
            assertTrue("relay never closed the session", closed.await(5, TimeUnit.SECONDS))
        }

        override fun close() {
            socket.close(1000, "done")
        }
    }

    private fun req(
        subId: String,
        filter: String,
    ): String = "[\"REQ\",\"$subId\",$filter]"

    private fun eventMessage(event: Event): String = "[\"EVENT\",${event.toJson()}]"

    private fun signedEvent(
        kind: Int = NOSTR_CONNECT_TEST_KIND,
        content: String = "local bunker relay test",
        tags: Array<Array<String>> = emptyArray(),
    ): Event = NostrSignerInternal(KeyPair()).signerSync.sign<Event>(TimeUtils.now(), kind, tags, content)

    private fun parse(text: String) = JacksonMapper.mapper.readTree(text)

    @Test
    fun publishIsAckedAndReplayedToLaterSubscribers() {
        val publisher = WsClient(http, url)
        val event = signedEvent()
        assertTrue(publisher.send(eventMessage(event)))
        val ack = parse(publisher.next())
        assertEquals("OK", ack.get(0).asText())
        assertEquals(event.id, ack.get(1).asText())
        assertTrue(ack.get(2).asBoolean())

        val lateSubscriber = WsClient(http, url)
        lateSubscriber.send(req("replay", """{"kinds":[${event.kind}],"authors":["${event.pubKey}"]}"""))
        val delivered = parse(lateSubscriber.next())
        assertEquals("EVENT", delivered.get(0).asText())
        assertEquals("replay", delivered.get(1).asText())
        assertEquals(event.id, delivered.get(2).get("id").asText())
        assertEquals("EOSE", parse(lateSubscriber.next()).get(0).asText())
    }

    @Test
    fun liveEventGoesOnlyToMatchingSubscriptions() {
        val subscriber = WsClient(http, url)
        val other = WsClient(http, url)
        val target = NostrSignerInternal(KeyPair()).keyPair.pubKey.toHexKey()
        subscriber.send(req("live", """{"kinds":[$NOSTR_CONNECT_TEST_KIND],"#p":["$target"]}"""))
        assertEquals("EOSE", parse(subscriber.next()).get(0).asText())
        other.send(req("other", """{"kinds":[$NOSTR_CONNECT_TEST_KIND],"#p":["${"a".repeat(64)}"]}"""))
        assertEquals("EOSE", parse(other.next()).get(0).asText())

        val event = signedEvent(tags = arrayOf(arrayOf("p", target)))
        assertTrue(subscriber.send(eventMessage(event)))
        // The publisher is subscribed itself, so it gets its OK ack first.
        assertEquals(event.id, parse(subscriber.next()).get(1).asText())
        val delivered = parse(subscriber.next())
        assertEquals("EVENT", delivered.get(0).asText())
        assertEquals("live", delivered.get(1).asText())
        assertEquals(event.id, delivered.get(2).get("id").asText())
        // The event does not match the other subscription's p tag.
        other.silence()
    }

    @Test
    fun limitKeepsTheLatestEvents() {
        val publisher = WsClient(http, url)
        val events = List(3) { signedEvent(content = "counting $it") }
        events.forEach { event -> assertTrue(publisher.send(eventMessage(event))) }
        // Consume the acks so nothing queues up behind the subscription replay.
        repeat(events.size) { assertEquals("OK", parse(publisher.next()).get(0).asText()) }

        val subscriber = WsClient(http, url)
        subscriber.send(req("last2", """{"kinds":[$NOSTR_CONNECT_TEST_KIND],"limit":2}"""))
        val first = parse(subscriber.next())
        val second = parse(subscriber.next())
        assertEquals(events[1].id, first.get(2).get("id").asText())
        assertEquals(events[2].id, second.get(2).get("id").asText())
        assertEquals("EOSE", parse(subscriber.next()).get(0).asText())
    }

    @Test
    fun duplicateEventIsAckedButDeliveredOnce() {
        val publisher = WsClient(http, url)
        val subscriber = WsClient(http, url)
        subscriber.send(req("dup", """{"kinds":[$NOSTR_CONNECT_TEST_KIND]}"""))
        assertEquals("EOSE", parse(subscriber.next()).get(0).asText())
        val event = signedEvent()
        val raw = eventMessage(event)
        assertTrue(publisher.send(raw))
        assertTrue(publisher.send(raw))
        assertEquals(event.id, parse(publisher.next()).get(1).asText())
        assertEquals(event.id, parse(publisher.next()).get(1).asText())
        assertEquals(event.id, parse(subscriber.next()).get(2).get("id").asText())
        subscriber.silence()
    }

    @Test
    fun tamperedEventIsRejected() {
        val client = WsClient(http, url)
        val node = parse(eventMessage(signedEvent())).get(1) as ObjectNode
        // A signature that is no longer valid for the event's id.
        node.put("sig", "0".repeat(128))
        assertTrue(client.send(JacksonMapper.mapper.writeValueAsString(listOf("EVENT", node))))
        val ack = parse(client.next())
        assertEquals("OK", ack.get(0).asText())
        assertFalse(ack.get(2).asBoolean())
        assertTrue(ack.get(3).asText().isNotBlank())
    }

    @Test
    fun malformedCommandsAreAnsweredWithNotice() {
        val client = WsClient(http, url)
        assertTrue(client.send("this is not json"))
        assertEquals("NOTICE", parse(client.next()).get(0).asText())
        assertTrue(client.send("""["NOT_A_COMMAND"]"""))
        assertEquals("NOTICE", parse(client.next()).get(0).asText())
        // The session stays usable afterwards.
        val event = signedEvent()
        assertTrue(client.send(eventMessage(event)))
        assertEquals(event.id, parse(client.next()).get(1).asText())
    }

    @Test
    fun unapprovedEventFloodIsRateLimited() {
        val client = WsClient(http, url)
        val signer = NostrSignerInternal(KeyPair())
        repeat(12) { i ->
            val event = signer.signerSync.sign<Event>(TimeUtils.now(), NOSTR_CONNECT_TEST_KIND, emptyArray(), "flood $i")
            assertTrue(client.send(eventMessage(event)))
        }
        val acks = (0 until 12).map { parse(client.next()) }
        assertEquals(10, acks.count { it.get(2).asBoolean() })
        val rejected = acks.filter { !it.get(2).asBoolean() }
        assertEquals(2, rejected.size)
        assertTrue(rejected.all { it.get(3).asText().contains("rate limited") })
    }

    @Test
    fun reqFloodIsRateLimited() {
        val client = WsClient(http, url)
        repeat(65) { i -> client.send(req("s$i", """{"kinds":[1]}""")) }
        var eose = 0
        var tooMany = 0
        var limited = 0
        repeat(65) {
            val msg = parse(client.next())
            when {
                msg.get(0).asText() == "EOSE" -> eose++
                msg.get(1).asText().contains("too many subscriptions") -> tooMany++
                msg.get(1).asText().contains("rate limited") -> limited++
                else -> {}
            }
        }
        // The subscription cap (20 per client) binds first; the rate limiter
        // catches the rest of the flood past 60 REQs per minute.
        assertEquals(20, eose)
        assertEquals(40, tooMany)
        assertEquals(5, limited)
    }

    @Test
    fun malformedFloodClosesTheConnection() {
        val client = WsClient(http, url)
        repeat(65) { client.send("garbage $it") }
        // The first 60 stay within the per-connection budget; the 61st is told
        // "rate limited" and the session is dropped right after it.
        var notices = 0
        var limited = 0
        repeat(61) {
            val msg = parse(client.next())
            if (msg.get(1).asText().contains("rate limited")) {
                limited++
            } else {
                notices++
            }
        }
        assertEquals(60, notices)
        assertEquals(1, limited)
        client.awaitClose()
    }

    @Test
    fun approvedConnectionsAreNeverRateLimited() = runBlocking {
        val account = AccountManager.addAccount(KeyPair(), name = "relay-approved")
        val clientPriv = NostrSignerInternal(KeyPair()).keyPair.privKey ?: error("no privkey")
        val clientSigner = NostrSignerInternal(KeyPair(privKey = clientPriv))
        val clientPub = clientSigner.keyPair.pubKey.toHexKey()
        val connPriv = generateBunkerPrivKey()
        val connPub = localPubKeyFromPrivKey(connPriv)
        val connSigner = NostrSignerInternal(KeyPair(privKey = connPriv.hexToByteArray()))
        AmberDesktop.store(account.npub).upsert(
            AppWithPermissions(
                app = AppRecord(
                    key = clientPub,
                    name = "approved-relay-app",
                    pubKey = account.hexKey,
                    localKey = connPriv,
                ),
            ),
        )
        // Both the client key and the engine's connection key are approved.
        assertTrue(AmberDesktop.approvedRelaySenders().contains(connPub))

        val client = WsClient(http, url)
        repeat(15) { i ->
            val ev = clientSigner.signerSync.sign<Event>(TimeUtils.now(), NOSTR_CONNECT_TEST_KIND, emptyArray(), "client $i")
            assertTrue(client.send(eventMessage(ev)))
        }
        repeat(15) { i ->
            val ev = connSigner.signerSync.sign<Event>(TimeUtils.now(), NOSTR_CONNECT_TEST_KIND, emptyArray(), "response $i")
            assertTrue(client.send(eventMessage(ev)))
        }
        // All 30 acked as accepted: approved senders bypass the bucket.
        repeat(30) { assertTrue(parse(client.next()).get(2).asBoolean()) }
    }

    @Test
    fun closeCommandEndsTheSessionCleanly() {
        val client = WsClient(http, url)
        client.close()
        client.awaitClose()
    }

    @Test
    fun shutdownClosesConnectedClients() {
        val client = WsClient(http, url)
        client.send(req("sub", """{"kinds":[$NOSTR_CONNECT_TEST_KIND]}"""))
        assertEquals("EOSE", parse(client.next()).get(0).asText())
        LocalBunkerRelay.stop()
        client.awaitClose()
    }

    @Test
    fun startWhileRunningKeepsTheCurrentServer() {
        val previousPort = LocalBunkerRelay.boundPort
        // A second start must not rebind or reset the relay.
        assertEquals(Status.RUNNING, LocalBunkerRelay.start(0))
        assertEquals(previousPort, LocalBunkerRelay.boundPort)
        assertNotNull(LocalBunkerRelay.relayUrl())
        LocalBunkerRelay.stop()
        // A stopped relay accepts a fresh start.
        assertEquals(Status.RUNNING, LocalBunkerRelay.start(0))
        assertTrue(LocalBunkerRelay.boundPort > 0)
    }

    @Test
    fun explicitPortIsHonored() {
        val probe = ServerSocket(0)
        val free = probe.localPort
        probe.close()
        LocalBunkerRelay.stop()
        assertEquals(Status.RUNNING, LocalBunkerRelay.start(free))
        assertEquals(free, LocalBunkerRelay.boundPort)
        assertEquals("ws://127.0.0.1:$free/", LocalBunkerRelay.relayUrl()?.url)
    }

    @Test
    fun busyPortIsReportedAsInUse() {
        // Hold the port on the loopback address the relay binds: on macOS a
        // wildcard listener does not block a more specific 127.0.0.1 bind.
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { busy ->
            LocalBunkerRelay.stop()
            assertEquals(Status.IN_USE, LocalBunkerRelay.start(busy.localPort))
            assertNull(LocalBunkerRelay.relayUrl())
        }
    }

    private companion object {
        const val NOSTR_CONNECT_TEST_KIND = 24133
    }
}
