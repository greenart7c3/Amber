package com.greenart7c3.nostrsigner.common

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryTest {

    @Test
    fun `retryWithBackoff returns true when block succeeds on first attempt`() = runBlocking {
        var calls = 0
        val result = retryWithBackoff(
            maxRetries = 3,
            initialDelay = 1.milliseconds,
            maxDelay = 4.milliseconds,
        ) {
            calls++
            true
        }
        assertTrue(result)
        assertEquals(1, calls)
    }

    @Test
    fun `retryWithBackoff retries and returns true on eventual success`() = runBlocking {
        var calls = 0
        val result = retryWithBackoff(
            maxRetries = 5,
            initialDelay = 1.milliseconds,
            maxDelay = 4.milliseconds,
        ) {
            calls++
            calls >= 3
        }
        assertTrue(result)
        assertEquals(3, calls)
    }

    @Test
    fun `retryWithBackoff returns false when all retries fail`() = runBlocking {
        var calls = 0
        val result = retryWithBackoff(
            maxRetries = 3,
            initialDelay = 1.milliseconds,
            maxDelay = 4.milliseconds,
        ) {
            calls++
            false
        }
        assertFalse(result)
        assertEquals(3, calls)
    }

    @Test
    fun `retryWithBackoff with maxRetries 1 calls block exactly once on failure`() = runBlocking {
        var calls = 0
        val result = retryWithBackoff(
            maxRetries = 1,
            initialDelay = 1.milliseconds,
            maxDelay = 4.milliseconds,
        ) {
            calls++
            false
        }
        assertFalse(result)
        assertEquals(1, calls)
    }

    @Test
    fun `retryWithBackoff with maxRetries 1 returns true on success`() = runBlocking {
        val result = retryWithBackoff(
            maxRetries = 1,
            initialDelay = 1.milliseconds,
            maxDelay = 4.milliseconds,
        ) { true }
        assertTrue(result)
    }

    @Test
    fun `retryWithBackoff does not exceed maxRetries attempts`() = runBlocking {
        val maxRetries = 4
        var calls = 0
        retryWithBackoff(
            maxRetries = maxRetries,
            initialDelay = 1.milliseconds,
            maxDelay = 4.milliseconds,
        ) {
            calls++
            false
        }
        assertEquals(maxRetries, calls)
    }

    @Test
    fun `a single try runs at once, without the backoff wait`() = runBlocking {
        var calls = 0
        val elapsed = measureTime {
            assertFalse(
                retryWithBackoff(maxRetries = UNVERIFIED_REPLY_ATTEMPTS, initialDelay = 10.seconds) {
                    calls++
                    false
                },
            )
        }
        assertEquals(1, calls)
        assertTrue(elapsed < 5.seconds)
    }
}
