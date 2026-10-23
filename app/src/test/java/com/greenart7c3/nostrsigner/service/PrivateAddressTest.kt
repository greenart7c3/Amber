package com.greenart7c3.nostrsigner.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateAddressTest {
    @Test
    fun `local and private-range hosts skip Tor`() {
        for (url in listOf(
            "ws://localhost:7777",
            "ws://127.0.0.1:4869/",
            "http://127.0.0.2/avatar.png",
            "wss://192.168.1.5/",
            "https://172.16.0.1/dav/",
            "ws://172.31.255.255",
            "192.168.1.5:7777",
            "localhost",
        )) {
            assertTrue(url, PrivateAddress.isPrivate(url))
        }
    }

    @Test
    fun `public hosts whose url only contains a private marker still use Tor`() {
        for (url in listOf(
            "https://attacker.example/pixel.png?localhost",
            "https://attacker.example/p.png?x=127.0.0.1",
            "https://localhost.attacker.example/pixel.png",
            "https://attacker.example/192.168.1.1.png",
            "https://172.16.attacker.example/x",
            "wss://attacker.example/localhost",
            "https://attacker.example\\@192.168.0.1/p.png",
            "https://192.168.0.1@attacker.example/",
            "https://dav.attacker.example/remote.php/localhost/",
        )) {
            assertFalse(url, PrivateAddress.isPrivate(url))
        }
    }

    @Test
    fun `public and malformed addresses are not private`() {
        for (url in listOf(
            "wss://relay.damus.io/",
            "https://172.32.0.1/",
            "https://172.15.0.1/",
            "https://192.169.0.1/",
            "http://999.168.0.1/",
            "",
            "not a url",
        )) {
            assertFalse(url, PrivateAddress.isPrivate(url))
        }
    }
}
