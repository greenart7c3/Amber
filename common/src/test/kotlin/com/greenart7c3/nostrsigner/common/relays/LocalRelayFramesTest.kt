package com.greenart7c3.nostrsigner.common.relays

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.Socket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The relay's HTTP upgrade and WebSocket framing, spoken byte by byte over a plain socket. */
class LocalRelayFramesTest {
    private object SilentLogger : LocalRelayServer.Logger {
        override fun d(message: String) = Unit

        override fun i(message: String) = Unit

        override fun e(
            message: String,
            throwable: Throwable?,
        ) = Unit
    }

    private class ServerFrame(
        val opcode: Int,
        val payload: ByteArray,
    ) {
        val text get() = payload.toString(Charsets.UTF_8)
        val closeCode get() = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
    }

    private val server = LocalRelayServer(SilentLogger, { emptySet() }, storesEphemeral = false)
    private val sockets = mutableListOf<Socket>()

    @Before
    fun start() {
        assertEquals(LocalRelayServer.Status.RUNNING, server.start(0))
    }

    @After
    fun stop() {
        sockets.forEach { runCatching { it.close() } }
        server.stop()
    }

    private fun open(): Socket = Socket(InetAddress.getLoopbackAddress(), server.boundPort).also {
        it.soTimeout = 5_000
        sockets += it
    }

    /** Sends a request head and returns the response head (status line and headers). */
    private fun Socket.request(head: String): String {
        getOutputStream().write("$head\r\n\r\n".toByteArray(Charsets.UTF_8))
        val bytes = ByteArrayOutputStream()
        val input = getInputStream()
        while (!bytes.toString(Charsets.UTF_8).endsWith("\r\n\r\n")) {
            val read = input.read()
            if (read < 0) break
            bytes.write(read)
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun upgradeHead(
        method: String = "GET",
        version: String? = "13",
        key: String? = "dGhlIHNhbXBsZSBub25jZQ==",
    ): String = buildList {
        add("$method / HTTP/1.1")
        add("Host: 127.0.0.1")
        add("Upgrade: websocket")
        add("Connection: Upgrade")
        key?.let { add("Sec-WebSocket-Key: $it") }
        version?.let { add("Sec-WebSocket-Version: $it") }
    }.joinToString("\r\n")

    private fun upgraded(): Socket = open().also {
        assertTrue(it.request(upgradeHead()).startsWith("HTTP/1.1 101"))
    }

    /** One client frame: masked unless told otherwise, with the 7-, 16- or 64-bit length its size needs. */
    private fun Socket.sendFrame(
        opcode: Int,
        payload: ByteArray,
        fin: Boolean = true,
        masked: Boolean = true,
        firstByteExtra: Int = 0,
        declaredLength: Long = payload.size.toLong(),
    ) {
        val out = ByteArrayOutputStream()
        out.write((if (fin) 0x80 else 0) or firstByteExtra or opcode)
        val maskBit = if (masked) 0x80 else 0
        when {
            declaredLength < 126 -> out.write(maskBit or declaredLength.toInt())

            declaredLength <= 0xFFFF -> {
                out.write(maskBit or 126)
                out.write((declaredLength shr 8).toInt() and 0xFF)
                out.write(declaredLength.toInt() and 0xFF)
            }

            else -> {
                out.write(maskBit or 127)
                for (shift in 56 downTo 0 step 8) out.write((declaredLength shr shift).toInt() and 0xFF)
            }
        }
        val mask = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        if (masked) out.write(mask)
        out.write(if (masked) ByteArray(payload.size) { (payload[it].toInt() xor mask[it % 4].toInt()).toByte() } else payload)
        getOutputStream().write(out.toByteArray())
        getOutputStream().flush()
    }

    private fun Socket.sendText(
        text: String,
        fin: Boolean = true,
    ) = sendFrame(OP_TEXT, text.toByteArray(Charsets.UTF_8), fin)

    private fun Socket.readFrame(): ServerFrame {
        val input = DataInputStream(getInputStream())
        val opcode = input.readUnsignedByte() and 0x0F
        val length = when (val short = input.readUnsignedByte() and 0x7F) {
            126 -> input.readUnsignedShort().toLong()
            127 -> input.readLong()
            else -> short.toLong()
        }
        val payload = ByteArray(length.toInt())
        input.readFully(payload)
        return ServerFrame(opcode, payload)
    }

    private fun Socket.assertClosedWith(code: Int) {
        val frame = readFrame()
        assertEquals(OP_CLOSE, frame.opcode)
        assertEquals(code, frame.closeCode)
    }

    private fun InputStream.atEnd(): Boolean = read() < 0

    /** A REQ the relay answers with EOSE, padded with JSON whitespace to [size] bytes. */
    private fun req(size: Int = 0): String {
        val start = "[\"REQ\",\"s\",{\"kinds\":[1]}"
        return start + " ".repeat((size - start.length - 1).coerceAtLeast(0)) + "]"
    }

    private fun Socket.assertEose() {
        val frame = readFrame()
        assertEquals(OP_TEXT, frame.opcode)
        assertEquals("[\"EOSE\",\"s\"]", frame.text)
    }

    @Test
    fun upgradeAnswersTheRfcAcceptKey() {
        val response = open().request(upgradeHead())
        assertTrue(response, response.startsWith("HTTP/1.1 101 Switching Protocols\r\n"))
        assertTrue(response, response.contains("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n"))
    }

    @Test
    fun requestsThatAreNotAnUpgradeAreRefused() {
        for (head in listOf(upgradeHead(method = "POST"), upgradeHead(key = null), "GET / HTTP/1.1\r\nHost: 127.0.0.1", "GET /")) {
            val socket = open()
            val response = socket.request(head)
            assertTrue(response, response.startsWith("HTTP/1.1 400 Bad Request\r\n"))
            assertTrue(socket.getInputStream().atEnd())
        }
    }

    @Test
    fun anotherWebSocketVersionGetsUpgradeRequired() {
        val response = open().request(upgradeHead(version = "12"))
        assertTrue(response, response.startsWith("HTTP/1.1 426 Upgrade Required\r\nSec-WebSocket-Version: 13\r\n"))
    }

    @Test
    fun preflightIsAnsweredWithTheOrigin() {
        val response = open().request("OPTIONS / HTTP/1.1\r\nOrigin: https://coracle.social")
        assertTrue(response, response.startsWith("HTTP/1.1 204 No Content\r\n"))
        assertTrue(response, response.contains("Access-Control-Allow-Origin: https://coracle.social\r\n"))
    }

    @Test
    fun messagesOfEveryLengthEncodingArrive() {
        val socket = upgraded()
        for (size in listOf(0, 200, 70_000)) {
            socket.sendText(req(size))
            socket.assertEose()
        }
    }

    @Test
    fun fragmentedMessageIsAssembledAroundAPing() {
        val socket = upgraded()
        val message = req()
        socket.sendText(message.substring(0, 10), fin = false)
        socket.sendFrame(OP_PING, "hi".toByteArray())
        val pong = socket.readFrame()
        assertEquals(OP_PONG, pong.opcode)
        assertEquals("hi", pong.text)
        socket.sendFrame(OP_CONTINUATION, message.substring(10).toByteArray())
        socket.assertEose()
    }

    @Test
    fun closeIsEchoed() {
        val socket = upgraded()
        socket.sendFrame(OP_CLOSE, byteArrayOf(0x03, 0xE8.toByte()))
        socket.assertClosedWith(1000)
    }

    @Test
    fun malformedFramesCloseWithProtocolError() {
        val cases: List<Socket.() -> Unit> = listOf(
            { sendFrame(OP_TEXT, req().toByteArray(), masked = false) },
            { sendFrame(OP_TEXT, req().toByteArray(), firstByteExtra = 0x40) },
            { sendFrame(OP_BINARY, byteArrayOf(1, 2, 3)) },
            { sendFrame(OP_CONTINUATION, "x".toByteArray()) },
            {
                sendText("[\"REQ\"", fin = false)
                sendText(req())
            },
            { sendFrame(OP_PING, "x".toByteArray(), fin = false) },
            { sendFrame(OP_PING, ByteArray(126)) },
        )
        for (case in cases) {
            val socket = upgraded()
            socket.case()
            socket.assertClosedWith(1002)
        }
    }

    @Test
    fun oversizedFrameClosesWithMessageTooBig() {
        val socket = upgraded()
        socket.sendFrame(OP_TEXT, ByteArray(0), declaredLength = MAX_MESSAGE_BYTES + 1L)
        socket.assertClosedWith(1009)
    }

    @Test
    fun oversizedFragmentedMessageClosesWithMessageTooBig() {
        val socket = upgraded()
        val half = ByteArray(MAX_MESSAGE_BYTES / 2 + 1) { ' '.code.toByte() }
        socket.sendFrame(OP_TEXT, half, fin = false)
        socket.sendFrame(OP_CONTINUATION, half)
        socket.assertClosedWith(1009)
    }

    private companion object {
        const val OP_CONTINUATION = 0x0
        const val OP_TEXT = 0x1
        const val OP_BINARY = 0x2
        const val OP_CLOSE = 0x8
        const val OP_PING = 0x9
        const val OP_PONG = 0xA

        /** The relay's per-message cap (`RelayCore.MAX_MESSAGE_BYTES`). */
        const val MAX_MESSAGE_BYTES = 1024 * 1024
    }
}
