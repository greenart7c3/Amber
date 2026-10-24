package com.greenart7c3.nostrsigner.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The batch screens let a relay auth for a relay outside the auth whitelist be
 * approved. Every path now decides it with the same rule.
 */
class AuthWhitelistRuleTest {
    private val whitelist = listOf("relay.example.com", "nos.lol")

    @Test
    fun aRelayOutsideANonEmptyWhitelistIsBlocked() {
        assertTrue(isBlockedByAuthWhitelist(22242, "evil.example.net", whitelist))
    }

    @Test
    fun aWhitelistedRelayIsNotBlocked() {
        assertFalse(isBlockedByAuthWhitelist(22242, "nos.lol", whitelist))
    }

    @Test
    fun anEmptyWhitelistBlocksNothing() {
        assertFalse(isBlockedByAuthWhitelist(22242, "evil.example.net", emptyList()))
    }

    @Test
    fun onlyRelayAuthIsAffected() {
        assertFalse(isBlockedByAuthWhitelist(1, "evil.example.net", whitelist))
    }
}
