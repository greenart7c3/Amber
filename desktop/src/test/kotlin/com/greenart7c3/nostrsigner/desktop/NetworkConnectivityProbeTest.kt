package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.NetworkConnectivity
import com.greenart7c3.nostrsigner.desktop.core.TorMode
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The availability probe must follow the relay routing rule: through the Tor
 * SOCKS address when Tor is on (a direct probe beacons the real IP around the
 * tunnel every 10s), direct when Tor is off. The socket itself is injectable,
 * so the loop is driven against a hermetic fake instead of the internet.
 */
class NetworkConnectivityProbeTest {
    private val defaultFactory = NetworkConnectivity.newProbeSocket

    @After
    fun tearDown() {
        NetworkConnectivity.newProbeSocket = defaultFactory
    }

    /** Records the connect targets; never touches the network. */
    private class FakeSocket(
        private val succeed: Boolean,
    ) : Socket() {
        val targets = mutableListOf<SocketAddress?>()

        override fun connect(
            endpoint: SocketAddress?,
            timeout: Int,
        ) {
            targets += endpoint
            if (!succeed) throw ConnectException("refused")
        }
    }

    @Test
    fun probeProxyMatchesTheRelaySocksRouteWhenTorIsOn() {
        val target = InetSocketAddress("1.1.1.1", 53)

        val external = NetworkConnectivity.probeProxy(target, TorMode.EXTERNAL, 9150, 0)
        assertEquals(Proxy.Type.SOCKS, external?.type())
        assertEquals(InetSocketAddress("127.0.0.1", 9150), external?.address())

        val builtin = NetworkConnectivity.probeProxy(target, TorMode.BUILTIN, 9050, 37123)
        assertEquals(Proxy.Type.SOCKS, builtin?.type())
        assertEquals(InetSocketAddress("127.0.0.1", 37123), builtin?.address())
    }

    @Test
    fun probeIsFailClosedWhileBuiltinTorIsDown() {
        // Port 0 is the placeholder that refuses everything; the probe fails
        // with the relays instead of leaking a direct dial.
        val proxy = NetworkConnectivity.probeProxy(InetSocketAddress("1.1.1.1", 53), TorMode.BUILTIN, 9050, 0)
        assertEquals(InetSocketAddress("127.0.0.1", 0), proxy?.address())
    }

    @Test
    fun probeStaysDirectWhenTorIsDisabled() {
        assertNull(NetworkConnectivity.probeProxy(InetSocketAddress("8.8.8.8", 53), TorMode.DISABLED, 9050, 1234))
    }

    @Test
    fun isOnlineDialsTheInjectedSocketUntilOneSucceeds() {
        val dialled = mutableListOf<SocketAddress?>()
        NetworkConnectivity.newProbeSocket = { target ->
            dialled += target
            FakeSocket(succeed = true)
        }

        assertTrue(NetworkConnectivity.isOnline())
        // `any` stops at the first success: exactly one probe target dialled.
        assertEquals(listOf<SocketAddress?>(InetSocketAddress("1.1.1.1", 53)), dialled)
    }

    @Test
    fun isOnlineIsFalseWhenEveryProbeFails() {
        var created = 0
        NetworkConnectivity.newProbeSocket = {
            created++
            FakeSocket(succeed = false)
        }

        assertFalse(NetworkConnectivity.isOnline())
        assertEquals(2, created)
    }
}
