package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.common.SignerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentRateLimiterTest {
    private var now = 0L
    private var config = IntentRateLimiter.Config(enabled = true, maxPerWindow = 3, windowSeconds = 10)
    private val limiter = IntentRateLimiter(config = { config }, clock = { now })

    private fun key(pkg: String = "com.example.app", type: String = SignerType.SIGN_EVENT.name, kind: Int? = null) = IntentRateLimiter.BucketKey(pkg, type, kind)

    @Test
    fun `allows requests up to the limit and blocks after`() {
        val k = key()
        repeat(3) { assertTrue(limiter.checkAndRecord(k)) }
        assertFalse(limiter.checkAndRecord(k))
        assertFalse(limiter.checkAndRecord(k))
    }

    @Test
    fun `sliding window drops expired timestamps`() {
        val k = key()
        repeat(3) { assertTrue(limiter.checkAndRecord(k)) }
        assertFalse(limiter.checkAndRecord(k))

        now += 10_000L
        assertTrue(limiter.checkAndRecord(k))
    }

    @Test
    fun `different types use separate buckets`() {
        val signEvent = key(type = SignerType.SIGN_EVENT.name)
        val getPubKey = key(type = SignerType.GET_PUBLIC_KEY.name)

        repeat(3) { assertTrue(limiter.checkAndRecord(signEvent)) }
        assertFalse(limiter.checkAndRecord(signEvent))

        assertTrue(limiter.checkAndRecord(getPubKey))
    }

    @Test
    fun `different event kinds use separate buckets for SIGN_EVENT`() {
        val kind1 = key(kind = 1)
        val kind22242 = key(kind = 22242)

        repeat(3) { assertTrue(limiter.checkAndRecord(kind1)) }
        assertFalse(limiter.checkAndRecord(kind1))

        repeat(3) { assertTrue(limiter.checkAndRecord(kind22242)) }
        assertFalse(limiter.checkAndRecord(kind22242))
    }

    @Test
    fun `different callers use separate buckets`() {
        val appA = key(pkg = "com.a")
        val appB = key(pkg = "com.b")

        repeat(3) { assertTrue(limiter.checkAndRecord(appA)) }
        assertFalse(limiter.checkAndRecord(appA))
        assertTrue(limiter.checkAndRecord(appB))
    }

    @Test
    fun `unknown package bucket has a tighter limit`() {
        val unknown = key(pkg = IntentRateLimiter.UNKNOWN_PACKAGE)
        repeat(3) { assertTrue(limiter.checkAndRecord(unknown)) }
        assertFalse(limiter.checkAndRecord(unknown))
    }

    @Test
    fun `disabled flag short-circuits to allow everything`() {
        config = IntentRateLimiter.Config(enabled = false, maxPerWindow = 1, windowSeconds = 10)
        val k = key()
        repeat(100) { assertTrue(limiter.checkAndRecord(k)) }
    }

    @Test
    fun `toast shown at most once per window per key`() {
        val k = key()
        assertTrue(limiter.shouldShowToast(k))
        assertFalse(limiter.shouldShowToast(k))

        now += 10_000L
        assertTrue(limiter.shouldShowToast(k))
    }

    @Test
    fun `toast throttling is per key`() {
        val a = key(pkg = "com.a")
        val b = key(pkg = "com.b")
        assertTrue(limiter.shouldShowToast(a))
        assertTrue(limiter.shouldShowToast(b))
        assertFalse(limiter.shouldShowToast(a))
        assertFalse(limiter.shouldShowToast(b))
    }

    @Test
    fun `bucket key equality ignores reference identity`() {
        val a = IntentRateLimiter.BucketKey("pkg", SignerType.SIGN_EVENT.name, 1)
        val b = IntentRateLimiter.BucketKey("pkg", SignerType.SIGN_EVENT.name, 1)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
