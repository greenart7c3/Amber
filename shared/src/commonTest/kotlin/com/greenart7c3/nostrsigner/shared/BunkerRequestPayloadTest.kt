package com.greenart7c3.nostrsigner.shared

import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BunkerRequestPayloadTest {
    @Test
    fun signEventPayload() {
        val request = BunkerRequest("id-1", "sign_event", arrayOf("{\"kind\":1}"))
        assertEquals(BunkerMethod.SIGN_EVENT, bunkerMethodOf(request.method))
        assertEquals("{\"kind\":1}", BunkerRequestPayload.payload(request))
    }

    @Test
    fun nip44EncryptPayload() {
        val request = BunkerRequest("id-2", "nip44_encrypt", arrayOf("deadbeef", "plaintext"))
        assertEquals(BunkerMethod.NIP44_ENCRYPT, bunkerMethodOf(request.method))
        assertEquals("plaintext", BunkerRequestPayload.payload(request))
        assertEquals("deadbeef", BunkerRequestPayload.counterpartyPubKey(request))
    }

    @Test
    fun nip44v3Payload() {
        val request = BunkerRequest("id-3", "nip44v3_encrypt", arrayOf("deadbeef", "7", "scope", "cGF5bG9hZA=="))
        assertEquals(BunkerMethod.NIP44_V3_ENCRYPT, bunkerMethodOf(request.method))
        assertEquals("cGF5bG9hZA==", BunkerRequestPayload.payload(request))
        assertEquals(7, BunkerRequestPayload.nip44v3Kind(request))
        assertEquals("scope", BunkerRequestPayload.nip44v3Scope(request))
    }

    @Test
    fun unknownMethodIsInvalid() {
        val request = BunkerRequest("id-4", "not_a_real_method", arrayOf())
        assertEquals(BunkerMethod.INVALID, bunkerMethodOf(request.method))
        assertNull(BunkerRequestPayload.counterpartyPubKey(request))
    }
}
