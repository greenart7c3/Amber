package com.greenart7c3.nostrsigner.cli

import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrisTest {
    private val pubKey = "0123456789abcdef".repeat(4)
    private val clientPubKey = "fedcba9876543210".repeat(4)

    private fun params(uri: String): Map<String, String> = uri.substringAfter('?').split('&').associate {
        it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "utf-8")
    }

    @Test
    fun buildsANostrConnectUriQuartzParsesBack() {
        val uri = buildNostrConnectUri(
            clientPubKey = clientPubKey,
            relays = listOf("wss://relay.nip46.com/", "ws://127.0.0.1:7777/"),
            secret = "s3cr3t",
            name = "Amber CLI + scripts",
            perms = "sign_event:1,nip44_encrypt",
        )
        // Quartz parses what it builds, so the CLI and an Amber reading the URI
        // agree on every value.
        val parsed = NostrConnectURI.parseNostrConnect(uri)!!
        assertEquals(clientPubKey, parsed.clientPubKey)
        assertEquals(setOf("wss://relay.nip46.com/", "ws://127.0.0.1:7777/"), parsed.relays.map { it.url }.toSet())
        assertEquals("s3cr3t", parsed.secret)
        assertEquals("Amber CLI + scripts", parsed.name)
        assertEquals("sign_event:1,nip44_encrypt", parsed.perms)
    }

    @Test
    fun omitsTheOptionalParametersWhenBlank() {
        val uri = buildNostrConnectUri(clientPubKey, listOf("wss://relay.example/"), "s", name = "", perms = "")
        val parsed = NostrConnectURI.parseNostrConnect(uri)!!
        assertEquals(null, parsed.name)
        assertEquals(null, parsed.perms)
        assertEquals(params(uri)["relay"], "wss://relay.example/")
    }

    @Test
    fun namesWithReservedCharactersSurvive() {
        val name = "My App & tools + more"
        val uri = buildNostrConnectUri(clientPubKey, listOf("wss://relay.example/"), "s", name, perms = "")
        // Amber decodes each query value with URLDecoder ('+' is a space).
        assertEquals(name, params(uri)["name"])
        assertEquals(name, NostrConnectURI.parseNostrConnect(uri)!!.name)
    }

    @Test
    fun secretsAreRandomHex() {
        val first = randomSecret()
        assertEquals(32, first.length)
        assertTrue(first.matches(Regex("[0-9a-f]{32}")))
        assertTrue(first != randomSecret())
    }

    @Test
    fun theQrCodeRendersTheUri() {
        val out = java.io.ByteArrayOutputStream()
        printQrCode(java.io.PrintStream(out, true, "utf-8"), "nostrconnect://$pubKey?relay=wss://relay.example/&secret=x")
        val text = out.toString("utf-8")
        assertTrue(text.contains("\u2588"))
        assertTrue(text.lines().size > 10)
    }
}
