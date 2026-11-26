package com.greenart7c3.nostrsigner.common

import org.junit.Assert.assertEquals
import org.junit.Test

class RememberUntilTest {
    private val now = 1_000_000L

    @Test
    fun `each remember choice lasts as long as its label says`() {
        val oneMinute = 1
        val fiveMinutes = 2
        val tenMinutes = 3
        val oneHour = 5
        val oneDay = 6
        val oneWeek = 7
        assertEquals(now + 60, rememberUntil(oneMinute, now))
        assertEquals(now + 300, rememberUntil(fiveMinutes, now))
        assertEquals(now + 600, rememberUntil(tenMinutes, now))
        assertEquals(now + 3600, rememberUntil(oneHour, now))
        assertEquals(now + 86_400, rememberUntil(oneDay, now))
        assertEquals(now + 604_800, rememberUntil(oneWeek, now))
    }

    @Test
    fun `always is forever, and never or an unknown code is not remembered`() {
        assertEquals(REMEMBER_FOREVER, rememberUntil(4, now))
        assertEquals(0L, rememberUntil(0, now))
        assertEquals(0L, rememberUntil(99, now))
        assertEquals(0L, rememberUntil(-1, now))
    }
}
