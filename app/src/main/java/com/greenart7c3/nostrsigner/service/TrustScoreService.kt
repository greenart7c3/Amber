package com.greenart7c3.nostrsigner.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.models.TorMode
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response

object TrustScoreService {
    private const val TAG = "TrustScoreService"
    private const val API_BASE_URL = "https://trustedrelays.xyz/api/score?url="
    private const val CACHE_DURATION_MS = 60 * 60 * 1000L
    private const val FAILED_CACHE_DURATION_MS = 5 * 60 * 1000L

    private data class CachedScore(val score: Int?, val timestamp: Long, val isFailed: Boolean = false)

    private val cache = ConcurrentHashMap<String, CachedScore>()
    private val mapper = jacksonObjectMapper()

    /**
     * Whether trust scores should be fetched and displayed.
     * False on the offline flavor or when the user disabled trust scores in the settings.
     */
    fun isEnabled(settings: AmberSettings): Boolean = !BuildFlavorChecker.isOfflineFlavor() && settings.trustScoreEnabled

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
     * A URL [TrustScoreUrlPolicy] excludes returns null without a cache probe or a request,
     * and the policy's canonical URL is both the cache key and the API query.
     */
    suspend fun getScore(relayUrl: String, settings: AmberSettings): Int? {
        if (!isEnabled(settings)) {
            return null
        }

        val normalizedUrl = TrustScoreUrlPolicy.publicRelayUrl(relayUrl) ?: return null
        val cached = cache[normalizedUrl]

        if (cached != null) {
            val expirationTime = if (cached.isFailed) FAILED_CACHE_DURATION_MS else CACHE_DURATION_MS
            if (System.currentTimeMillis() - cached.timestamp < expirationTime) {
                return cached.score
            }
        }

        return fetchScore(relayUrl, normalizedUrl, settings.torMode)
    }

    private suspend fun fetchScore(relayUrl: String, normalizedUrl: String, torMode: TorMode): Int? {
        return withContext(Dispatchers.IO) {
            try {
                val encodedUrl = URLEncoder.encode(normalizedUrl, "UTF-8")
                val requestUrl = "$API_BASE_URL$encodedUrl"

                val client = HttpClientManager.getHttpClient(torMode != TorMode.DISABLED)
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

                    val body = response.bodyStringOrNull()
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

    @Suppress("UNNECESSARY_SAFE_CALL", "NoComments")
    private fun Response.bodyStringOrNull(): String? {
        // Safe call needed: the offline flavor compiles against OkHttp 4, where body is nullable
        return body?.string()
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
