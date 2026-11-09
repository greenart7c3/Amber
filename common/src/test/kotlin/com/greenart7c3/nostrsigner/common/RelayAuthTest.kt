package com.greenart7c3.nostrsigner.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayAuthTest {
    @Test
    fun hostAndPortIgnoresSchemePathAndTrailingSlash() {
        assertEquals("relay.example", relayHostAndPort("wss://relay.example/"))
        assertEquals("relay.example:8080", relayHostAndPort("ws://relay.example:8080/path"))
        assertEquals("relay.example:8080", relayHostAndPort("relay.example:8080"))
        assertEquals("", relayHostAndPort("  "))
        assertEquals("", relayHostAndPort(null))
    }

    @Test
    fun authHostComesFromTheFirstRelayTag() {
        assertEquals("a.example", relayAuthHost(arrayOf(arrayOf("challenge", "x"), arrayOf("relay", "wss://a.example"))))
        assertEquals("", relayAuthHost(arrayOf(arrayOf("challenge", "x"))))
    }

    @Test
    fun blankAndStarGrantsCoverEveryRelay() {
        assertTrue(isAllRelaysGrant("*"))
        assertTrue(isAllRelaysGrant(""))
        assertTrue(isAllRelaysGrant(null))
        assertFalse(isAllRelaysGrant("a.example"))
    }

    @Test
    fun whitelistOnlyRestrictsRelayAuth() {
        val whitelist = listOf("a.example")
        assertFalse(isBlockedByAuthWhitelist(RELAY_AUTH_KIND, "b.example", emptyList()))
        assertFalse(isBlockedByAuthWhitelist(RELAY_AUTH_KIND, "a.example", whitelist))
        assertTrue(isBlockedByAuthWhitelist(RELAY_AUTH_KIND, "b.example", whitelist))
        assertTrue(isBlockedByAuthWhitelist(RELAY_AUTH_KIND, "", whitelist))
        assertFalse(isBlockedByAuthWhitelist(1, "b.example", whitelist))
        assertFalse(isBlockedByAuthWhitelist(null, "b.example", whitelist))
    }

    @Test
    fun thisRelayOnlyNeedsARelayToBeRemembered() {
        assertTrue(canRememberRelayAuth(allRelays = false, relayHost = "a.example"))
        assertTrue(canRememberRelayAuth(allRelays = true, relayHost = ""))
        assertFalse(canRememberRelayAuth(allRelays = false, relayHost = ""))
    }
}
