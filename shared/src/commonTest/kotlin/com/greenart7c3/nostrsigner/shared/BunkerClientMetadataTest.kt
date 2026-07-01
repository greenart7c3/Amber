package com.greenart7c3.nostrsigner.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BunkerClientMetadataTest {
    @Test
    fun parsesNameUrlImage() {
        val json = """{"name":"Test Client","url":"https://example.com","image":"https://example.com/icon.png"}"""
        val metadata = BunkerClientMetadata.parseOrNull(json)
        assertEquals(BunkerClientMetadata("Test Client", "https://example.com", "https://example.com/icon.png"), metadata)
    }

    @Test
    fun blankOrMissingIsNull() {
        assertNull(BunkerClientMetadata.parseOrNull(null))
        assertNull(BunkerClientMetadata.parseOrNull(""))
        assertNull(BunkerClientMetadata.parseOrNull("not json"))
        assertNull(BunkerClientMetadata.parseOrNull("{}"))
    }

    @Test
    fun fromConnectRequestReadsFourthParam() {
        val requestJson = """{"id":"1","method":"connect","params":["pubkey","secret","","{\"name\":\"My App\"}"]}"""
        val metadata = BunkerClientMetadata.fromConnectRequest(requestJson)
        assertEquals("My App", metadata?.name)
    }

    @Test
    fun fromConnectRequestWithoutFourthParamIsNull() {
        val requestJson = """{"id":"1","method":"connect","params":["pubkey","secret",""]}"""
        assertNull(BunkerClientMetadata.fromConnectRequest(requestJson))
    }
}
