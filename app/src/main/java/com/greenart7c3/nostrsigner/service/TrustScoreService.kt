package com.greenart7c3.nostrsigner.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.models.TorMode
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
     * Whether trust scores should be fetched and displayed.
     * False on the offline flavor or when the user disabled trust scores in the settings.
     */
    fun isEnabled(): Boolean = !BuildFlavorChecker.isOfflineFlavor() && Amber.instance.settings.trustScoreEnabled

    /**
     * True when the URL policy excludes this relay from the ratings API entirely
     * (private/anonymous endpoints, credentials, IP literals). The UI hides the
     * score badge for these instead of showing UNKNOWN, which would read as
     * "untrusted".
     */
    fun isExcludedFromScoring(relayUrl: String): Boolean = !BuildFlavorChecker.isOfflineFlavor() &&
        TrustScoreUrlPolicy.publicRelayUrl(relayUrl) == null

    /**
     * Gets the trust score for a relay URL.
     * Returns cached value if available and not expired, otherwise fetches from API.
     */
    suspend fun getScore(relayUrl: String): Int? {
        if (!isEnabled()) {
            return null
        }

        // One key function for cache lookups and the API query alike — a lookup
        // normalized differently from the fetch never hits. Excluded URLs are
        // resolved up front: no cache probe, no network, no failed-entry churn.
        val normalizedUrl = TrustScoreUrlPolicy.publicRelayUrl(relayUrl) ?: return null
        val cached = cache[normalizedUrl]

        if (cached != null) {
            val expirationTime = if (cached.isFailed) FAILED_CACHE_DURATION_MS else CACHE_DURATION_MS
            if (System.currentTimeMillis() - cached.timestamp < expirationTime) {
                return cached.score
            }
        }

        return fetchScore(relayUrl, normalizedUrl)
    }

    /**
     * Fetches the trust score from the API. The caller resolves the policy
     * normalization once so the URL is computed exactly once per request.
     */
    private suspend fun fetchScore(relayUrl: String, normalizedUrl: String): Int? {
        if (!isEnabled()) {
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val encodedUrl = URLEncoder.encode(normalizedUrl, "UTF-8")
                val requestUrl = "$API_BASE_URL$encodedUrl"

                val client = HttpClientManager.getHttpClient(Amber.instance.settings.torMode != TorMode.DISABLED)
                val request = Request.Builder()
                    .url(requestUrl)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->

                    if (!response.isSuccessful) {
                        AmberLog.w(TAG, "Failed to fetch trust score for $relayUrl: ${response.code}")
                        cacheResult(normalizedUrl, null, isFailed = true)
                        return@withContext null
                    }

                    // Safe call needed: the offline flavor compiles against OkHttp 4, where body is nullable
                    @Suppress("UNNECESSARY_SAFE_CALL")
                    val body = response.body?.string()
                    if (body == null) {
                        cacheResult(normalizedUrl, null, isFailed = true)
                        return@withContext null
                    }

                    val score = parseResponse(body)
                    cacheResult(normalizedUrl, score, isFailed = false)
                    score
                }
            } catch (e: Exception) {
                AmberLog.e(TAG, "Error fetching trust score for $relayUrl", e)
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
            AmberLog.e(TAG, "Error parsing trust score response", e)
            null
        }
    }

    private fun cacheResult(normalizedUrl: String, score: Int?, isFailed: Boolean) {
        cache[normalizedUrl] = CachedScore(score, System.currentTimeMillis(), isFailed)
    }

    /**
     * Clears all cached scores.
     */
    fun clearCache() {
        cache.clear()
    }
}
