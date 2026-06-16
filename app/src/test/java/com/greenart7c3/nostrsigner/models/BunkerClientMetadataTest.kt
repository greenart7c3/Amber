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
}
