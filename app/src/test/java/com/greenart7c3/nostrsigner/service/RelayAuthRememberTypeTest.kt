package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.ui.RememberType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F-15: a relay-less kind-22242 request approved with the SPECIFIC scope must
 * not be remembered — the blank relay reads as the all-relays wildcard in the
 * permission lookups, so remembering it would auto-sign relay auth for every
 * relay instead of "this relay only".
 */
class RelayAuthRememberTypeTest {
    @Test
    fun `blank relay with specific scope is downgraded to just this time`() {
        assertEquals(
            RememberType.NEVER,
            relayAuthRememberType(allRelays = false, relayUrl = "", rememberType = RememberType.ALWAYS),
        )
        assertEquals(
            RememberType.NEVER,
            relayAuthRememberType(allRelays = false, relayUrl = "", rememberType = RememberType.ONE_DAY),
        )
    }

    @Test
    fun `all relays scope keeps the remember choice`() {
        assertEquals(
            RememberType.ALWAYS,
            relayAuthRememberType(allRelays = true, relayUrl = "", rememberType = RememberType.ALWAYS),
        )
        assertEquals(
            RememberType.ONE_HOUR,
            relayAuthRememberType(allRelays = true, relayUrl = "victim.example.org", rememberType = RememberType.ONE_HOUR),
        )
    }

    @Test
    fun `explicit relay with specific scope keeps the remember choice`() {
        assertEquals(
            RememberType.ALWAYS,
            relayAuthRememberType(allRelays = false, relayUrl = "granted.example.com", rememberType = RememberType.ALWAYS),
        )
    }

    @Test
    fun `never stays never regardless of relay`() {
        assertEquals(
            RememberType.NEVER,
            relayAuthRememberType(allRelays = false, relayUrl = "granted.example.com", rememberType = RememberType.NEVER),
        )
    }
}
