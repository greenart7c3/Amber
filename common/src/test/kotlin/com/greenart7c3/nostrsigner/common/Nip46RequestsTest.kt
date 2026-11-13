package com.greenart7c3.nostrsigner.common

import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Nip46RequestsTest {
    @Test
    fun `every NIP-46 method maps to its signer type`() {
        val expected = mapOf(
            "connect" to SignerType.CONNECT,
            "sign_event" to SignerType.SIGN_EVENT,
            "get_public_key" to SignerType.GET_PUBLIC_KEY,
            "nip04_encrypt" to SignerType.NIP04_ENCRYPT,
            "nip04_decrypt" to SignerType.NIP04_DECRYPT,
            "nip44_encrypt" to SignerType.NIP44_ENCRYPT,
            "nip44_decrypt" to SignerType.NIP44_DECRYPT,
            "nip44v3_encrypt" to SignerType.NIP44_V3_ENCRYPT,
            "nip44v3_decrypt" to SignerType.NIP44_V3_DECRYPT,
            "decrypt_zap_event" to SignerType.DECRYPT_ZAP_EVENT,
            "ping" to SignerType.PING,
            "switch_relays" to SignerType.SWITCH_RELAYS,
            "sign_psbt" to SignerType.SIGN_PSBT,
            "logout" to SignerType.LOGOUT,
        )
        expected.forEach { (method, type) -> assertEquals(method, type, nip46SignerType(method)) }
    }

    @Test
    fun `unknown, empty and differently cased methods are invalid`() {
        listOf("sign_message", "unknown_method", "", "CONNECT").forEach {
            assertEquals(it, SignerType.INVALID, nip46SignerType(it))
        }
    }

    @Test
    fun `NIP-44 v3 kind and scope come from params 1 and 2`() {
        val request = BunkerRequest("id", "nip44v3_encrypt", arrayOf("pk", "42", "dm", "data"))
        assertEquals(42, request.nip44v3Kind())
        assertEquals("dm", request.nip44v3Scope())
    }

    @Test
    fun `a missing or non-numeric NIP-44 v3 kind is null and a missing scope is empty`() {
        assertNull(BunkerRequest("id", "nip44v3_encrypt", arrayOf("pk")).nip44v3Kind())
        assertNull(BunkerRequest("id", "nip44v3_encrypt", arrayOf("pk", "not-a-number", "s", "d")).nip44v3Kind())
        assertEquals("", BunkerRequest("id", "nip44v3_encrypt", arrayOf("pk", "4")).nip44v3Scope())
    }

    @Test
    fun `a connect from a connected app or one using a bunker secret is a repeat`() {
        assertTrue(isRepeatedConnect(SignerType.CONNECT, "key", "key", appUsesSecret = false, appConnected = true))
        assertTrue(isRepeatedConnect(SignerType.CONNECT, "key", "secret", appUsesSecret = true, appConnected = false))
    }

    @Test
    fun `a first connect or a non-connect request is not a repeat`() {
        // secret == key is a bunker placeholder no client has attached to yet.
        assertFalse(isRepeatedConnect(SignerType.CONNECT, "key", "key", appUsesSecret = true, appConnected = false))
        assertFalse(isRepeatedConnect(SignerType.CONNECT, "key", "secret", appUsesSecret = false, appConnected = false))
        assertFalse(isRepeatedConnect(SignerType.SIGN_EVENT, "key", "secret", appUsesSecret = true, appConnected = true))
    }

    @Test
    fun `a connect secret is refused unless it names an unused bunker connection`() {
        assertEquals("invalid secret", connectSecretRejection("s", appFound = false, appConnected = false, appUsesSecret = false))
        assertEquals("no secret", connectSecretRejection(" ", appFound = true, appConnected = false, appUsesSecret = true))
        assertEquals("already connected", connectSecretRejection("s", appFound = true, appConnected = true, appUsesSecret = true))
        assertEquals("secret not in use", connectSecretRejection("s", appFound = true, appConnected = false, appUsesSecret = false))
        assertNull(connectSecretRejection("s", appFound = true, appConnected = false, appUsesSecret = true))
    }
}
