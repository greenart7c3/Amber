package com.greenart7c3.nostrsigner.service

import org.junit.Assert.assertEquals
import org.junit.Test

class RelayUrlUtilsTest {

    @Test
    fun `bare hostname is returned unchanged`() {
        assertEquals("relay.example.com", RelayUrlUtils.extractHostAndPort("relay.example.com"))
    }

    @Test
    fun `bare hostname with port keeps the port although URI reads the host as a scheme`() {
        assertEquals("relay.example.com:8080", RelayUrlUtils.extractHostAndPort("relay.example.com:8080"))
    }

    @Test
    fun `bare IPv4 with port is preserved`() {
        assertEquals("127.0.0.1:7777", RelayUrlUtils.extractHostAndPort("127.0.0.1:7777"))
    }

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

    @Test
    fun `a bare host-port whitelist entry matches the wss relay tag with the same port`() {
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
    fun `unparseable input falls back to the trimmed original instead of throwing`() {
        val weird = "not a valid uri at all <<>>"
        assertEquals(weird, RelayUrlUtils.extractHostAndPort("  $weird  "))
    }
}
