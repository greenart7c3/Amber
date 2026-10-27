package com.greenart7c3.nostrsigner.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZapstoreVersionTest {
    private fun newer(version: String, current: String = "6.6.9") = ZapstoreUpdater.isNewerVersion(version, current)

    @Test
    fun comparesNumericParts() {
        assertTrue(newer("6.6.10"))
        assertTrue(newer("6.7.0"))
        assertTrue(newer("7"))
        assertTrue(newer("6.6.9.1"))
        assertFalse(newer("6.6.9"))
        assertFalse(newer("6.6.8"))
        assertFalse(newer("6.6"))
        assertFalse(newer("5.99.99"))
    }

    @Test
    fun ignoresSuffixes() {
        assertFalse(newer("6.6.9-DEBUG", "6.6.9-DEBUG"))
        assertTrue(newer("6.6.10-rc1", "6.6.9-DEBUG"))
        assertFalse(newer("6.6.9-rc1"))
    }

    // The old fallback compared the strings, where e.g. "x" or "v1.0" sort
    // above "6.6.9", so an unparsable version was offered as an update.
    @Test
    fun unparsableVersionsAreNeverNewer() {
        listOf("x", "v1.0", "9.a", "7..0", "+7.0.0", "", "7.0.0 ", "99999999999.0.0").forEach {
            assertFalse(it, newer(it))
        }
        assertFalse("unparsable current", newer("7.0.0", "dev"))
    }
}
