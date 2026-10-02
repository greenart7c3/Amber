package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.LocalRelays
import com.greenart7c3.nostrsigner.desktop.core.RelayHttpClients
import com.greenart7c3.nostrsigner.desktop.core.TorMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayNetworkTest {
    @Test
    fun hostIsExtractedFromUrlsAndBareInput() {
        assertEquals("relay.example.com", LocalRelays.hostOf("wss://relay.example.com/path?x=1"))
        assertEquals("192.168.1.5", LocalRelays.hostOf("192.168.1.5:4869"))
        assertEquals("::1", LocalRelays.hostOf("ws://[::1]:7777/"))
        assertEquals("localhost", LocalRelays.hostOf("ws://LocalHost:8080"))
        assertNull(LocalRelays.hostOf("ws://"))
    }

    @Test
    fun localRelaysAreDetected() {
        listOf(
            "ws://localhost:7777",
            "ws://127.0.0.1:7777/",
            "ws://10.0.0.2",
            "ws://172.16.5.4",
            "ws://172.31.255.1",
            "ws://192.168.1.5:4869",
            "ws://169.254.10.10",
            "ws://100.101.102.103",
            "ws://[::1]:4869",
            "ws://[fd12:3456::1]",
            "ws://[fe80::1]",
            "ws://umbrel.local",
            "ws://relay.home.arpa",
            "ws://nas.lan:4848",
        ).forEach { assertTrue(it, LocalRelays.isLocal(it)) }
    }

    @Test
    fun publicRelaysAreNotLocal() {
        listOf(
            "wss://relay.damus.io",
            // Old substring matching flagged these as private.
            "wss://relay10.example.com",
            "wss://nostr.localhost.example.com",
            "wss://172.32.0.1",
            "wss://8.8.8.8",
            "wss://100.128.0.1",
            "wss://[2001:db8::1]",
            "ws://abcdef.onion",
        ).forEach { assertFalse(it, LocalRelays.isLocal(it)) }
    }

    @Test
    fun insecureWarningOnlyForPublicCleartext() {
        assertTrue(LocalRelays.isInsecure("ws://relay.example.com"))
        assertFalse(LocalRelays.isInsecure("wss://relay.example.com"))
        assertFalse(LocalRelays.isInsecure("ws://abcdef.onion"))
        assertFalse(LocalRelays.isInsecure("ws://192.168.1.5:4869"))
        assertFalse(LocalRelays.isInsecure("relay.example.com"))
    }

    @Test
    fun torRoutingSendsRemoteRelaysThroughTheProxy() {
        val remote = "wss://relay.example.com/"
        assertNull(RelayHttpClients.socksPortFor(remote, TorMode.DISABLED, 9050, 0))
        assertEquals(9150, RelayHttpClients.socksPortFor(remote, TorMode.EXTERNAL, 9150, 0))
        assertEquals(37123, RelayHttpClients.socksPortFor(remote, TorMode.BUILTIN, 9050, 37123))
        assertEquals(9150, RelayHttpClients.socksPortFor("ws://abcdef.onion/", TorMode.EXTERNAL, 9150, 0))
    }

    @Test
    fun builtinTorFailsClosedBeforeItIsUp() {
        // Port 0 refuses every connection instead of falling back to the clearnet.
        assertEquals(0, RelayHttpClients.socksPortFor("wss://relay.example.com/", TorMode.BUILTIN, 9050, 0))
    }

    @Test
    fun localRelaysBypassTor() {
        assertNull(RelayHttpClients.socksPortFor("ws://192.168.1.5:4869/", TorMode.BUILTIN, 9050, 37123))
        assertNull(RelayHttpClients.socksPortFor("ws://localhost:7777/", TorMode.EXTERNAL, 9050, 0))
    }

    @Test
    fun socksProbeRejectsInvalidPorts() {
        assertFalse(RelayHttpClients.isSocksProxyAlive(0))
        assertFalse(RelayHttpClients.isSocksProxyAlive(70000))
    }
}
