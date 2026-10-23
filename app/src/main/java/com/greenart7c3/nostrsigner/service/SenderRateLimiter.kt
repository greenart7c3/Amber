package com.greenart7c3.nostrsigner.service

/**
 * Token buckets limiting NIP-46 events from senders that are not a stored
 * connection: [capacity] events per [windowSeconds] for each sender, refilled
 * steadily, and [aggregateCapacity] per window across all of them. Every
 * request costs a Room write, a decrypt, a parse and usually a signed reply,
 * so a flood must cost the sender silence instead of the user's battery.
 *
 * The per-sender bucket stops one key from using up the shared budget. The
 * aggregate bucket is what bounds the total work, since a fresh key costs an
 * attacker nothing. Keyed by `(relay url, sender pubkey)`; the map is bounded,
 * evicting the oldest sender past [maxSenders] keys.
 *
 * Port of the desktop `SenderRateLimiter` (desktop/.../core/BunkerEngine.kt).
 */
internal class SenderRateLimiter(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val windowSeconds: Long = DEFAULT_WINDOW_SECONDS,
    private val maxSenders: Int = DEFAULT_MAX_SENDERS,
    private val aggregateCapacity: Int = DEFAULT_AGGREGATE_CAPACITY,
) {
    private class Bucket(var tokens: Double, var updatedAtMs: Long)

    private val buckets = object : LinkedHashMap<String, Bucket>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bucket>?): Boolean = size > maxSenders
    }
    private var aggregate: Bucket? = null

    /** Whether one more event from [key] is allowed at [nowMs]; excess is dropped silently. */
    @Synchronized
    fun allow(key: String, nowMs: Long): Boolean {
        val bucket = buckets.getOrPut(key) { Bucket(capacity.toDouble(), nowMs) }
        if (!take(bucket, capacity, nowMs)) return false
        val shared = aggregate ?: Bucket(aggregateCapacity.toDouble(), nowMs).also { aggregate = it }
        return take(shared, aggregateCapacity, nowMs)
    }

    private fun take(bucket: Bucket, bucketCapacity: Int, nowMs: Long): Boolean {
        val refillPerMs = bucketCapacity.toDouble() / (windowSeconds * 1000.0)
        val tokens = (bucket.tokens + (nowMs - bucket.updatedAtMs) * refillPerMs).coerceIn(0.0, bucketCapacity.toDouble())
        bucket.updatedAtMs = nowMs
        return if (tokens >= 1.0) {
            bucket.tokens = tokens - 1.0
            true
        } else {
            bucket.tokens = tokens
            false
        }
    }

    val trackedSenders: Int
        @Synchronized get() = buckets.size

    @Synchronized
    internal fun resetForTest() {
        buckets.clear()
        aggregate = null
    }

    companion object {
        const val DEFAULT_CAPACITY = 10
        const val DEFAULT_WINDOW_SECONDS = 60L
        const val DEFAULT_MAX_SENDERS = 4096
        const val DEFAULT_AGGREGATE_CAPACITY = 60
    }
}
