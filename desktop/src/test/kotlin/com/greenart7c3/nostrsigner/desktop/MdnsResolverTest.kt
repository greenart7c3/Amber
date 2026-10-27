package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.LocalNameDns
import com.greenart7c3.nostrsigner.desktop.core.MdnsResolver
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `.localhost` and `.local` relay names must never reach the system resolver,
 * which could forward them to unicast DNS outside Tor.
 */
class MdnsResolverTest {
    @Test
    fun localhostNamesAreLoopbackWithoutAnyLookup() {
        val addresses = LocalNameDns.lookup("relay.localhost")
        assertTrue(addresses.all { it.isLoopbackAddress })
        assertEquals(listOf("127.0.0.1", "0:0:0:0:0:0:0:1"), addresses.map { it.hostAddress })
    }

    @Test
    fun theQueryAsksForAAndAaaa() {
        val query = MdnsResolver.query("nas.local")
        val name = byteArrayOf(3, 'n'.code.toByte(), 'a'.code.toByte(), 's'.code.toByte(), 5) + "local".toByteArray() + byteArrayOf(0)
        val expected = byteArrayOf(0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0) +
            name + byteArrayOf(0, 1, 0, 1) +
            name + byteArrayOf(0, 28, 0, 1)
        assertArrayEquals(expected, query)
    }

    /** A response with one question, then [answers] encoded after it. */
    private fun response(vararg answers: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 0x84.toByte(), 0, 0, 1, 0, answers.size.toByte(), 0, 0, 0, 0))
        // Question at offset 12: nas.local A IN.
        out.write(byteArrayOf(3) + "nas".toByteArray() + byteArrayOf(5) + "local".toByteArray() + byteArrayOf(0, 0, 1, 0, 1))
        answers.forEach { out.write(it) }
        return out.toByteArray()
    }

    /** A record whose name is a compression pointer to the question (offset 12). */
    private fun record(type: Int, data: ByteArray, cacheFlush: Boolean = true) = byteArrayOf(0xC0.toByte(), 12, 0, type.toByte(), if (cacheFlush) 0x80.toByte() else 0, 1, 0, 0, 0, 120, 0, data.size.toByte()) + data

    @Test
    fun answersAreParsedThroughNameCompression() {
        val v4 = byteArrayOf(192.toByte(), 168.toByte(), 1, 20)
        val v6 = InetAddress.getByName("fe80::1").address
        val found = MdnsResolver.parseAnswers(response(record(1, v4), record(28, v6)), "nas.local")
        assertEquals(listOf("192.168.1.20", "fe80:0:0:0:0:0:0:1"), found.map { it.hostAddress })
    }

    @Test
    fun answersForOtherNamesAreIgnored() {
        val other = byteArrayOf(5) + "other".toByteArray() + byteArrayOf(5) + "local".toByteArray() + byteArrayOf(0, 0, 1, 0x80.toByte(), 1, 0, 0, 0, 120, 0, 4, 10, 0, 0, 9)
        assertTrue(MdnsResolver.parseAnswers(response(other), "nas.local").isEmpty())
    }

    @Test
    fun aCompressionLoopIsRejected() {
        // A record name pointing at itself.
        val loop = response(byteArrayOf(0xC0.toByte(), 0))
        val looped = loop.copyOf().also {
            val at = it.size - 2
            it[at + 1] = at.toByte()
        }
        assertThrows(IllegalArgumentException::class.java) { MdnsResolver.parseAnswers(looped, "nas.local") }
    }
}
