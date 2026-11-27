package com.greenart7c3.nostrsigner.ui

import com.greenart7c3.nostrsigner.ui.actions.relayUrlToAdd
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RelayUrlToAddTest {
    @Suppress("NoComments")
    @Before
    fun setUp() {
        // Quartz logs a URL it cannot normalize; android.util.Log is not mocked on the JVM.
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    private fun added(url: String): String? = relayUrlToAdd(url)?.url

    @Test
    fun anExplicitSchemeIsKept() {
        assertEquals("wss://relay.example.com/", added("wss://relay.example.com"))
        assertEquals("ws://relay.example.com/", added("ws://relay.example.com"))
    }

    @Test
    fun aBareHostGetsTls() {
        assertEquals("wss://relay.example.com/", added("relay.example.com"))
    }

    @Test
    fun onionAndPrivateHostsGetPlainWebSocket() {
        val onion = "2gzyxa5ihm7nsggfxnu52rck2vv4rvmdlkiu3zzui5du4xyclen53wid.onion"
        assertEquals("ws://$onion/", added(onion))
        assertEquals("ws://$onion/", added("$onion/"))
        assertEquals("ws://192.168.1.20:7777/", added("192.168.1.20:7777"))
    }

    @Test
    fun somethingThatIsNotARelayUrlIsRefused() {
        assertNull(added("https://relay.example.com"))
    }
}
