package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.system.measureNanoTime
import org.junit.Test

/**
 * Benchmark tests for IntentUtils hot-path functions.
 *
 * These tests run each function under load and print throughput/latency to stdout.
 * They do not assert specific timing thresholds (which would be flaky across machines),
 * but they make regressions visible during local profiling and CI log review.
 *
 * Run with: ./gradlew test --tests "*.IntentUtilsBenchmarkTest" --no-daemon
 */
class IntentUtilsBenchmarkTest {

    // -------------------------------------------------------------------------
    // isUrlEncoded — regex match on a cached Regex vs. per-call compilation
    // -------------------------------------------------------------------------

    @Test
    fun `benchmark isUrlEncoded - percent-encoded input`() {
        val input = "nostrsigner%3Ahello%20world%26param%3Dvalue%3D1"
        val iterations = 200_000
        val elapsed = measureNanoTime {
            repeat(iterations) {
                IntentUtils.isUrlEncoded(input)
            }
        }
        printResult("isUrlEncoded (encoded)", iterations, elapsed)
    }

    @Test
    fun `benchmark isUrlEncoded - plain text input`() {
        val input = "nostrsignerHelloWorldParamValue1"
        val iterations = 200_000
        val elapsed = measureNanoTime {
            repeat(iterations) {
                IntentUtils.isUrlEncoded(input)
            }
        }
        printResult("isUrlEncoded (plain)", iterations, elapsed)
    }

    @Test
    fun `benchmark isUrlEncoded - long plain text input`() {
        val input = "a".repeat(1_000)
        val iterations = 50_000
        val elapsed = measureNanoTime {
            repeat(iterations) {
                IntentUtils.isUrlEncoded(input)
            }
        }
        printResult("isUrlEncoded (long plain, 1000 chars)", iterations, elapsed)
    }

    @Test
    fun `benchmark isUrlEncoded - long encoded input`() {
        val input = "a".repeat(500) + "%20" + "b".repeat(500)
        val iterations = 50_000
        val elapsed = measureNanoTime {
            repeat(iterations) {
                IntentUtils.isUrlEncoded(input)
            }
        }
        printResult("isUrlEncoded (long encoded, 1003 chars)", iterations, elapsed)
    }

    // -------------------------------------------------------------------------
    // decodeData — URL-decoding and prefix stripping
    // -------------------------------------------------------------------------

    @Test
    fun `benchmark decodeData - default args with percent-encoded input`() {
        val input = "nostrsigner:hello%20world%26param%3Dvalue"
        val iterations = 200_000
        val elapsed = measureNanoTime {
            repeat(iterations) {
                IntentUtils.decodeData(input)
            }
        }
        printResult("decodeData (replace=true, encoded)", iterations, elapsed)
    }

    @Test
    fun `benchmark decodeData - no decode, replace=true`() {
        val input = "nostrsigner:hello+world"
        val iterations = 200_000
        val elapsed = measureNanoTime {
            repeat(iterations) {
                IntentUtils.decodeData(input, replace = true, decodeData = false)
            }
        }
        printResult("decodeData (no decode, replace=true)", iterations, elapsed)
    }

    @Test
    fun `benchmark decodeData - replace=false, encoded`() {
        val input = "nostrsigner:hello%20world"
        val iterations = 200_000
        val elapsed = measureNanoTime {
            repeat(iterations) {
                IntentUtils.decodeData(input, replace = false)
            }
        }
        printResult("decodeData (replace=false, encoded)", iterations, elapsed)
    }

    @Test
    fun `benchmark decodeData - long encoded URL`() {
        // Simulate a realistic nostrsigner URI with a long encoded JSON payload
        val jsonFragment = "%7B%22kind%22%3A1%2C%22content%22%3A%22hello+world%22%7D"
        val input = "nostrsigner:$jsonFragment"
        val iterations = 100_000
        val elapsed = measureNanoTime {
            repeat(iterations) {
                IntentUtils.decodeData(input)
            }
        }
        printResult("decodeData (long encoded JSON fragment)", iterations, elapsed)
    }

    // -------------------------------------------------------------------------
    // parsePubKey — hex-to-npub conversion and npub passthrough
    // -------------------------------------------------------------------------

    @Test
    fun `benchmark parsePubKey - already npub`() {
        val npub = "npub1testvalueexampleexampleexampleexampleexampleexampleexample"
        val iterations = 200_000
        val elapsed = measureNanoTime {
            repeat(iterations) {
                IntentUtils.parsePubKey(npub)
            }
        }
        printResult("parsePubKey (npub passthrough)", iterations, elapsed)
    }

    @Test
    fun `benchmark parsePubKey - valid 32-byte hex`() {
        val hex = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
        val iterations = 50_000
        val elapsed = measureNanoTime {
            repeat(iterations) {
                IntentUtils.parsePubKey(hex)
            }
        }
        printResult("parsePubKey (hex to npub)", iterations, elapsed)
    }

    @Test
    fun `benchmark parsePubKey - invalid hex returns null`() {
        val invalid = "not-a-valid-hex-key!!!"
        val iterations = 200_000
        val elapsed = measureNanoTime {
            repeat(iterations) {
                IntentUtils.parsePubKey(invalid)
            }
        }
        printResult("parsePubKey (invalid, returns null)", iterations, elapsed)
    }

    // -------------------------------------------------------------------------
    // isRemembered — permission timestamp checks
    // -------------------------------------------------------------------------

    @Test
    fun `benchmark isRemembered - signPolicy 2 shortcircuit`() {
        val iterations = 500_000
        val elapsed = measureNanoTime {
            repeat(iterations) {
                IntentUtils.isRemembered(signPolicy = 2, permission = null)
            }
        }
        printResult("isRemembered (signPolicy=2, null permission)", iterations, elapsed)
    }

    @Test
    fun `benchmark isRemembered - future acceptUntil`() {
        val permission = permissionWith(acceptable = true, acceptUntil = TimeUtils.now() + 3600)
        val iterations = 500_000
        val elapsed = measureNanoTime {
            repeat(iterations) {
                IntentUtils.isRemembered(signPolicy = null, permission = permission)
            }
        }
        printResult("isRemembered (future acceptUntil, returns true)", iterations, elapsed)
    }

    @Test
    fun `benchmark isRemembered - future rejectUntil`() {
        val permission = permissionWith(acceptable = false, rejectUntil = TimeUtils.now() + 3600)
        val iterations = 500_000
        val elapsed = measureNanoTime {
            repeat(iterations) {
                IntentUtils.isRemembered(signPolicy = null, permission = permission)
            }
        }
        printResult("isRemembered (future rejectUntil, returns false)", iterations, elapsed)
    }

    @Test
    fun `benchmark isRemembered - expired permission`() {
        val permission = permissionWith(acceptable = true, acceptUntil = TimeUtils.now() - 3600)
        val iterations = 500_000
        val elapsed = measureNanoTime {
            repeat(iterations) {
                IntentUtils.isRemembered(signPolicy = null, permission = permission)
            }
        }
        printResult("isRemembered (expired, returns null)", iterations, elapsed)
    }

    @Test
    fun `benchmark isRemembered - null permission no policy`() {
        val iterations = 500_000
        val elapsed = measureNanoTime {
            repeat(iterations) {
                IntentUtils.isRemembered(signPolicy = null, permission = null)
            }
        }
        printResult("isRemembered (null permission, null policy)", iterations, elapsed)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun printResult(label: String, iterations: Int, elapsedNs: Long) {
        val totalMs = elapsedNs / 1_000_000.0
        val nsPerOp = elapsedNs.toDouble() / iterations
        println("BENCHMARK [$label]: ${iterations.formatCount()} ops | ${totalMs.fmt(2)}ms total | ${nsPerOp.fmt(1)}ns/op")
    }

    private fun Int.formatCount(): String = when {
        this >= 1_000_000 ->
            "${this / 1_000_000}M"
        this >= 1_000 ->
            "${this / 1_000}k"
        else ->
            toString()
    }

    private fun Double.fmt(decimals: Int) = "%.${decimals}f".format(this)

    private fun permissionWith(
        acceptable: Boolean = true,
        acceptUntil: Long = 0L,
        rejectUntil: Long = 0L,
    ) = ApplicationPermissionsEntity(
        id = 1,
        pkKey = "testKey",
        type = "sign_event",
        kind = 1,
        acceptable = acceptable,
        rememberType = 1,
        acceptUntil = acceptUntil,
        rejectUntil = rejectUntil,
    )
}
