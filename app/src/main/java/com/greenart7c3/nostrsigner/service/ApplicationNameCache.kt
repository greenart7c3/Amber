package com.greenart7c3.nostrsigner.service

import androidx.collection.LruCache

/**
 * Bounded cache of "$npub-$key" -> display name. Previously an unbounded
 * ConcurrentHashMap; long-running sessions with many connecting apps were
 * leaking memory here. The size cap is conservative — names are short, but
 * the cache grows with every external app the user has ever interacted with.
 */
object ApplicationNameCache {
    private const val MAX_ENTRIES = 256

    private val cache = LruCache<String, String>(MAX_ENTRIES)

    operator fun get(key: String): String? = synchronized(cache) { cache.get(key) }

    operator fun set(key: String, value: String) {
        synchronized(cache) { cache.put(key, value) }
    }

    fun clearForAccount(npub: String) {
        synchronized(cache) {
            val toRemove = cache.snapshot().keys.filter { it.startsWith("$npub-") }
            toRemove.forEach { cache.remove(it) }
        }
    }
}
