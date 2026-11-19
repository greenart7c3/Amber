package com.greenart7c3.nostrsigner.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Nip46FreshnessTest {
    private val now = 1_800_000_000L

    @Test
    fun `a request within five minutes either way is fresh`() {
        assertNull(nip46StaleReason("e", now, null, now))
        assertNull(nip46StaleReason("e", now - NIP46_FRESHNESS_WINDOW, null, now))
        assertNull(nip46StaleReason("e", now + NIP46_FRESHNESS_WINDOW, null, now))
    }

    @Test
    fun `stale replays and future-dated requests are dropped`() {
        assertEquals("Event e is too old: 301s ago", nip46StaleReason("e", now - 301, null, now))
        assertEquals("Event e is in the future: 301s ahead", nip46StaleReason("e", now + 301, null, now))
    }

    @Test
    fun `an expired request is dropped, an unexpired one kept`() {
        assertEquals("Event e has expired", nip46StaleReason("e", now, now - 1, now))
        assertNull(nip46StaleReason("e", now, now + 60, now))
    }

    @Test
    fun `switch_relays answers the relays as a JSON array, or null for no change`() {
        assertEquals("""["wss://a.example","wss://b.example"]""", switchRelaysResult(listOf("wss://a.example", "wss://b.example")))
        assertNull(switchRelaysResult(emptyList()))
    }
}
