package com.greenart7c3.nostrsigner.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderRateLimiterTest {
    private val start = 1_000_000L

    @Test
    fun dropsBurstsAndRefillsSteadily() {
        val limiter = SenderRateLimiter(capacity = 3, windowSeconds = 60, maxSenders = 4, aggregateCapacity = 100)
        val sender = "wss://relay|senderpubkey"

        // A fresh sender gets its whole budget at once, then drops the excess.
        repeat(3) { assertTrue("within budget", limiter.allow(sender, start)) }
        assertFalse("burst beyond budget dropped", limiter.allow(sender, start))

        // Steady refill: half a window later exactly one token is back.
        assertTrue(limiter.allow(sender, start + 30_000))
        assertFalse(limiter.allow(sender, start + 30_000))

        // A full window restores the whole budget.
        assertTrue(limiter.allow(sender, start + 90_000))
    }

    @Test
    fun boundsItsMap() {
        val limiter = SenderRateLimiter(capacity = 3, windowSeconds = 60, maxSenders = 4, aggregateCapacity = 100)
        repeat(6) { limiter.allow("wss://relay|sender-$it", start) }
        assertTrue("map bounded", limiter.trackedSenders <= 4)
    }

    @Test
    fun freshKeysCannotExceedTheAggregateBudget() {
        val limiter = SenderRateLimiter(capacity = 10, windowSeconds = 60, aggregateCapacity = 20)
        val allowed = (0 until 500).count { limiter.allow("wss://relay|fresh-$it", start) }
        assertEquals("one event per fresh key, capped in total", 20, allowed)

        // The shared budget refills too.
        assertTrue(limiter.allow("wss://relay|fresh-later", start + 3_000))
    }

    @Test
    fun oneNoisySenderDoesNotUseUpTheSharedBudget() {
        val limiter = SenderRateLimiter(capacity = 10, windowSeconds = 60, aggregateCapacity = 20)
        repeat(1000) { limiter.allow("wss://relay|noisy", start) }
        // The noisy sender only ever spent its own 10 tokens of the shared 20.
        val others = (0 until 10).count { limiter.allow("wss://relay|newcomer-$it", start) }
        assertEquals(10, others)
    }
}
