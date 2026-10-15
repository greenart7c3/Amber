package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.models.TorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavTransportPolicyTest {
    @Test
    fun `credentials cannot be sent to cleartext or ambiguous URLs`() {
        listOf(
            "http://backup.example",
            "https://user:pass@backup.example",
            "https://backup.example?token=secret",
            "https://backup.example#fragment",
            "not a url at all",
        ).forEach {
            assertThrows(IllegalArgumentException::class.java) { WebDavTransportPolicy.requireSecureServerUrl(it) }
        }
    }

    @Test
    fun `urls okHttp accepts keep working`() {
        // The java.net.URI-based check rejected all of these with a misleading
        // "requires an HTTPS server URL" error even though requests worked.
        listOf(
            "https://backup.example:8443/remote.php/dav/files/user",
            "https://backup.example/dav ",
            " https://backup.example/dav",
            "HTTPS://backup.example/dav",
            "https://my_nas.local/dav",
            "https://münchen.example/dav",
            "https://backup.example/remote.php/dav/files/John Doe",
        ).forEach {
            WebDavTransportPolicy.requireSecureServerUrl(it)
        }
    }

    @Test
    fun `file names are single encoded segments and the final url stays policy-clean`() {
        val url = WebDavTransportPolicy.buildFileUrl("https://backup.example/dav", "amber.ncryptsec")
        assertEquals("https://backup.example/dav/amber.ncryptsec", url.toString())

        // Query/fragment injection via the restore text field must be encoded,
        // not interpreted.
        val injected = WebDavTransportPolicy.buildFileUrl("https://backup.example/dav", "x?token=secret#f")
        assertNull(injected.query)
        assertNull(injected.fragment)
        assertTrue(injected.encodedPath.endsWith("/x%3Ftoken%3Dsecret%23f"))

        listOf("", "..", ".", "a/b", "a\\b").forEach {
            assertThrows(IllegalArgumentException::class.java) { WebDavTransportPolicy.buildFileUrl("https://backup.example/dav", it) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebDavTransportPolicy.buildFileUrl("http://backup.example/dav", "amber.ncryptsec")
        }
    }

    @Test
    fun `private addresses bypass Tor, everything else follows the setting`() {
        assertTrue(webDavUsesProxy(isPrivateAddress = false, torMode = TorMode.BUILTIN))
        assertTrue(webDavUsesProxy(isPrivateAddress = false, torMode = TorMode.ORBOT))
        assertFalse(webDavUsesProxy(isPrivateAddress = true, torMode = TorMode.BUILTIN))
        assertFalse(webDavUsesProxy(isPrivateAddress = false, torMode = TorMode.DISABLED))
    }
}
