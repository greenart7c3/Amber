package com.greenart7c3.nostrsigner.service

import android.content.Intent
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.models.SignerType
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-caller sliding-window rate limiter for incoming `nostrsigner://` intents.
 *
 * Gated only at [com.greenart7c3.nostrsigner.SignerActivity.onCreate] (the "new screen" path).
 * Intents that merge into an already-open approval UI via `onNewIntent` are the legitimate
 * batch flow and are never throttled here.
 */
object IntentRateLimiter {
    const val UNKNOWN_PACKAGE = "<unknown>"
    private const val UNKNOWN_PACKAGE_LIMIT = 5
    private const val CLEANUP_WHEN_SIZE_EXCEEDS = 256
    private const val CLEANUP_STALE_MS = 60L * 60L * 1000L

    data class BucketKey(
        val pkg: String,
        val type: String,
        val kind: Int? = null,
    )

    internal data class Config(
        val enabled: Boolean = true,
        val maxPerWindow: Int = 5,
        val windowSeconds: Int = 30,
    )

    private val buckets = ConcurrentHashMap<BucketKey, ArrayDeque<Long>>()
    private val lastToastAt = ConcurrentHashMap<BucketKey, Long>()

    @Volatile
    private var clock: () -> Long = { System.currentTimeMillis() }

    @Volatile
    private var configOverride: Config? = null

    internal fun setClockForTest(source: () -> Long) {
        clock = source
    }

    internal fun setConfigForTest(config: Config?) {
        configOverride = config
    }

    internal fun resetForTest() {
        buckets.clear()
        lastToastAt.clear()
        clock = { System.currentTimeMillis() }
        configOverride = null
    }

    fun checkAndRecord(key: BucketKey): Boolean {
        val cfg = currentConfig()
        if (!cfg.enabled) return true

        val now = clock()
        val windowMs = cfg.windowSeconds.toLong() * 1000L
        val limit = if (key.pkg == UNKNOWN_PACKAGE) UNKNOWN_PACKAGE_LIMIT else cfg.maxPerWindow

        val deque = buckets.getOrPut(key) { ArrayDeque() }
        val allowed = synchronized(deque) {
            while (deque.isNotEmpty() && deque.first() <= now - windowMs) {
                deque.removeFirst()
            }
            if (deque.size >= limit) {
                false
            } else {
                deque.addLast(now)
                true
            }
        }
        maybeCleanup(now)
        return allowed
    }

    fun shouldShowToast(key: BucketKey): Boolean {
        val cfg = currentConfig()
        val windowMs = cfg.windowSeconds.toLong() * 1000L
        val now = clock()
        val prev = lastToastAt[key]
        return if (prev == null || now - prev >= windowMs) {
            lastToastAt[key] = now
            true
        } else {
            false
        }
    }

    private fun currentConfig(): Config {
        configOverride?.let { return it }
        return try {
            val s = Amber.instance.settings
            Config(s.rateLimitEnabled, s.rateLimitMaxPerWindow, s.rateLimitWindowSeconds)
        } catch (_: Throwable) {
            Config()
        }
    }

    private fun maybeCleanup(now: Long) {
        if (buckets.size <= CLEANUP_WHEN_SIZE_EXCEEDS) return
        val iterator = buckets.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val newest = synchronized(entry.value) { entry.value.lastOrNull() ?: 0L }
            if (now - newest > CLEANUP_STALE_MS) {
                iterator.remove()
                lastToastAt.remove(entry.key)
            }
        }
    }
}

/**
 * Best-effort extractor that derives a [IntentRateLimiter.BucketKey] from a raw [Intent]
 * cheaply enough to run on the UI thread at the top of `onCreate`. Returns `null` when the
 * intent cannot be classified — unclassifiable intents will be rejected downstream anyway.
 */
object IntentRateLimitInspector {
    fun inspect(intent: Intent, callingPackage: String?): IntentRateLimiter.BucketKey? {
        val pkg = callingPackage?.takeIf { it.isNotBlank() } ?: IntentRateLimiter.UNKNOWN_PACKAGE

        val typeStr = intent.extras?.getString("type") ?: extractTypeFromUrl(intent)
        val type = typeStr?.let { parseSignerType(it) } ?: return null

        val kind = if (type == SignerType.SIGN_EVENT) extractEventKind(intent) else null
        return IntentRateLimiter.BucketKey(pkg, type.name, kind)
    }

    private fun parseSignerType(value: String): SignerType? = when (value) {
        "sign_message" -> SignerType.SIGN_MESSAGE
        "sign_event" -> SignerType.SIGN_EVENT
        "get_public_key" -> SignerType.GET_PUBLIC_KEY
        "nip04_encrypt" -> SignerType.NIP04_ENCRYPT
        "nip04_decrypt" -> SignerType.NIP04_DECRYPT
        "nip44_encrypt" -> SignerType.NIP44_ENCRYPT
        "nip44_decrypt" -> SignerType.NIP44_DECRYPT
        "decrypt_zap_event" -> SignerType.DECRYPT_ZAP_EVENT
        else -> null
    }

    private fun extractTypeFromUrl(intent: Intent): String? {
        val decoded = decodeDataSafely(intent) ?: return null
        val query = decoded.substringAfter('?', "")
        if (query.isEmpty()) return null
        for (pair in query.split('&')) {
            val idx = pair.indexOf('=')
            if (idx <= 0) continue
            if (pair.substring(0, idx) == "type") {
                val value = pair.substring(idx + 1)
                if (value.isNotBlank()) return value
            }
        }
        return null
    }

    private fun extractEventKind(intent: Intent): Int? {
        val decoded = decodeDataSafely(intent) ?: return null
        val json = decoded.substringBefore('?')
        if (json.isBlank() || !json.trimStart().startsWith('{')) return null
        return try {
            JacksonMapper.mapper.readTree(json).get("kind")?.asInt()
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeDataSafely(intent: Intent): String? {
        val raw = intent.data?.toString() ?: return null
        val stripped = raw.removePrefix("nostrsigner:")
        return try {
            URLDecoder.decode(stripped.replace("+", "%2b"), "utf-8")
        } catch (_: Throwable) {
            stripped
        }
    }
}
