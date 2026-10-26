package com.greenart7c3.nostrsigner.common.tor

import java.io.DataInputStream
import java.net.Authenticator
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TorSocksAuthenticatorTest {
    @Before
    fun setUp() {
        TorSocksAuthenticator.install()
    }

    @After
    fun tearDown() {
        TorSocksAuthenticator.clear()
        Authenticator.setDefault(null)
    }

    /**
     * A minimal SOCKS5 server that requires username/password, like Arti's
     * listener. Completes with the credentials the client sent, or null when
     * the client did not offer the username/password method.
     */
    private fun fakeSocksServer(server: ServerSocket): CompletableFuture<Pair<String, String>?> {
        val result = CompletableFuture<Pair<String, String>?>()
        thread(isDaemon = true) {
            runCatching {
                server.accept().use { socket ->
                    val input = DataInputStream(socket.getInputStream())
                    val output = socket.getOutputStream()
                    check(input.readUnsignedByte() == 5)
                    val methods = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
                    if (2.toByte() !in methods) {
                        output.write(byteArrayOf(5, 0xFF.toByte()))
                        result.complete(null)
                        return@use
                    }
                    output.write(byteArrayOf(5, 2))
                    check(input.readUnsignedByte() == 1)
                    val username = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
                    val password = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
                    result.complete(String(username) to String(password))
                    // Refuse: only the credentials matter here.
                    output.write(byteArrayOf(1, 1))
                }
            }.onFailure { result.completeExceptionally(it) }
        }
        return result
    }

    private fun dialThrough(port: Int) {
        runCatching {
            Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress(InetAddress.getLoopbackAddress(), port))).use {
                it.connect(InetSocketAddress.createUnresolved("example.onion", 80), 5_000)
            }
        }
    }

    @Test
    fun `java's SOCKS client sends the listener's credentials`() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val sent = fakeSocksServer(server)
            val (username, password) = TorSocksAuthenticator.newCredentials()
            TorSocksAuthenticator.set(server.localPort, username, password)

            dialThrough(server.localPort)

            assertEquals(username to password, sent.get(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `credentials are not handed out for another port`() {
        TorSocksAuthenticator.set(port = 1, username = "user", password = "secret")

        val answer = Authenticator.requestPasswordAuthentication(
            "127.0.0.1",
            InetAddress.getLoopbackAddress(),
            9050,
            "SOCKS5",
            "SOCKS authentication",
            null,
        )

        assertNull(answer)
    }

    @Test
    fun `credentials are cleared with the listener`() {
        TorSocksAuthenticator.set(port = 9150, username = "user", password = "secret")
        TorSocksAuthenticator.clear()

        val answer = Authenticator.requestPasswordAuthentication(
            "127.0.0.1",
            InetAddress.getLoopbackAddress(),
            9150,
            "SOCKS5",
            "SOCKS authentication",
            null,
        )

        assertNull(answer)
    }

    @Test
    fun `each listener gets fresh random credentials`() {
        val first = TorSocksAuthenticator.newCredentials()
        val second = TorSocksAuthenticator.newCredentials()

        assertTrue(first != second && first.first != first.second)
        assertEquals(32, first.first.length)
        assertEquals(32, first.second.length)
    }
}
