package com.greenart7c3.nostrsigner.desktop.core

import com.fasterxml.jackson.databind.JsonNode
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.net.BindException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Local bunker relay: a private NIP-01 relay on the loopback interface, so
 * NIP-46 clients on this machine connect to the bunker without public relays.
 * Opt out under Settings -> Desktop ([DesktopSettings.localBunkerServer]).
 *
 * It speaks just enough NIP-01 for the bunker flow (EVENT, REQ, CLOSE plus
 * OK/EOSE/NOTICE): the app itself subscribes to it like any other relay
 * (`BunkerEngine`), so requests arriving over it take the same permission and
 * approval path as relay requests, and answers are published back to the
 * relay the request arrived on. New bunker connections list this relay
 * beside the default relays (`AmberDesktop.newBunkerRelays`).
 *
 * Trust model: only the local machine can reach it (bound to the loopback
 * interface), and it holds only bunker traffic — kind-24133 events encrypted
 * to per-connection keys. Any web origin may connect (real clients like
 * coracle.social are hosted outside localhost): origin says nothing about
 * intent, authorization is the connection secret plus the approval UI. The
 * relay itself rate limits clients that are not an approved connection
 * (denied EVENTs skip signature verification, REQ/malformed floods are cut
 * off per connection); approved connections are never throttled, and the
 * engine rate limits unapproved senders again when processing. Events are
 * verified, capped in count and size, and kept in memory only.
 */
object LocalBunkerRelay {
    enum class Status { STOPPED, RUNNING, IN_USE, FAILED }

    const val TAG = "LocalBunkerRelay"

    /** Default port; [BuildVariant.isDebug] runs one port up so a debug run never fights an installed release. */
    const val PORT = 7777
    const val DEBUG_PORT = 7778

    val status = MutableStateFlow(Status.STOPPED)

    /** The port actually bound (differs from the request when asked for an ephemeral port, as tests do). */
    @Volatile
    var boundPort: Int = 0
        private set

    private var server: ServerSocket? = null
    private var core: RelayCore? = null

    /** The relay URL to subscribe to and put in bunker URIs; null unless the server is running. */
    fun relayUrl(): NormalizedRelayUrl? = if (status.value == Status.RUNNING) RelayUrlNormalizer.normalizeOrNull("ws://127.0.0.1:$boundPort/") else null

    @Synchronized
    fun start(requestedPort: Int): Status {
        if (server != null) return status.value
        val started = try {
            val socket = ServerSocket(requestedPort, RelayCore.MAX_CLIENTS, InetAddress.getLoopbackAddress())
            boundPort = socket.localPort
            server = socket
            core = RelayCore(socket)
            AmberLogger.i(TAG, "Listening on ws://127.0.0.1:${socket.localPort}/")
            Status.RUNNING
        } catch (_: BindException) {
            AmberLogger.e(TAG, "Port $requestedPort is already in use; local bunker connections stay off")
            Status.IN_USE
        } catch (e: Exception) {
            AmberLogger.e(TAG, "Could not start on port $requestedPort", e)
            Status.FAILED
        }
        status.value = started
        return started
    }

    @Synchronized
    fun stop() {
        core?.shutdown()
        core = null
        server?.let { runCatching { it.close() } }
        server = null
        boundPort = 0
        status.value = Status.STOPPED
        AmberLogger.i(TAG, "Stopped")
    }
}

/**
 * The relay engine: the accept loop, the connected WebSocket clients with
 * their subscriptions, and the bounded in-memory event store. One instance
 * per [LocalBunkerRelay.start]; [shutdown] tears everything down.
 */
private class RelayCore(serverSocket: ServerSocket) {
    companion object {
        const val MAX_CLIENTS = 64
        const val MAX_EVENTS = 512
        const val MAX_SUBSCRIPTIONS_PER_CLIENT = 20

        /** Matches the local socket signer's per-message cap. */
        const val MAX_MESSAGE_BYTES = 1024 * 1024
        const val MAX_HANDSHAKE_BYTES = 8 * 1024

        private const val WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

        /** How often the approved-sender set is rebuilt from the saved connections. */
        private const val APPROVED_SENDER_REFRESH_MS = 30_000L

        private const val OP_CONTINUATION = 0x0
        private const val OP_TEXT = 0x1
        private const val OP_BINARY = 0x2
        private const val OP_CLOSE = 0x8
        private const val OP_PING = 0x9
        private const val OP_PONG = 0xA
    }

    private val tag = LocalBunkerRelay.TAG
    private val mapper = JacksonMapper.mapper
    private val connections = ConcurrentHashMap<Long, Connection>()
    private val connectionSeq = AtomicLong()
    private val store = EventStore()

    // Unapproved EVENT senders: per-pubkey token buckets (denied events skip
    // verification — the schnorr check is the CPU cost a flood exploits).
    private val eventLimiter = SenderRateLimiter()

    // REQ subscriptions and malformed commands per client connection.
    private val clientLimiter = SenderRateLimiter(
        capacity = 60,
        windowSeconds = 60,
        aggregateCapacity = 240,
    )

    @Volatile
    private var approvedSenderCache: Set<String> = emptySet()
    private var approvedRefreshedAtMs = 0L

    private fun approvedSenders(): Set<String> {
        val now = System.currentTimeMillis()
        if (now - approvedRefreshedAtMs > APPROVED_SENDER_REFRESH_MS) {
            approvedRefreshedAtMs = now
            runCatching { AmberDesktop.approvedRelaySenders() }
                .onSuccess { approvedSenderCache = it }
                .onFailure { entry -> AmberLogger.e(tag, "Could not refresh approved relay senders", entry) }
        }
        return approvedSenderCache
    }

    @Volatile
    private var shuttingDown = false

    init {
        Thread({ acceptLoop(serverSocket) }, "$tag-accept").apply { isDaemon = true }.start()
    }

    fun shutdown() {
        shuttingDown = true
        // Closing the server socket breaks the accept loop (accept() is not
        // interruptible); closing each client interrupts its read loop.
        // Not toList(): it reads size, then next(), and a connection ending in
        // between makes next() throw. The map's own iterator tolerates that.
        connections.values.forEach { it.close() }
        connections.clear()
        synchronized(store) { store.clear() }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: Exception) {
                if (!socket.isClosed) {
                    AmberLogger.e(tag, "Accept failed; shutting the local relay down", e)
                    LocalBunkerRelay.stop()
                }
                return
            }
            if (connections.size >= MAX_CLIENTS) {
                AmberLogger.e(tag, "Too many clients; dropping a connection")
                runCatching { client.close() }
                continue
            }
            val id = connectionSeq.incrementAndGet()
            val connection = Connection(id, client)
            connections[id] = connection
            if (shuttingDown) {
                // Accepted while stop() was tearing the relay down.
                connection.close()
                connections.remove(id)
                return
            }
            Thread.ofVirtual().name("$tag-client-$id").start {
                try {
                    connection.serve()
                } finally {
                    connections.remove(id)
                }
            }
        }
    }

    /** First value of every single-letter tag, under its lowercased name: what REQ tag filters match. */
    private fun Event.tagValues(): Map<String, List<String>> = buildMap {
        for (tag in tags) {
            if (tag.size > 1 && tag[0].length == 1) {
                merge(tag[0].lowercase(), listOf(tag[1])) { old, extra -> old + extra }
            }
        }
    }

    private inner class Connection(
        val id: Long,
        private val socket: Socket,
    ) {
        private val subscriptions = ConcurrentHashMap<String, List<RelayFilter>>()
        private val writeLock = Any()
        private var output: BufferedOutputStream? = null

        @Volatile
        private var closed = false

        fun serve() {
            try {
                runCatching { socket.tcpNoDelay = true }
                val input = socket.getInputStream().buffered()
                output = BufferedOutputStream(socket.getOutputStream(), 16 * 1024)
                if (!handshake(input)) return
                AmberLogger.d(tag, "Client #$id connected")
                while (true) {
                    handleCommand(nextMessage(input) ?: return)
                }
            } catch (e: ProtocolError) {
                AmberLogger.d(tag, "Client #$id protocol error: ${e.message}")
                sendClose(e.closeCode, e.message ?: "protocol error")
            } catch (_: EOFException) {
                // The peer hung up: a normal end for one-shot clients.
            } catch (e: Exception) {
                if (!closed) AmberLogger.e(tag, "Client #$id failed", e)
            } finally {
                close()
            }
        }

        fun close() {
            if (closed) return
            closed = true
            synchronized(writeLock) {
                runCatching { output?.flush() }
                output = null
            }
            runCatching { socket.close() }
        }

        private fun maybeDeliver(event: StoredEvent) {
            for ((subId, filters) in subscriptions) {
                if (filters.any { it.matches(event) }) send(eventMessage(subId, event.raw))
            }
        }

        private fun handshake(input: InputStream): Boolean {
            val head = readHandshakeHead(input) ?: return false
            val lines = head.split("\r\n")
            val request = lines.firstOrNull()?.split(" ") ?: return false
            if (request.size < 3) return reject("HTTP/1.1 400 Bad Request")
            val headers = HashMap<String, String>()
            for (line in lines.drop(1)) {
                val separator = line.indexOf(':')
                if (separator > 0) headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
            }
            // Chrome sends a CORS/PNA preflight (OPTIONS) before a WebSocket to
            // a private address from a public page (coracle.social -> ws://127.0.0.1).
            // Answer it and close; the real handshake arrives on a new connection.
            if (request[0].equals("OPTIONS", true)) {
                sendRaw(
                    "HTTP/1.1 204 No Content\r\n" +
                        "Access-Control-Allow-Origin: " + (headers["origin"] ?: "*") + "\r\n" +
                        "Access-Control-Allow-Methods: GET, OPTIONS\r\n" +
                        "Access-Control-Allow-Headers: *\r\n" +
                        "Access-Control-Allow-Private-Network: true\r\n" +
                        "Access-Control-Max-Age: 86400\r\n" +
                        "\r\n",
                )
                return false
            }
            if (!request[0].equals("GET", true)) return reject("HTTP/1.1 400 Bad Request")
            if (headers["upgrade"]?.equals("websocket", true) != true) return reject("HTTP/1.1 400 Bad Request")
            if (headers["connection"]?.contains("upgrade", true) != true) return reject("HTTP/1.1 400 Bad Request")
            val key = headers["sec-websocket-key"] ?: return reject("HTTP/1.1 400 Bad Request")
            if (headers["sec-websocket-version"] != "13") {
                return reject("HTTP/1.1 426 Upgrade Required\r\nSec-WebSocket-Version: 13")
            }
            val digest = MessageDigest.getInstance("SHA-1").digest((key + WS_GUID).toByteArray(Charsets.UTF_8))
            val accept = Base64.getEncoder().encodeToString(digest)
            // Access-Control-Allow-* lets pages on public origins (coracle.social,
            // …) pass Chrome's Private Network Access check for ws://127.0.0.1.
            sendRaw(
                "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: $accept\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Private-Network: true\r\n" +
                    "\r\n",
            )
            return true
        }

        /** Why the upgrade was refused; extra headers (like Sec-WebSocket-Version) ride in [reason]. */
        private fun reject(reason: String): Boolean {
            sendRaw("$reason\r\nConnection: close\r\n\r\n")
            return false
        }

        /** The request head up to the blank line, or null on EOF / when it exceeds the cap. */
        private fun readHandshakeHead(input: InputStream): String? {
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) return null
                bytes.write(buffer, 0, read)
                if (bytes.size() > MAX_HANDSHAKE_BYTES) return null
                val soFar = bytes.toString(Charsets.UTF_8)
                val end = soFar.indexOf("\r\n\r\n")
                if (end >= 0) return soFar.substring(0, end)
            }
        }

        /** The next complete text message, or null once the client closed the session. */
        private fun nextMessage(input: InputStream): String? {
            val fragments = ByteArrayOutputStream()
            var dataOpcode = -1
            while (true) {
                val frame = readFrame(input)
                when (frame.opcode) {
                    OP_CLOSE -> {
                        sendClose(closeCodeOf(frame.payload), "")
                        return null
                    }

                    OP_PING -> sendFrame(OP_PONG, frame.payload)
                    OP_PONG -> {}
                    else -> {
                        if (frame.opcode == OP_CONTINUATION && dataOpcode == -1) {
                            throw ProtocolError("continuation frame outside a message")
                        }
                        if (frame.opcode != OP_CONTINUATION && dataOpcode != -1) {
                            throw ProtocolError("new data frame inside a fragmented message")
                        }
                        if (frame.opcode == OP_BINARY) throw ProtocolError("binary messages are not supported")
                        if (fragments.size() + frame.payload.size > MAX_MESSAGE_BYTES) {
                            throw ProtocolError("message exceeds the ${MAX_MESSAGE_BYTES / 1024} KiB limit", 1009)
                        }
                        fragments.write(frame.payload)
                        if (frame.opcode != OP_CONTINUATION) dataOpcode = frame.opcode
                        if (frame.fin) {
                            val message = fragments.toString(Charsets.UTF_8)
                            fragments.reset()
                            dataOpcode = -1
                            return message
                        }
                    }
                }
            }
        }

        /** Malformed or unknown command: answer while the connection is under its budget, drop it when this is abuse. */
        private fun malformed(message: String) {
            if (clientLimiter.allow("bad|$id", System.currentTimeMillis())) {
                send(notice(message))
            } else {
                send(notice("rate limited"))
                close()
            }
        }

        private fun handleCommand(text: String) {
            val node = try {
                mapper.readTree(text)
            } catch (_: Exception) {
                malformed("invalid JSON")
                return
            }
            if (!node.isArray || node.isEmpty) {
                malformed("a command must be a non-empty array")
                return
            }
            when (node.get(0).asText("")) {
                "EVENT" -> handleEvent(node)
                "REQ" -> handleReq(node)
                "CLOSE" -> handleClose(node)
                else -> malformed("unknown command")
            }
        }

        private fun handleEvent(command: JsonNode) {
            val eventNode = command.get(1)
            val eventId = eventNode?.takeIf { it.isObject }?.get("id")?.takeIf { it.isTextual }?.asText() ?: ""
            if (eventNode == null || !eventNode.isObject) {
                send(ok(eventId, false, "invalid: event must be an object"))
                return
            }
            val raw = try {
                mapper.writeValueAsString(eventNode)
            } catch (_: Exception) {
                send(ok(eventId, false, "invalid: event could not be serialized"))
                return
            }
            // Quartz's parser (not Jackson): events are kotlinx-serialized, and
            // this must round-trip exactly what relays accept.
            val event = Event.fromJsonOrNull(raw) ?: run {
                send(ok(eventId, false, "invalid: event could not be parsed"))
                return
            }
            // Relay-level rate limiting for senders that are not an approved
            // connection: denied events skip verification — the signature check
            // is the CPU cost a flood exploits. Approved senders (saved client
            // keys + the engine's own connection key) are never limited, like
            // in BunkerEngine.
            if (event.pubKey !in approvedSenders() && !eventLimiter.allow(event.pubKey, System.currentTimeMillis())) {
                send(ok(event.id, false, "rate limited"))
                return
            }
            // verify() checks both the id (serialization hash) and the signature.
            if (!event.verify()) {
                send(ok(event.id, false, "invalid: id or signature verification failed"))
                return
            }
            val stored = StoredEvent(
                id = event.id,
                raw = raw,
                createdAt = event.createdAt,
                kind = event.kind,
                pubKey = event.pubKey,
                tags = event.tagValues(),
            )
            val isNew = synchronized(store) { store.add(stored) }
            send(ok(event.id, true, ""))
            if (isNew) {
                // After the ack so the publisher's round trip is never queued behind our fan-out.
                connections.values.forEach { it.maybeDeliver(stored) }
            }
        }

        private fun handleReq(command: JsonNode) {
            if (!clientLimiter.allow("req|$id", System.currentTimeMillis())) {
                send(notice("rate limited"))
                return
            }
            val subId = command.get(1)?.takeIf { it.isTextual }?.asText()
            if (subId.isNullOrEmpty()) {
                send(notice("REQ needs a subscription id"))
                return
            }
            val filters = buildList {
                for (i in 2 until command.size()) {
                    val node = command.get(i)
                    if (node?.isObject == true) add(RelayFilter.of(node))
                }
            }
            if (filters.isEmpty()) {
                send(notice("REQ needs at least one filter"))
                return
            }
            if (!subscriptions.containsKey(subId) && subscriptions.size >= MAX_SUBSCRIPTIONS_PER_CLIENT) {
                send(notice("too many subscriptions"))
                return
            }
            subscriptions[subId] = filters
            val matches = synchronized(store) { store.query(filters) }
            matches.forEach { send(eventMessage(subId, it.raw)) }
            send(eose(subId))
        }

        private fun handleClose(command: JsonNode) {
            val subId = command.get(1)?.asText() ?: return
            subscriptions.remove(subId)
        }

        private fun notice(message: String): String = mapper.writeValueAsString(arrayListOf("NOTICE", message))

        private fun ok(
            eventId: String,
            accepted: Boolean,
            message: String,
        ): String = mapper.writeValueAsString(arrayListOf("OK", eventId, accepted, message))

        private fun eose(subId: String): String = mapper.writeValueAsString(arrayListOf("EOSE", subId))

        private fun eventMessage(
            subId: String,
            rawEvent: String,
        ): String = "[\"EVENT\",${mapper.writeValueAsString(subId)},$rawEvent]"

        private fun sendRaw(text: String) = synchronized(writeLock) {
            output?.write(text.toByteArray(Charsets.UTF_8))
            output?.flush()
        }

        fun send(message: String) {
            sendFrame(OP_TEXT, message.toByteArray(Charsets.UTF_8))
        }

        private fun sendFrame(
            opcode: Int,
            payload: ByteArray,
        ) {
            synchronized(writeLock) {
                val out = output ?: return
                out.write(0x80 or opcode)
                when {
                    payload.size <= 125 -> out.write(payload.size)
                    payload.size <= 0xFFFF -> {
                        out.write(126)
                        out.write((payload.size shr 8) and 0xFF)
                        out.write(payload.size and 0xFF)
                    }

                    else -> {
                        out.write(127)
                        for (shift in 56 downTo 8 step 8) out.write(((payload.size.toLong() shr shift) and 0xFF).toInt())
                        out.write(payload.size and 0xFF)
                    }
                }
                out.write(payload)
                out.flush()
            }
        }

        private fun sendClose(
            code: Int,
            reason: String,
        ) {
            val bytes = reason.toByteArray(Charsets.UTF_8)
            val body = bytes.copyOfRange(0, minOf(123, bytes.size))
            val payload = ByteArray(2 + body.size)
            payload[0] = ((code shr 8) and 0xFF).toByte()
            payload[1] = (code and 0xFF).toByte()
            body.copyInto(payload, 2)
            sendFrame(OP_CLOSE, payload)
        }

        private fun readFrame(input: InputStream): Frame {
            val first = input.readU8()
            if (first and 0x70 != 0) throw ProtocolError("reserved frame bits set")
            val fin = first and 0x80 != 0
            val opcode = first and 0x0F
            val second = input.readU8()
            if (second and 0x80 == 0) throw ProtocolError("client frames must be masked")
            var length = (second and 0x7F).toLong()
            when (length) {
                126L -> length = (input.readU8().toLong() shl 8) or input.readU8().toLong()
                127L -> {
                    length = 0L
                    repeat(8) { length = (length shl 8) or input.readU8().toLong() }
                }
            }
            if (length > MAX_MESSAGE_BYTES) throw ProtocolError("frame exceeds the ${MAX_MESSAGE_BYTES / 1024} KiB limit", 1009)
            val mask = ByteArray(4)
            input.readFully(mask)
            val payload = ByteArray(length.toInt())
            input.readFully(payload)
            for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            if (opcode >= OP_CLOSE) {
                if (!fin) throw ProtocolError("control frames must not be fragmented")
                if (payload.size > 125) throw ProtocolError("control frame payload too large")
            }
            return Frame(opcode, fin, payload)
        }
    }
}

private class ProtocolError(
    message: String,
    val closeCode: Int = 1002,
) : Exception(message)

private class Frame(
    val opcode: Int,
    val fin: Boolean,
    val payload: ByteArray,
)

/** A stored event: the raw JSON as received (forwarded verbatim) plus the fields filters match on. */
private class StoredEvent(
    val id: String,
    val raw: String,
    val createdAt: Long,
    val kind: Int,
    val pubKey: String,
    /** First value of every single-letter tag, lowercased name: the NIP-01 filterable shape. */
    val tags: Map<String, List<String>>,
)

/** The NIP-01 filter subset the bunker flow needs: ids/authors/kinds/since/until/limit plus #tag lists. */
private class RelayFilter(
    val ids: List<String>?,
    val authors: List<String>?,
    val kinds: Set<Int>?,
    val since: Long?,
    val until: Long?,
    val limit: Int?,
    val tagValues: Map<String, Set<String>>,
) {
    fun matches(event: StoredEvent): Boolean {
        if (ids?.none(event.id::startsWith) == true) return false
        if (authors?.none(event.pubKey::startsWith) == true) return false
        if (kinds != null && event.kind !in kinds) return false
        if (since != null && event.createdAt < since) return false
        if (until != null && event.createdAt > until) return false
        for ((name, values) in tagValues) {
            val present = event.tags[name] ?: return false
            if (values.none(present::contains)) return false
        }
        return true
    }

    companion object {
        fun of(node: JsonNode): RelayFilter = RelayFilter(
            ids = node.stringList("ids"),
            authors = node.stringList("authors"),
            kinds = node.intSet("kinds"),
            since = node.longField("since"),
            until = node.longField("until"),
            limit = node.longField("limit")?.toInt(),
            tagValues = buildMap {
                node.fieldNames().forEach { key ->
                    if (key.length > 1 && key.startsWith("#")) {
                        val values = node.get(key)?.stringSet() ?: emptySet()
                        if (values.isNotEmpty()) put(key.substring(1).lowercase(), values)
                    }
                }
            },
        )

        private fun JsonNode.stringList(name: String): List<String>? = get(name)?.takeIf { it.isArray }?.stringSet()?.toList()?.takeIf { it.isNotEmpty() }

        private fun JsonNode.stringSet(): Set<String> = buildSet {
            if (isArray) for (i in 0 until size()) get(i)?.takeIf { it.isTextual }?.let { add(it.asText()) }
        }

        private fun JsonNode.intSet(name: String): Set<Int>? = get(name)?.takeIf { it.isArray }?.let { array ->
            buildSet {
                for (i in 0 until array.size()) array.get(i)?.takeIf { it.canConvertToInt() }?.let { add(it.asInt()) }
            }.takeIf { it.isNotEmpty() }
        }

        private fun JsonNode.longField(name: String): Long? = get(name)?.takeIf { it.canConvertToLong() }?.asLong()
    }
}

/** Bounded in-memory event store; the relay is a request pipe, not an archive. */
private class EventStore {
    private val events = ArrayDeque<StoredEvent>(RelayCore.MAX_EVENTS)
    private val ids = HashSet<String>()

    /** true when the event is new; duplicates are acked but neither stored nor broadcast again. */
    fun add(event: StoredEvent): Boolean {
        if (!ids.add(event.id)) return false
        events.addLast(event)
        while (events.size > RelayCore.MAX_EVENTS) {
            ids.remove(events.removeFirst().id)
        }
        return true
    }

    /** Matches sorted by created_at; a filter's limit keeps the latest events (NIP-01 semantics). */
    fun query(filters: List<RelayFilter>): List<StoredEvent> {
        val matches = events.filter { event -> filters.any { it.matches(event) } }.sortedBy { it.createdAt }
        val limit = filters.mapNotNull { it.limit }.maxOrNull()
        return if (limit != null && matches.size > limit) matches.takeLast(limit) else matches
    }

    fun clear() {
        events.clear()
        ids.clear()
    }
}

private fun closeCodeOf(payload: ByteArray): Int = if (payload.size >= 2) ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF) else 1000

private fun InputStream.readU8(): Int = read().also { if (it < 0) throw EOFException("closed") }

private fun InputStream.readFully(buffer: ByteArray) {
    var offset = 0
    while (offset < buffer.size) {
        val read = read(buffer, offset, buffer.size - offset)
        if (read < 0) throw EOFException("closed")
        offset += read
    }
}
