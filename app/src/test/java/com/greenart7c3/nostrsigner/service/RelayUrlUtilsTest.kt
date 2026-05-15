package com.greenart7c3.nostrsigner.service

import org.junit.Assert.assertEquals
import org.junit.Test

class RelayUrlUtilsTest {

    // --- bare host inputs (no scheme) ---

    @Test
    fun `bare hostname is returned unchanged`() {
        assertEquals("relay.example.com", RelayUrlUtils.extractHostAndPort("relay.example.com"))
    }

    @Test
    fun `bare hostname with port is preserved`() {
        // Regression: URI("relay.example.com:8080") treats the host as a scheme,
        // so a naive URI.host call returns null and drops the port. The helper
        // prepends "wss://" so the port is preserved.
        assertEquals("relay.example.com:8080", RelayUrlUtils.extractHostAndPort("relay.example.com:8080"))
    }

    @Test
    fun `bare IPv4 with port is preserved`() {
        assertEquals("127.0.0.1:7777", RelayUrlUtils.extractHostAndPort("127.0.0.1:7777"))
    }

    // --- inputs with scheme ---

    @Test
    fun `wss URL without port returns host only`() {
        assertEquals("relay.example.com", RelayUrlUtils.extractHostAndPort("wss://relay.example.com"))
    }

    @Test
    fun `wss URL with port returns host and port`() {
        assertEquals("relay.example.com:8080", RelayUrlUtils.extractHostAndPort("wss://relay.example.com:8080"))
    }

    @Test
    fun `ws URL with port returns host and port`() {
        assertEquals("relay.example.com:8443", RelayUrlUtils.extractHostAndPort("ws://relay.example.com:8443"))
    }

    @Test
    fun `https URL with port returns host and port`() {
        assertEquals("relay.example.com:443", RelayUrlUtils.extractHostAndPort("https://relay.example.com:443"))
    }

    @Test
    fun `trailing slash is stripped`() {
        assertEquals("relay.example.com", RelayUrlUtils.extractHostAndPort("wss://relay.example.com/"))
    }

    @Test
    fun `trailing slash on host with port is stripped`() {
        assertEquals("relay.example.com:8080", RelayUrlUtils.extractHostAndPort("wss://relay.example.com:8080/"))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("relay.example.com:8080", RelayUrlUtils.extractHostAndPort("  wss://relay.example.com:8080  "))
    }

    @Test
    fun `path after host is ignored`() {
        assertEquals("relay.example.com:8080", RelayUrlUtils.extractHostAndPort("wss://relay.example.com:8080/some/path"))
    }

    // --- equivalence: the whitelist check (uses `in`) is essentially a string compare ---

    @Test
    fun `bare host-port and wss URL with same port normalize to same value`() {
        // This is the core whitelist regression: a user adds "relay.example.com:8080"
        // to the whitelist; the event's "relay" tag is "wss://relay.example.com:8080".
        // Both must normalize to the same string for `relayHost in authWhitelist` to pass.
        val whitelistEntry = RelayUrlUtils.extractHostAndPort("relay.example.com:8080")
        val eventTag = RelayUrlUtils.extractHostAndPort("wss://relay.example.com:8080")
        assertEquals(whitelistEntry, eventTag)
    }

    @Test
    fun `different ports on the same host do not collide`() {
        val portA = RelayUrlUtils.extractHostAndPort("wss://relay.example.com:8080")
        val portB = RelayUrlUtils.extractHostAndPort("wss://relay.example.com:9090")
        assertEquals("relay.example.com:8080", portA)
        assertEquals("relay.example.com:9090", portB)
    }

    @Test
    fun `host with port is distinct from same host without port`() {
        val withPort = RelayUrlUtils.extractHostAndPort("wss://relay.example.com:8080")
        val withoutPort = RelayUrlUtils.extractHostAndPort("wss://relay.example.com")
        assertEquals("relay.example.com:8080", withPort)
        assertEquals("relay.example.com", withoutPort)
    }

    // --- edge cases ---

    @Test
    fun `null input returns empty string`() {
        assertEquals("", RelayUrlUtils.extractHostAndPort(null))
    }

    @Test
    fun `empty input returns empty string`() {
        assertEquals("", RelayUrlUtils.extractHostAndPort(""))
    }

    @Test
    fun `blank input returns empty string`() {
        assertEquals("", RelayUrlUtils.extractHostAndPort("   "))
    }

    @Test
    fun `unparseable input falls back to the trimmed original`() {
        // Garbage that URI cannot parse should fall through to the trimmed input
        // rather than throw.
        val weird = "not a valid uri at all <<>>"
        assertEquals(weird, RelayUrlUtils.extractHostAndPort("  $weird  "))
    }
}
