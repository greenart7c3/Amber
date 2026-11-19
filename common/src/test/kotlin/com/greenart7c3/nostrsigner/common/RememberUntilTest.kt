package com.greenart7c3.nostrsigner.common

import org.junit.Assert.assertEquals
import org.junit.Test

class RememberUntilTest {
    private val now = 1_000_000L

    @Test
    fun `each remember choice lasts as long as its label says`() {
        // Screen codes: 1 one minute, 2 five minutes, 3 ten minutes, 5 one hour, 6 one day, 7 one week.
        assertEquals(now + 60, rememberUntil(1, now))
        assertEquals(now + 300, rememberUntil(2, now))
        assertEquals(now + 600, rememberUntil(3, now))
        assertEquals(now + 3600, rememberUntil(5, now))
        assertEquals(now + 86_400, rememberUntil(6, now))
        assertEquals(now + 604_800, rememberUntil(7, now))
    }

    @Test
    fun `always is forever, and never or an unknown code is not remembered`() {
        assertEquals(REMEMBER_FOREVER, rememberUntil(4, now))
        assertEquals(0L, rememberUntil(0, now))
        assertEquals(0L, rememberUntil(99, now))
        assertEquals(0L, rememberUntil(-1, now))
    }
}
