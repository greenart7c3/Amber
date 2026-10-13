package com.greenart7c3.nostrsigner.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrustScoreUrlPolicyTest {
    @Test
    fun `private or credential bearing relay endpoints are excluded`() {
        listOf("wss://user:pass@relay.example", "wss://relay.example?token=secret", "wss://relay.example/private", "ws://127.0.0.1:8080", "ws://192.168.1.5", "ws://[::1]", "ws://relay", "ws://hidden.onion", "wss://relay.local").forEach {
            assertNull(it, TrustScoreUrlPolicy.publicRelayUrl(it))
        }
    }

    @Test
    fun `public root relay is normalized without sensitive components`() {
        assertEquals("wss://relay.example:443", TrustScoreUrlPolicy.publicRelayUrl("wss://relay.example:443/"))
    }
}
