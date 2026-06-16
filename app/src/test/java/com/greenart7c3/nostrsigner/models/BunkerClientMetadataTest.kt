package com.greenart7c3.nostrsigner.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BunkerClientMetadataTest {

    @Test
    fun `parses name url and image`() {
        val json = """{"name":"My App","url":"https://example.com","image":"https://example.com/icon.png"}"""
        val metadata = BunkerClientMetadata.parseOrNull(json)
        assertEquals("My App", metadata?.name)
        assertEquals("https://example.com", metadata?.url)
        assertEquals("https://example.com/icon.png", metadata?.image)
    }

    @Test
    fun `parses partial metadata`() {
        val metadata = BunkerClientMetadata.parseOrNull("""{"name":"Just A Name"}""")
        assertEquals("Just A Name", metadata?.name)
        assertEquals("", metadata?.url)
        assertEquals("", metadata?.image)
    }

    @Test
    fun `ignores unknown properties`() {
        val json = """{"name":"App","extra":"ignored","perms":"sign_event:1"}"""
        val metadata = BunkerClientMetadata.parseOrNull(json)
        assertEquals("App", metadata?.name)
    }

    @Test
    fun `returns null for null input`() {
        assertNull(BunkerClientMetadata.parseOrNull(null))
    }

    @Test
    fun `returns null for blank input`() {
        assertNull(BunkerClientMetadata.parseOrNull(""))
        assertNull(BunkerClientMetadata.parseOrNull("   "))
    }

    @Test
    fun `returns null for malformed json`() {
        assertNull(BunkerClientMetadata.parseOrNull("not json"))
        assertNull(BunkerClientMetadata.parseOrNull("{"))
    }

    @Test
    fun `returns null when all fields are empty`() {
        assertNull(BunkerClientMetadata.parseOrNull("""{"name":"","url":"","image":""}"""))
        assertNull(BunkerClientMetadata.parseOrNull("{}"))
    }

    @Test
    fun `trims whitespace in fields`() {
        val metadata = BunkerClientMetadata.parseOrNull("""{"name":"  App  ","url":" https://x.com "}""")
        assertEquals("App", metadata?.name)
        assertEquals("https://x.com", metadata?.url)
    }

    @Test
    fun `fromConnectRequest reads the 4th param`() {
        val request = """
            {"id":"1","method":"connect","params":["remotepk","secret","sign_event:1","{\"name\":\"Web Client\",\"url\":\"https://web.client\",\"image\":\"https://web.client/i.png\"}"]}
        """.trimIndent()
        val metadata = BunkerClientMetadata.fromConnectRequest(request)
        assertEquals("Web Client", metadata?.name)
        assertEquals("https://web.client", metadata?.url)
        assertEquals("https://web.client/i.png", metadata?.image)
    }

    @Test
    fun `fromConnectRequest returns null when metadata param is absent`() {
        val request = """{"id":"1","method":"connect","params":["remotepk","secret","sign_event:1"]}"""
        assertNull(BunkerClientMetadata.fromConnectRequest(request))
    }

    @Test
    fun `fromConnectRequest returns null when params missing or not an array`() {
        assertNull(BunkerClientMetadata.fromConnectRequest("""{"id":"1","method":"connect"}"""))
        assertNull(BunkerClientMetadata.fromConnectRequest("""{"id":"1","method":"connect","params":"x"}"""))
    }

    @Test
    fun `fromConnectRequest returns null for null blank or malformed input`() {
        assertNull(BunkerClientMetadata.fromConnectRequest(null))
        assertNull(BunkerClientMetadata.fromConnectRequest(""))
        assertNull(BunkerClientMetadata.fromConnectRequest("not json"))
    }
}
