package com.greenart7c3.nostrsigner.desktop.core

import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import okhttp3.Dns

/**
 * Resolves `.local` names with multicast DNS on the local network (RFC 6762),
 * never through the system resolver: one that forwards `.local` to unicast
 * DNS would send a LAN relay's name to the ISP's servers, outside Tor.
 *
 * It sends a one-shot "legacy unicast" query (RFC 6762 §6.7) for A and AAAA
 * from an ephemeral port to 224.0.0.251:5353; responders answer that port
 * directly, so no port-5353 socket or system daemon is needed.
 */
object MdnsResolver {
    private val group = InetSocketAddress(InetAddress.getByName("224.0.0.251"), 5353)
    private const val TYPE_A = 1
    private const val TYPE_AAAA = 28
    private const val CLASS_IN = 1
    private const val TIMEOUT_MS = 1500

    fun resolve(host: String, timeoutMs: Int = TIMEOUT_MS): List<InetAddress> {
        val name = host.lowercase().trimEnd('.')
        MulticastSocket(0).use { socket ->
            socket.timeToLive = 255
            socket.soTimeout = timeoutMs
            val query = query(name)
            socket.send(DatagramPacket(query, query.size, group))
            val deadline = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(9000)
            while (true) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) return emptyList()
                socket.soTimeout = left.toInt().coerceAtLeast(1)
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    return emptyList()
                }
                val found = runCatching { parseAnswers(buffer.copyOf(packet.length), name) }.getOrDefault(emptyList())
                if (found.isNotEmpty()) return found
            }
        }
    }

    /** A standard query (id 0, no flags) asking for both A and AAAA of [name]. */
    internal fun query(name: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0))
        listOf(TYPE_A, TYPE_AAAA).forEach { type ->
            encodeName(name, out)
            out.write(byteArrayOf((type shr 8).toByte(), type.toByte(), 0, CLASS_IN.toByte()))
        }
        return out.toByteArray()
    }

    private fun encodeName(name: String, out: ByteArrayOutputStream) {
        name.split('.').forEach { label ->
            val bytes = label.toByteArray(Charsets.UTF_8)
            require(bytes.size in 1..63) { "Invalid DNS label in $name" }
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
    }

    /** A and AAAA records for [name] in a DNS response (answers and additional records). */
    internal fun parseAnswers(message: ByteArray, name: String): List<InetAddress> {
        val buf = ByteBuffer.wrap(message)
        buf.position(4)
        val questions = buf.short.toInt() and 0xFFFF
        val records = (buf.short.toInt() and 0xFFFF) + (buf.short.toInt() and 0xFFFF) + (buf.short.toInt() and 0xFFFF)
        repeat(questions) {
            readName(message, buf)
            buf.position(buf.position() + 4)
        }
        val found = mutableListOf<InetAddress>()
        repeat(records) {
            val recordName = readName(message, buf)
            val type = buf.short.toInt() and 0xFFFF
            // The top bit of the class is mDNS's cache-flush flag.
            val recordClass = buf.short.toInt() and 0x7FFF
            buf.int // TTL
            val length = buf.short.toInt() and 0xFFFF
            val data = ByteArray(length).also { buf.get(it) }
            if (recordClass == CLASS_IN && recordName.equals(name, ignoreCase = true)) {
                if ((type == TYPE_A && length == 4) || (type == TYPE_AAAA && length == 16)) {
                    found += InetAddress.getByAddress(name, data)
                }
            }
        }
        return found
    }

    private fun readName(message: ByteArray, buf: ByteBuffer): String {
        val labels = mutableListOf<String>()
        var position = buf.position()
        var jumped = false
        var hops = 0
        while (true) {
            val length = message[position].toInt() and 0xFF
            when {
                length == 0 -> {
                    if (!jumped) buf.position(position + 1)
                    return labels.joinToString(".")
                }
                length and 0xC0 == 0xC0 -> {
                    // Compression pointer; bounded so a looping message can't hang us.
                    require(++hops <= 32) { "DNS name compression loop" }
                    val target = ((length and 0x3F) shl 8) or (message[position + 1].toInt() and 0xFF)
                    if (!jumped) buf.position(position + 2)
                    jumped = true
                    position = target
                }
                else -> {
                    labels += String(message, position + 1, length, Charsets.UTF_8)
                    position += 1 + length
                }
            }
        }
    }
}

/**
 * The resolver for direct (non-Tor) dials. `localhost` and `*.localhost`
 * are loopback by definition (RFC 6761) and are answered without asking any
 * resolver; `*.local` is resolved only with [MdnsResolver]; everything else
 * uses the system resolver, as before.
 */
object LocalNameDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val host = hostname.lowercase().trimEnd('.')
        return when {
            host == "localhost" || host.endsWith(".localhost") ->
                listOf(InetAddress.getByName("127.0.0.1"), InetAddress.getByName("::1"))
            host.endsWith(".local") ->
                MdnsResolver.resolve(host).ifEmpty { throw UnknownHostException("$hostname: no mDNS answer on the local network") }
            else -> Dns.SYSTEM.lookup(hostname)
        }
    }
}
