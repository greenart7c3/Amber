package com.greenart7c3.nostrsigner.common.relays

import com.fasterxml.jackson.databind.JsonNode
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.TimeUtils
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRelayServerTest {
    private val mapper = JacksonMapper.mapper
    private val signer = NostrSignerInternal(KeyPair())
    private val http = HttpClient.newHttpClient()
    private val servers = mutableListOf<LocalRelayServer>()
    private val sockets = mutableListOf<WebSocket>()

    private object SilentLogger : LocalRelayServer.Logger {
        override fun d(message: String) = Unit

        override fun i(message: String) = Unit

        override fun e(
            message: String,
            throwable: Throwable?,
        ) = Unit
    }

    /** A relay client: every text message the relay sends lands in [messages]. */
    private class Client(
        val socket: WebSocket,
        val messages: LinkedBlockingQueue<JsonNode>,
    )

    @After
    fun tearDown() {
        sockets.forEach { runCatching { it.abort() } }
        servers.forEach { it.stop() }
    }

    private fun relay(storesEphemeral: Boolean): LocalRelayServer {
        val server = LocalRelayServer(SilentLogger, { emptySet() }, storesEphemeral = storesEphemeral)
        servers += server
        assertEquals(LocalRelayServer.Status.RUNNING, server.start(0))
        return server
    }

    private fun connect(server: LocalRelayServer): Client {
        val messages = LinkedBlockingQueue<JsonNode>()
        val listener = object : WebSocket.Listener {
            private val buffer = StringBuilder()

            override fun onText(
                webSocket: WebSocket,
                data: CharSequence,
                last: Boolean,
            ): CompletionStage<*>? {
                buffer.append(data)
                if (last) {
                    messages.add(mapper.readTree(buffer.toString()))
                    buffer.setLength(0)
                }
                webSocket.request(1)
                return null
            }
        }
        val socket = http.newWebSocketBuilder()
            .buildAsync(URI("ws://127.0.0.1:${server.boundPort}/"), listener)
            .get(5, TimeUnit.SECONDS)
        sockets += socket
        return Client(socket, messages)
    }

    private fun Client.send(vararg command: Any) {
        socket.sendText(mapper.writeValueAsString(command), true).get(5, TimeUnit.SECONDS)
    }

    private fun Client.next(): JsonNode? = messages.poll(5, TimeUnit.SECONDS)

    private fun Client.nextOfType(type: String): JsonNode {
        while (true) {
            val message = next() ?: throw AssertionError("no $type message")
            if (message.get(0).asText() == type) return message
        }
    }

    // Distinct content: two events signed in the same second would otherwise share an id.
    private fun event(kind: Int): Event = signer.signerSync.sign(TimeUtils.now(), kind, emptyArray(), UUID.randomUUID().toString())

    private fun Client.publish(event: Event) {
        send("EVENT", mapper.readTree(event.toJson()))
        val ok = nextOfType("OK")
        assertEquals(event.id, ok.get(1).asText())
        assertTrue(ok.get(2).asBoolean())
    }

    private fun Client.subscribe(
        subId: String,
        kind: Int,
    ): List<String> {
        send("REQ", subId, mapOf("kinds" to listOf(kind)))
        val ids = mutableListOf<String>()
        while (true) {
            val message = next() ?: throw AssertionError("no EOSE")
            when (message.get(0).asText()) {
                "EVENT" -> ids += message.get(2).get("id").asText()
                "EOSE" -> return ids
            }
        }
    }

    @Test
    fun storedEventsReachLaterSubscriptions() {
        val server = relay(storesEphemeral = true)
        val client = connect(server)
        val stored = event(24133)
        client.publish(stored)
        assertEquals(listOf(stored.id), client.subscribe("sub", 24133))
    }

    @Test
    fun ephemeralEventsOnlyReachLiveSubscriptionsWhenNotStored() {
        val server = relay(storesEphemeral = false)
        val publisher = connect(server)
        val early = event(24133)
        publisher.publish(early)

        val subscriber = connect(server)
        assertEquals(emptyList<String>(), subscriber.subscribe("sub", 24133))

        val live = event(24133)
        publisher.publish(live)
        val delivered = subscriber.nextOfType("EVENT")
        assertEquals("sub", delivered.get(1).asText())
        assertEquals(live.id, delivered.get(2).get("id").asText())

        // A duplicate is acknowledged but not delivered a second time.
        publisher.publish(live)
        assertNull(subscriber.messages.poll(300, TimeUnit.MILLISECONDS))
    }

    @Test
    fun regularKindsAreStoredEvenWhenEphemeralOnesAreNot() {
        val server = relay(storesEphemeral = false)
        val client = connect(server)
        val note = event(1)
        client.publish(note)
        assertEquals(listOf(note.id), client.subscribe("notes", 1))
    }

    @Test
    fun malformedCommandsGetANotice() {
        val server = relay(storesEphemeral = true)
        val client = connect(server)
        client.socket.sendText("not json", true).get(5, TimeUnit.SECONDS)
        client.nextOfType("NOTICE")
        client.send("REQ", "sub")
        assertEquals("REQ needs at least one filter", client.nextOfType("NOTICE").get(1).asText())
    }
}
