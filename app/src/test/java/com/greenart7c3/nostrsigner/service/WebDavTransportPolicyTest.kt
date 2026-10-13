package com.greenart7c3.nostrsigner.service

import org.junit.Assert.assertThrows
import org.junit.Test

class WebDavTransportPolicyTest {
    @Test
    fun `credentials cannot be sent to cleartext or ambiguous URLs`() {
        listOf("http://backup.example", "https://user:pass@backup.example", "https://backup.example?token=secret", "https://backup.example#fragment").forEach {
            assertThrows(IllegalArgumentException::class.java) { WebDavTransportPolicy.requireSecureServerUrl(it) }
        }
    }

    @Test
    fun `secure server with a base path is accepted`() {
        WebDavTransportPolicy.requireSecureServerUrl("https://backup.example:8443/remote.php/dav/files/user")
    }
}
