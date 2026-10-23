package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.net.InetSocketAddress
import java.net.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test

/**
 * F-12: when built-in Tor stops or fails, the OkHttp proxy must not keep
 * pointing at the released loopback SOCKS port — another app could bind it
 * and receive Amber's proxied dials. The released port is replaced with a
 * fail-closed placeholder (127.0.0.1:0), but only while the configured proxy
 * still names that port, so a concurrent switch to Orbot or DIRECT survives.
 */
class TorProxyReleaseTest {
    private val releasedPort = 41189

    @Before
    fun setUp() {
        // The offline flavor ships a no-op TorManager with no fail() at all.
        assumeFalse(BuildFlavorChecker.isOfflineFlavor())
        mockkObject(AmberLog)
        every { AmberLog.d(any(), any()) } returns Unit
        every { AmberLog.d(any(), any(), any()) } returns Unit
        every { AmberLog.i(any(), any()) } returns Unit
        every { AmberLog.i(any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        HttpClientManager.clearProxy()
        unmockkObject(AmberLog)
    }

    private fun socksPortFlow(): MutableStateFlow<Int> {
        val field = TorManager::class.java.getDeclaredField("_socksPort")
        field.isAccessible = true
        return field.get(TorManager) as MutableStateFlow<Int>
    }

    private fun currentProxy(): Proxy? {
        val field = HttpClientManager::class.java.getDeclaredField("currentProxy")
        field.isAccessible = true
        return field.get(HttpClientManager) as Proxy?
    }

    private fun proxyPort(): Int? = (currentProxy()?.address() as? InetSocketAddress)?.port

    @Test
    fun `stop replaces the released port with the fail-closed placeholder`() {
        HttpClientManager.setDefaultProxyOnPort(releasedPort)
        socksPortFlow().value = releasedPort

        TorManager.stop()

        assertEquals(0, TorManager.socksPort.value)
        assertEquals(0, proxyPort())
    }

    @Test
    fun `fail replaces the released port with the fail-closed placeholder`() {
        HttpClientManager.setDefaultProxyOnPort(releasedPort)
        socksPortFlow().value = releasedPort

        val fail = TorManager::class.java.getDeclaredMethod("fail", String::class.java)
        fail.isAccessible = true
        fail.invoke(TorManager, "test failure")

        assertEquals(0, TorManager.socksPort.value)
        assertEquals(0, proxyPort())
    }

    @Test
    fun `a proxy that moved on to another port is not overwritten`() {
        // Tor is torn down after the user already switched to Orbot on 9050.
        HttpClientManager.setDefaultProxyOnPort(9050)
        socksPortFlow().value = releasedPort

        TorManager.stop()

        assertEquals(9050, proxyPort())
    }

    @Test
    fun `release with no port configured keeps the current proxy`() {
        HttpClientManager.setDefaultProxyOnPort(9050)
        socksPortFlow().value = 0

        TorManager.stop()

        assertEquals(9050, proxyPort())
    }

    @Test
    fun `placeholder never matches a released port`() {
        HttpClientManager.setDefaultProxyOnPort(0)
        socksPortFlow().value = 0

        HttpClientManager.releaseProxyPort(0)

        assertEquals(0, proxyPort())
    }

    @Test
    fun `direct proxy survives a release`() {
        HttpClientManager.clearProxy()
        socksPortFlow().value = releasedPort

        TorManager.stop()

        assertFalse(currentProxy() is Proxy && proxyPort() == releasedPort)
        assertEquals(null, currentProxy())
    }

    @Test
    fun `stop is idempotent after a failure`() {
        HttpClientManager.setDefaultProxyOnPort(releasedPort)
        socksPortFlow().value = releasedPort

        val fail = TorManager::class.java.getDeclaredMethod("fail", String::class.java)
        fail.isAccessible = true
        fail.invoke(TorManager, "test failure")
        TorManager.stop()

        assertEquals(0, TorManager.socksPort.value)
        assertEquals(0, proxyPort())
    }

    @Test
    fun `placeholder is a socks proxy on loopback port zero`() {
        socksPortFlow().value = releasedPort
        HttpClientManager.setDefaultProxyOnPort(releasedPort)

        HttpClientManager.releaseProxyPort(releasedPort)

        val proxy = currentProxy()
        assertTrue(proxy != null && proxy.type() == Proxy.Type.SOCKS)
        assertEquals(0, (proxy!!.address() as InetSocketAddress).port)
    }
}
