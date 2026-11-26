package com.greenart7c3.nostrsigner.common

import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
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
    fun `NIP-44 v3 kind, scope and payload come from params 1 to 3`() {
        val request = BunkerRequest("id", "nip44v3_encrypt", arrayOf("pk", "42", "dm", "data"))
        assertEquals(42, request.nip44v3Kind())
        assertEquals("dm", request.nip44v3Scope())
        assertEquals("data", request.nip44v3Payload())
    }

    @Test
    fun `a missing or non-numeric NIP-44 v3 kind is null and a missing scope or payload is empty`() {
        assertNull(BunkerRequest("id", "nip44v3_encrypt", arrayOf("pk")).nip44v3Kind())
        assertNull(BunkerRequest("id", "nip44v3_encrypt", arrayOf("pk", "not-a-number", "s", "d")).nip44v3Kind())
        assertEquals("", BunkerRequest("id", "nip44v3_encrypt", arrayOf("pk", "4")).nip44v3Scope())
        assertEquals("", BunkerRequest("id", "nip44v3_decrypt", arrayOf("pk", "4", "s")).nip44v3Payload())
    }

    @Test
    fun `a decrypt_zap_event carries its zap request second, after the pubkey`() {
        assertEquals("{zap}", BunkerRequest("id", "decrypt_zap_event", arrayOf("pk", "{zap}")).zapRequestParam())
        val onlyTheZapRequest = arrayOf("{zap}")
        assertNull(BunkerRequest("id", "decrypt_zap_event", onlyTheZapRequest).zapRequestParam())
    }

    @Test
    fun `the NIP-44 v3 types are what the two NIP-44 v3 methods map to`() {
        assertEquals(nip44v3SignerTypes, setOf("nip44v3_encrypt", "nip44v3_decrypt").map(::nip46SignerType).toSet())
    }

    @Test
    fun `a connect from a connected app or one using a bunker secret is a repeat`() {
        assertTrue(isRepeatedConnect(SignerType.CONNECT, "key", "key", appUsesSecret = false, appConnected = true))
        assertTrue(isRepeatedConnect(SignerType.CONNECT, "key", "secret", appUsesSecret = true, appConnected = false))
    }

    @Test
    fun `a first connect or a non-connect request is not a repeat`() {
        val appKey = "key"
        val unattachedBunkerPlaceholderSecret = appKey
        assertFalse(isRepeatedConnect(SignerType.CONNECT, appKey, unattachedBunkerPlaceholderSecret, appUsesSecret = true, appConnected = false))
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

    private fun request(method: String, params: String) = JacksonMapper.mapper.readValue("""{"id":"1","method":"$method","params":$params}""", BunkerRequest::class.java)

    private val event = JacksonMapper.mapper.writeValueAsString("""{"kind":1,"content":"hi","tags":[],"created_at":1}""")

    @Test
    fun `a request with the params its method needs is well formed`() {
        assertTrue(request("sign_event", "[$event]").hasRequiredParams())
        assertTrue(request("nip44_encrypt", """["pk","hi"]""").hasRequiredParams())
        assertTrue(request("nip44v3_decrypt", """["pk","1","","payload"]""").hasRequiredParams())
        assertTrue(request("decrypt_zap_event", """["pk","{}"]""").hasRequiredParams())
        assertTrue(request("sign_psbt", """["psbt"]""").hasRequiredParams())
    }

    @Test
    fun `a request whose method takes no params is well formed`() {
        assertTrue(request("get_public_key", "[]").hasRequiredParams())
        assertTrue(request("ping", "[]").hasRequiredParams())
    }

    @Test
    fun `each method's params are read from their place in the layout`() {
        val nip44 = request("nip44_encrypt", """["peer","hello"]""")
        assertEquals("peer", nip44.peerPubKeyParam())
        assertEquals("hello", nip44.encryptionTextParam())
        assertEquals("peer", request("nip44v3_decrypt", """["peer","1","s","payload"]""").peerPubKeyParam())
        assertEquals("psbt", request("sign_psbt", """["psbt"]""").psbtParam())
        assertTrue(request("sign_event", "[$event]").eventParam()!!.contains("\"kind\""))
    }

    @Test
    fun `a missing param reads as null, or empty for the encryption text`() {
        val empty = request("nip04_decrypt", "[]")
        assertNull(empty.peerPubKeyParam())
        assertEquals("", empty.encryptionTextParam())
        assertNull(request("sign_psbt", "[]").psbtParam())
        assertNull(request("sign_event", "[]").eventParam())
    }

    @Test
    fun `a request missing a param its method needs is not`() {
        assertFalse(request("sign_event", "[]").hasRequiredParams())
        assertFalse(request("sign_event", """["not json"]""").hasRequiredParams())
        assertFalse(request("nip04_decrypt", "[]").hasRequiredParams())
        assertFalse(request("nip44v3_encrypt", "[]").hasRequiredParams())
        assertFalse(request("decrypt_zap_event", """["{}"]""").hasRequiredParams())
        assertFalse(request("sign_psbt", "[]").hasRequiredParams())
    }
}
