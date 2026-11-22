package com.greenart7c3.nostrsigner.common

import java.net.URLEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrConnectUriTest {
    private val key = "ab".repeat(32)
    private val knownNips = setOf(4, 44)

    private fun parse(query: String) = NostrConnectUri.parse("nostrconnect://$key?$query") { it in knownNips }

    private fun enc(value: String) = URLEncoder.encode(value, "utf-8")

    @Test
    fun `the client key must be a lowercase hex public key`() {
        assertEquals(key, parse("relay=wss%3A%2F%2Fr.example").clientPubKey)
        val error = assertThrows(IllegalArgumentException::class.java) { NostrConnectUri.parse("nostrconnect://${key.uppercase()}") { true } }
        assertEquals("Invalid public key in URI", error.message)
        assertThrows(IllegalArgumentException::class.java) { NostrConnectUri.parse("nostrconnect://abc?relay=x") { true } }
    }

    // The client compares the secret it gets back with the one it sent (issue #526).
    @Test
    fun `the secret comes back byte for byte`() {
        assertEquals("XQUdha4rjh_gjAgHN8X5yg==", parse("secret=XQUdha4rjh_gjAgHN8X5yg==").secret)
        assertEquals("YQ==Zm9v", parse("secret=YQ==Zm9v").secret)
        assertEquals("a+b", parse("secret=a%2Bb").secret)
        assertEquals("", parse("secret=").secret)
    }

    @Test
    fun `relays are decoded and normalized, unusable ones skipped`() {
        val uri = parse("relay=wss%3A%2F%2Frelay.primal.net&relay=${enc("wss://nos.lol")}&relay=not%20a%20relay")
        assertEquals(listOf("wss://relay.primal.net/", "wss://nos.lol/"), uri.relays.map { it.url })
    }

    @Test
    fun `a name encoded with plus for space shows its space, a literal plus survives`() {
        assertEquals("My App", parse("name=${enc("My App")}").name)
        assertEquals("C++ Dev", parse("name=${enc("C++ Dev")}").name)
    }

    @Test
    fun `metadata containing an encoded ampersand stays one parameter and overrides what it sets`() {
        val metadata = """{"name":"Meta App","url":"https://x?a=1&b=2","image":" https://x/i.png "}"""
        val uri = parse("name=Uri%20App&url=https%3A%2F%2Furi&metadata=${enc(metadata)}")
        assertEquals("Meta App", uri.name)
        assertEquals("https://x?a=1&b=2", uri.url)
        assertEquals("https://x/i.png", uri.image)
    }

    @Test
    fun `blank or malformed metadata keeps what the URI says`() {
        assertEquals("Uri App", parse("name=Uri%20App&metadata=${enc("""{"name":""}""")}").name)
        val malformed = parse("name=Uri%20App&secret=s1&metadata=${enc("{not json")}")
        assertEquals("Uri App", malformed.name)
        assertEquals("s1", malformed.secret)
    }

    @Test
    fun `the client name is sanitized, also from metadata`() {
        assertEquals("Evil App", parse("metadata=${enc("""{"name":"Evil\nApp"}""")}").name.replace(Regex("\\s+"), " "))
        assertTrue(parse("name=${enc("x".repeat(500))}").name.length <= APP_NAME_MAX)
    }

    @Test
    fun `permissions keep their kinds, and those that grant nothing knowable are dropped`() {
        val uri = parse("perms=${enc("nip04_encrypt,sign_event:1,sign_event,nip:44,nip:99,nip,,get_public_key")}&metadata=${enc("""{"perms":"nip44_decrypt"}""")}")
        assertEquals(
            listOf(
                RequestedPermission("nip04_encrypt", null),
                RequestedPermission("sign_event", 1),
                RequestedPermission("nip", 44),
                RequestedPermission("get_public_key", null),
                RequestedPermission("nip44_decrypt", null),
            ),
            uri.permissions,
        )
    }

    // The bunker connect's third parameter is read the same way (it used to differ per app).
    @Test
    fun `a requested-permissions list drops what grants nothing, and a malformed kind drops its entry`() {
        assertEquals(
            listOf(
                RequestedPermission("sign_event", 1),
                RequestedPermission("nip44_encrypt", null),
                RequestedPermission("nip", 4),
                RequestedPermission("nip44v3_encrypt", 7),
            ),
            parseRequestedPermissions(" sign_event : 1 ,sign_event, ,nip44_encrypt,nip:4,nip:99999,nip44v3_encrypt:abc,nip44v3_encrypt:7,sign_event:,") { it in knownNips },
        )
        assertEquals(emptyList<RequestedPermission>(), parseRequestedPermissions("") { true })
        // In a URI too: "abc" is not "every kind".
        assertEquals(emptyList<RequestedPermission>(), parse("perms=${enc("nip44v3_decrypt:abc")}").permissions)
    }

    @Test
    fun `parameters without a value or unknown ones are ignored`() {
        val uri = parse("flag&&foo=bar&secret=s")
        assertEquals("s", uri.secret)
        assertTrue(uri.relays.isEmpty())
    }
}
