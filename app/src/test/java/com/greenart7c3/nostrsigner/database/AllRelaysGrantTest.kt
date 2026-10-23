package com.greenart7c3.nostrsigner.database

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The approval screens only treated "*" as an all-relays relay-auth grant,
 * while the DAO behind the provider and NIP-46 paths also accepts the blank
 * relay the basic sign policy stores, so the same grant signed silently in the
 * background but prompted in the UI.
 */
class AllRelaysGrantTest {
    @Test
    fun wildcardAndBlankRelaysCoverEveryRelay() {
        assertTrue(isAllRelaysGrant("*"))
        assertTrue(isAllRelaysGrant(""))
        assertTrue(isAllRelaysGrant(null))
    }

    @Test
    fun aSpecificRelayDoesNot() {
        assertFalse(isAllRelaysGrant("relay.example.com"))
        assertFalse(isAllRelaysGrant(" "))
    }
}
