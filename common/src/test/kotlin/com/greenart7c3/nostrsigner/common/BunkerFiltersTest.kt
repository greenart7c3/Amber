package com.greenart7c3.nostrsigner.common

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class BunkerFiltersTest {
    private val relay1 = NormalizedRelayUrl("wss://relay1.example.com/")
    private val relay2 = NormalizedRelayUrl("wss://relay2.example.com/")

    @Test
    fun `each relay gets one filter with only its own keys`() {
        val filters = bunkerFilters(mapOf(relay1 to setOf("a", "b"), relay2 to setOf("b")), since = 10)

        assertEquals(listOf("a", "b"), filters.getValue(relay1).single().tags?.get("p"))
        assertEquals(listOf("b"), filters.getValue(relay2).single().tags?.get("p"))
        assertEquals(10L, filters.getValue(relay1).single().since)
        assertEquals(2, filters.getValue(relay1).single().limit)
    }

    @Test
    fun `many keys are split into several filters of one REQ`() {
        val keys = (1..250).map { "k$it" }.toSet()

        val filters = bunkerFilters(mapOf(relay1 to keys), since = 0).getValue(relay1)

        assertEquals(listOf(100, 100, 50), filters.map { it.tags?.get("p")?.size })
        assertEquals(keys.toList(), filters.flatMap { it.tags.orEmpty().getValue("p") })
    }

    @Test
    fun `a relay with no keys gets no subscription`() {
        assertEquals(setOf(relay1), bunkerFilters(mapOf(relay1 to setOf("a"), relay2 to emptySet()), since = 0).keys)
    }
}
