package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.models.SignerType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IntentRateLimiterTest {
    private var now = 0L

    @Before
    fun setUp() {
        IntentRateLimiter.resetForTest()
        IntentRateLimiter.setClockForTest { now }
        IntentRateLimiter.setConfigForTest(
            IntentRateLimiter.Config(enabled = true, maxPerWindow = 3, windowSeconds = 10),
        )
    }

    @After
    fun tearDown() {
        IntentRateLimiter.resetForTest()
    }

    private fun key(pkg: String = "com.example.app", type: String = SignerType.SIGN_EVENT.name, kind: Int? = null) = IntentRateLimiter.BucketKey(pkg, type, kind)

    @Test
    fun `allows requests up to the limit and blocks after`() {
        val k = key()
        repeat(3) { assertTrue(IntentRateLimiter.checkAndRecord(k)) }
        assertFalse(IntentRateLimiter.checkAndRecord(k))
        assertFalse(IntentRateLimiter.checkAndRecord(k))
    }

    @Test
    fun `sliding window drops expired timestamps`() {
        val k = key()
        repeat(3) { assertTrue(IntentRateLimiter.checkAndRecord(k)) }
        assertFalse(IntentRateLimiter.checkAndRecord(k))

        now += 10_000L
        assertTrue(IntentRateLimiter.checkAndRecord(k))
    }

    @Test
    fun `different types use separate buckets`() {
        val signEvent = key(type = SignerType.SIGN_EVENT.name)
        val getPubKey = key(type = SignerType.GET_PUBLIC_KEY.name)

        repeat(3) { assertTrue(IntentRateLimiter.checkAndRecord(signEvent)) }
        assertFalse(IntentRateLimiter.checkAndRecord(signEvent))

        assertTrue(IntentRateLimiter.checkAndRecord(getPubKey))
    }

    @Test
    fun `different event kinds use separate buckets for SIGN_EVENT`() {
        val kind1 = key(kind = 1)
        val kind22242 = key(kind = 22242)

        repeat(3) { assertTrue(IntentRateLimiter.checkAndRecord(kind1)) }
        assertFalse(IntentRateLimiter.checkAndRecord(kind1))

        repeat(3) { assertTrue(IntentRateLimiter.checkAndRecord(kind22242)) }
        assertFalse(IntentRateLimiter.checkAndRecord(kind22242))
    }

    @Test
    fun `different callers use separate buckets`() {
        val appA = key(pkg = "com.a")
        val appB = key(pkg = "com.b")

        repeat(3) { assertTrue(IntentRateLimiter.checkAndRecord(appA)) }
        assertFalse(IntentRateLimiter.checkAndRecord(appA))
        assertTrue(IntentRateLimiter.checkAndRecord(appB))
    }

    @Test
    fun `unknown package bucket has a tighter limit`() {
        val unknown = key(pkg = IntentRateLimiter.UNKNOWN_PACKAGE)
        repeat(3) { assertTrue(IntentRateLimiter.checkAndRecord(unknown)) }
        assertFalse(IntentRateLimiter.checkAndRecord(unknown))
    }

    @Test
    fun `disabled flag short-circuits to allow everything`() {
        IntentRateLimiter.setConfigForTest(
            IntentRateLimiter.Config(enabled = false, maxPerWindow = 1, windowSeconds = 10),
        )
        val k = key()
        repeat(100) { assertTrue(IntentRateLimiter.checkAndRecord(k)) }
    }

    @Test
    fun `toast shown at most once per window per key`() {
        val k = key()
        assertTrue(IntentRateLimiter.shouldShowToast(k))
        assertFalse(IntentRateLimiter.shouldShowToast(k))

        now += 10_000L
        assertTrue(IntentRateLimiter.shouldShowToast(k))
    }

    @Test
    fun `toast throttling is per key`() {
        val a = key(pkg = "com.a")
        val b = key(pkg = "com.b")
        assertTrue(IntentRateLimiter.shouldShowToast(a))
        assertTrue(IntentRateLimiter.shouldShowToast(b))
        assertFalse(IntentRateLimiter.shouldShowToast(a))
        assertFalse(IntentRateLimiter.shouldShowToast(b))
    }

    @Test
    fun `bucket key equality ignores reference identity`() {
        val a = IntentRateLimiter.BucketKey("pkg", SignerType.SIGN_EVENT.name, 1)
        val b = IntentRateLimiter.BucketKey("pkg", SignerType.SIGN_EVENT.name, 1)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
