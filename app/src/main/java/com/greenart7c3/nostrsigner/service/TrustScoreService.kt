package com.greenart7c3.nostrsigner.service

import android.util.Log
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

object TrustScoreService {
    private const val TAG = "TrustScoreService"
    private const val API_BASE_URL = "https://trustedrelays.xyz/api/score?url="
    private const val CACHE_DURATION_MS = 60 * 60 * 1000L // 1 hour
    private const val FAILED_CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes for failed requests

    private data class CachedScore(val score: Int?, val timestamp: Long, val isFailed: Boolean = false)

    private val cache = ConcurrentHashMap<String, CachedScore>()
    private val mapper = jacksonObjectMapper()

    /**
     * Gets the trust score for a relay URL.
     * Returns cached value if available and not expired, otherwise fetches from API.
     */
    suspend fun getScore(relayUrl: String): Int? {
        if (BuildFlavorChecker.isOfflineFlavor()) {
            return null
        }

        val normalizedUrl = normalizeUrl(relayUrl)
        val cached = cache[normalizedUrl]

        if (cached != null) {
            val expirationTime = if (cached.isFailed) FAILED_CACHE_DURATION_MS else CACHE_DURATION_MS
            if (System.currentTimeMillis() - cached.timestamp < expirationTime) {
                return cached.score
            }
        }

        return fetchScore(relayUrl)
    }

    /**
     * Gets the cached score without making a network request.
     * Returns null if not cached or expired.
     */
    fun getCachedScore(relayUrl: String): Int? {
        val normalizedUrl = normalizeUrl(relayUrl)
        val cached = cache[normalizedUrl] ?: return null

        val expirationTime = if (cached.isFailed) FAILED_CACHE_DURATION_MS else CACHE_DURATION_MS
        if (System.currentTimeMillis() - cached.timestamp < expirationTime) {
            return cached.score
        }
        return null
    }

    /**
     * Prefetches scores for multiple relay URLs in parallel.
     */
    suspend fun prefetchScores(relayUrls: List<String>) {
        if (BuildFlavorChecker.isOfflineFlavor()) {
            return
        }

        withContext(Dispatchers.IO) {
            relayUrls.forEach { url ->
                try {
                    getScore(url)
                } catch (e: Exception) {
                    Log.e(TAG, "Error prefetching score for $url", e)
                }
            }
        }
    }

    /**
     * Fetches the trust score from the API.
     */
    private suspend fun fetchScore(relayUrl: String): Int? {
        if (BuildFlavorChecker.isOfflineFlavor()) {
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val normalizedUrl = normalizeUrl(relayUrl)
                val encodedUrl = URLEncoder.encode(normalizedUrl, "UTF-8")
                val requestUrl = "$API_BASE_URL$encodedUrl"

                val client = HttpClientManager.getHttpClient(Amber.instance.settings.useProxy)
                val request = Request.Builder()
                    .url(requestUrl)
                    .get()
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.w(TAG, "Failed to fetch trust score for $relayUrl: ${response.code}")
                    cacheResult(normalizedUrl, null, isFailed = true)
                    return@withContext null
                }

                val body = response.body?.string()
                if (body == null) {
                    cacheResult(normalizedUrl, null, isFailed = true)
                    return@withContext null
                }

                val score = parseResponse(body)
                cacheResult(normalizedUrl, score, isFailed = false)
                score
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching trust score for $relayUrl", e)
                val normalizedUrl = normalizeUrl(relayUrl)
                cacheResult(normalizedUrl, null, isFailed = true)
                null
            }
        }
    }

    private fun parseResponse(body: String): Int? {
        return try {
            val json = mapper.readTree(body)
            val success = json.get("success")?.asBoolean() ?: false
            if (!success) {
                return null
            }
            json.get("data")?.get("score")?.asInt()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing trust score response", e)
            null
        }
    }

    private fun cacheResult(normalizedUrl: String, score: Int?, isFailed: Boolean) {
        cache[normalizedUrl] = CachedScore(score, System.currentTimeMillis(), isFailed)
    }

    private fun normalizeUrl(url: String): String = url.trimEnd('/')

    /**
     * Clears all cached scores.
     */
    fun clearCache() {
        cache.clear()
    }
}
