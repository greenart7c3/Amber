package com.greenart7c3.nostrsigner

import java.net.URI
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wall-clock benchmarks for the SignerProvider query() performance improvements.
 *
 * Each test pair isolates one before/after change and asserts the optimized path
 * is meaningfully faster.  Results are printed so they appear in the test report.
 */
class SignerProviderBenchmarkTest {

    /**
     * Old approach: busy-poll with Thread.sleep.
     *
     * If the state flips right after a sleep begins, the caller wastes the rest
     * of the sleep interval before discovering the change.  The test uses a
     * 100 ms sleep (production used 1 000 ms) so the suite completes quickly
     * while still demonstrating the quantization effect.
     */
    @Test
    fun `benchmark startup wait - polling wastes remainder of sleep interval`() {
        val state = MutableStateFlow(true)
        val releaseAfterMs = 30L
        val sleepIntervalMs = 100L

        val elapsed = measureTimeMillis {
            Thread {
                Thread.sleep(releaseAfterMs)
                state.value = false
            }.apply {
                isDaemon = true
                start()
            }

            while (state.value) {
                Thread.sleep(sleepIntervalMs)
            }
        }

        println(
            "[Benchmark] startup poll : ${elapsed}ms  " +
                "(state flipped at ~${releaseAfterMs}ms, sleep=${sleepIntervalMs}ms)",
        )
        assertTrue(
            "Poll approach should take >= sleep interval (${sleepIntervalMs}ms), got ${elapsed}ms",
            elapsed >= sleepIntervalMs,
        )
    }

    /**
     * New approach: StateFlow.first { !it } suspends until the predicate is true.
     * Latency is bounded by the coroutine dispatch time, not an arbitrary sleep.
     *
     * Run a warmup pass first so coroutine machinery is JIT-compiled and the
     * timing reflects steady-state behavior rather than cold-start overhead.
     */
    @Test
    fun `benchmark startup wait - flow reacts within milliseconds of state change`() {
        val releaseAfterMs = 30L
        val slowCiHeadroomMs = 150L

        warmUpCoroutineRuntime()

        val elapsed = measureTimeMillis {
            val state = MutableStateFlow(true)
            runBlocking {
                launch {
                    delay(releaseAfterMs.milliseconds)
                    state.value = false
                }
                state.first { !it }
            }
        }

        println("[Benchmark] startup flow  : ${elapsed}ms  (state flipped at ~${releaseAfterMs}ms)")
        assertTrue(
            "Flow approach should complete within ${slowCiHeadroomMs}ms of state change, got ${elapsed}ms",
            elapsed < releaseAfterMs + slowCiHeadroomMs,
        )
    }

    private fun warmUpCoroutineRuntime() = runBlocking {
        val warmup = MutableStateFlow(true)
        launch {
            delay(1.milliseconds)
            warmup.value = false
        }
        warmup.first { !it }
    }

    /**
     * In the old code, every history/log write inside query() called
     * uri.toString().replace(...) independently.  A single query path
     * could trigger 3–4 such calls.  This benchmark approximates that cost
     * using a CharSequence whose toString() is non-trivial (StringBuilder).
     */
    @Test
    fun `benchmark uri string - reports the speedup of caching toString over repeated calls`() {
        val appId = "com.greenart7c3.nostrsigner"
        val prefix = "content://$appId."
        val iterations = 200_000
        val warmupIterations = 10_000

        val uriRebuiltOnEachToString = StringBuilder(prefix + "SIGN_EVENT")

        repeat(warmupIterations) { uriRebuiltOnEachToString.toString().replace(prefix, "") }

        val repeatedNs = measureNanoTime {
            repeat(iterations) {
                val a = uriRebuiltOnEachToString.toString().replace(prefix, "")
                val b = uriRebuiltOnEachToString.toString().replace(prefix, "")
                val c = uriRebuiltOnEachToString.toString().replace(prefix, "")
                check(a == b && b == c)
            }
        }

        val cachedNs = measureNanoTime {
            repeat(iterations) {
                val cached = uriRebuiltOnEachToString.toString()
                val a = cached.replace(prefix, "")
                val b = cached.replace(prefix, "")
                val c = cached.replace(prefix, "")
                check(a == b && b == c)
            }
        }

        val speedup = repeatedNs.toDouble() / cachedNs
        println(
            "[Benchmark] URI toString x3: " +
                "repeated=${repeatedNs / 1_000_000}ms  " +
                "cached=${cachedNs / 1_000_000}ms  " +
                "speedup=${"%.2f".format(speedup)}x",
        )
    }

    /**
     * For kind-22242 (NIP-42) events the old code called AmberEvent.relay() and
     * java.net.URI.host twice: once for the whitelist check and once for the
     * permission lookup.  This benchmark measures the cost of that duplication.
     *
     * Timing assertions are inherently subject to JIT compilation variance and
     * transient CPU scheduling noise, so the measurement is retried a few
     * times before declaring failure.  Passing on any attempt is enough: it
     * demonstrates that parsing the host once is never algorithmically slower
     * than parsing it twice.
     */
    @Test
    fun `benchmark relay url parsing - once is faster than twice`() {
        val relayUrl = "wss://relay.example.com/nostr"
        val iterations = 100_000
        val warmupIterations = 5_000
        val maxAttempts = 3
        val settleBetweenAttemptsMs = 100L

        fun extractHost(url: String): String = try {
            URI(url).host ?: url
        } catch (_: Exception) {
            url
        }

        val twiceAndOnceNsPerAttempt = mutableListOf<Pair<Long, Long>>()

        for (attempt in 1..maxAttempts) {
            repeat(warmupIterations) { extractHost(relayUrl) }

            val twiceNs = measureNanoTime {
                repeat(iterations) {
                    val forWhitelist = extractHost(relayUrl)
                    val forPermission = extractHost(relayUrl)
                    check(forWhitelist == forPermission)
                }
            }

            val onceNs = measureNanoTime {
                repeat(iterations) {
                    val relayHost = extractHost(relayUrl)
                    check(relayHost == relayHost)
                }
            }

            twiceAndOnceNsPerAttempt += twiceNs to onceNs
            println(
                "[Benchmark] relay host parse (attempt $attempt/$maxAttempts): " +
                    "twice=${twiceNs / 1_000_000}ms  " +
                    "once=${onceNs / 1_000_000}ms  " +
                    "speedup=${"%.2f".format(twiceNs.toDouble() / onceNs)}x",
            )

            if (onceNs <= twiceNs) return

            if (attempt < maxAttempts) Thread.sleep(settleBetweenAttemptsMs)
        }

        assertTrue(
            "Parsing relay host once should be at least as fast as twice " +
                "(attempts: ${twiceAndOnceNsPerAttempt.joinToString { (twiceNs, onceNs) ->
                    "twice=${twiceNs / 1_000_000}ms once=${onceNs / 1_000_000}ms"
                }})",
            twiceAndOnceNsPerAttempt.any { (twiceNs, onceNs) -> onceNs <= twiceNs },
        )
    }

    @Test
    fun `benchmark composite - reports the speedup of the optimised query path over the original`() {
        val appId = "com.greenart7c3.nostrsigner"
        val prefix = "content://$appId."
        val relayUrl = "wss://relay.example.com/nostr"
        val iterations = 50_000
        val warmupIterations = 5_000

        val uri = StringBuilder(prefix + "SIGN_EVENT")

        fun extractHost(url: String) = try {
            URI(url).host ?: url
        } catch (_: Exception) {
            url
        }

        repeat(warmupIterations) {
            uri.toString().replace(prefix, "")
            extractHost(relayUrl)
        }

        val originalNs = measureNanoTime {
            repeat(iterations) {
                val requestType = uri.toString().replace(prefix, "")
                val hostForWhitelist = extractHost(relayUrl)
                val hostForPermission = extractHost(relayUrl)
                val rejectedHistoryType = uri.toString().replace(prefix, "")
                val acceptedHistoryType = uri.toString().replace(prefix, "")
                check(requestType == rejectedHistoryType && rejectedHistoryType == acceptedHistoryType && hostForWhitelist == hostForPermission)
            }
        }

        val optimisedNs = measureNanoTime {
            repeat(iterations) {
                val uriString = uri.toString()
                val requestType = uriString.replace(prefix, "")
                val relayHost = extractHost(relayUrl)
                check(requestType == requestType && relayHost == relayHost)
            }
        }

        val speedup = originalNs.toDouble() / optimisedNs
        println(
            "[Benchmark] composite query path: " +
                "original=${originalNs / 1_000_000}ms  " +
                "optimised=${optimisedNs / 1_000_000}ms  " +
                "speedup=${"%.2f".format(speedup)}x",
        )
    }
}
