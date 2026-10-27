package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.RelayChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** URL preparation for the add-relay flow, mirroring the mobile onAddRelay. */
class RelayCheckerTest {
    @Test
    fun bareHostsGetWss() {
        assertEquals("wss://relay.example.com/", RelayChecker.normalizeUserInput("relay.example.com")?.url)
    }

    @Test
    fun onionAndPrivateAddressesGetPlainWs() {
        assertEquals("ws://abcdef.onion/", RelayChecker.normalizeUserInput("abcdef.onion")?.url)
        assertTrue(RelayChecker.normalizeUserInput("192.168.1.5:4869")!!.url.startsWith("ws://"))
        assertTrue(RelayChecker.normalizeUserInput("localhost:8080")!!.url.startsWith("ws://"))
    }

    @Test
    fun explicitSchemesAreKept() {
        assertEquals("wss://relay.example.com/", RelayChecker.normalizeUserInput("wss://relay.example.com")?.url)
        assertEquals("ws://relay.example.com/", RelayChecker.normalizeUserInput("ws://relay.example.com")?.url)
    }

    @Test
    fun blankAndSlashAreRejected() {
        assertNull(RelayChecker.normalizeUserInput(""))
        assertNull(RelayChecker.normalizeUserInput("   "))
        assertNull(RelayChecker.normalizeUserInput("/"))
    }

    @Test
    fun privateIpDetectionMatchesMobile() {
        assertTrue(RelayChecker.isPrivateIp("127.0.0.1:7777"))
        assertTrue(RelayChecker.isPrivateIp("172.20.0.3"))
        assertFalse(RelayChecker.isPrivateIp("relay.example.com"))
    }
}
