package com.greenart7c3.nostrsigner.service

import android.net.NetworkCapabilities
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkStateTest {
    @Before
    fun setUp() {
        mockkObject(HttpClientManager)
        every { HttpClientManager.setDefaultTimeout(any()) } just runs
    }

    @After
    fun tearDown() = unmockkAll()

    private fun capabilities(vararg transports: Int) = mockk<NetworkCapabilities> {
        every { hasTransport(any()) } answers { firstArg<Int>() in transports }
        every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
    }

    @Test
    fun `reports a change only when the transport or internet access changes`() {
        val state = NetworkState()

        assertTrue(state.update(capabilities(NetworkCapabilities.TRANSPORT_WIFI)))
        assertTrue(state.isOnWifi)
        assertFalse(state.update(capabilities(NetworkCapabilities.TRANSPORT_WIFI)))

        assertTrue(state.update(capabilities(NetworkCapabilities.TRANSPORT_CELLULAR, NetworkCapabilities.TRANSPORT_VPN)))
        assertTrue(state.isOnMobileData && state.isOnVpn)
        assertFalse(state.isOnWifi)

        // No network at all: offline, so a change.
        assertTrue(state.update(null))
        assertFalse(state.update(null))
    }
}
