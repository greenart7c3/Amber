package com.greenart7c3.nostrsigner.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrustScoreUrlPolicyTest {
    @Test
    fun `private or credential bearing relay endpoints are excluded`() {
        listOf(
            "wss://user:pass@relay.example",
            "wss://relay.example?token=secret",
            "wss://relay.example#frag",
            "ws://127.0.0.1:8080",
            "ws://192.168.1.5",
            "ws://[::1]",
            "ws://relay",
            "ws://hidden.onion",
            "ws://myrelay.i2p",
            "ws://myrelay.loki",
            "wss://relay.local",
            "wss://nas.lan",
            "wss://relay.home.arpa",
            "wss://relay.home",
        ).forEach {
            assertNull(it, TrustScoreUrlPolicy.publicRelayUrl(it))
        }
    }

    @Test
    fun `public relay is normalized to one canonical url`() {
        assertEquals("wss://relay.example", TrustScoreUrlPolicy.publicRelayUrl("wss://relay.example"))
        assertEquals("wss://relay.example", TrustScoreUrlPolicy.publicRelayUrl("wss://relay.example:443/"))
        assertEquals("wss://relay.example", TrustScoreUrlPolicy.publicRelayUrl("WSS://Relay.Example"))
        assertEquals("ws://relay.example:8080", TrustScoreUrlPolicy.publicRelayUrl("ws://relay.example:8080"))
    }

    @Test
    fun `paths are dropped instead of rejecting the relay`() {
        assertEquals("wss://relay.nostr.band", TrustScoreUrlPolicy.publicRelayUrl("wss://relay.nostr.band/all"))
        assertEquals("wss://nostr.wine", TrustScoreUrlPolicy.publicRelayUrl("wss://nostr.wine/inbox"))
    }
}
