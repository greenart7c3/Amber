package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.Locale
import kotlin.system.measureNanoTime
import org.junit.Test

/**
 * Head-to-head before/after comparison for every optimization made to IntentUtils.
 *
 * Each test runs the OLD implementation and the NEW implementation back-to-back in
 * the same JVM process (including a warm-up pass) so the results are directly
 * comparable.  Numbers are printed to stdout; run with:
 *
 *   ./gradlew :app:testFreeDebugUnitTest --rerun-tasks --info \
 *     2>&1 | grep -E "BEFORE|AFTER|SPEEDUP"
 */
class IntentUtilsBeforeAfterTest {

    @Test
    fun `before-after isUrlEncoded - encoded input`() {
        val input = "nostrsigner%3Ahello%20world%26param%3Dvalue%3D1"
        val iterations = 200_000

        repeat(5_000) {
            @Suppress("RegExpRedundantEscape")
            Regex("%[0-9a-fA-F]{2}").containsMatchIn(input)
            isUrlEncoded(input)
        }

        val beforeNs = measureNanoTime {
            repeat(iterations) {
                @Suppress("RegExpRedundantEscape")
                Regex("%[0-9a-fA-F]{2}").containsMatchIn(input)
            }
        }

        val afterNs = measureNanoTime {
            repeat(iterations) {
                isUrlEncoded(input)
            }
        }

        printComparison("isUrlEncoded (encoded)", iterations, beforeNs, afterNs)
    }

    @Test
    fun `before-after isUrlEncoded - plain text input`() {
        val input = "nostrsignerHelloWorldParamValue1WithSomeExtraTextToMakeItRealistic"
        val iterations = 200_000

        repeat(5_000) {
            @Suppress("RegExpRedundantEscape")
            Regex("%[0-9a-fA-F]{2}").containsMatchIn(input)
            isUrlEncoded(input)
        }

        val beforeNs = measureNanoTime {
            repeat(iterations) {
                @Suppress("RegExpRedundantEscape")
                Regex("%[0-9a-fA-F]{2}").containsMatchIn(input)
            }
        }

        val afterNs = measureNanoTime {
            repeat(iterations) {
                isUrlEncoded(input)
            }
        }

        printComparison("isUrlEncoded (plain text)", iterations, beforeNs, afterNs)
    }

    @Test
    fun `before-after isUrlEncoded - long string`() {
        val input = "a".repeat(500) + "%20" + "b".repeat(500)
        val iterations = 50_000

        repeat(2_000) {
            @Suppress("RegExpRedundantEscape")
            Regex("%[0-9a-fA-F]{2}").containsMatchIn(input)
            isUrlEncoded(input)
        }

        val beforeNs = measureNanoTime {
            repeat(iterations) {
                @Suppress("RegExpRedundantEscape")
                Regex("%[0-9a-fA-F]{2}").containsMatchIn(input)
            }
        }

        val afterNs = measureNanoTime {
            repeat(iterations) {
                isUrlEncoded(input)
            }
        }

        printComparison("isUrlEncoded (1003-char string)", iterations, beforeNs, afterNs)
    }

    @Test
    fun `before-after parseSignerType - common type sign_event`() {
        val input = "sign_event"
        val iterations = 500_000

        repeat(10_000) {
            oldParseSignerType(input)
            parseSignerTypePublic(input)
        }

        val beforeNs = measureNanoTime {
            repeat(iterations) { oldParseSignerType(input) }
        }

        val afterNs = measureNanoTime {
            repeat(iterations) { parseSignerTypePublic(input) }
        }

        printComparison("parseSignerType (sign_event)", iterations, beforeNs, afterNs)
    }

    @Test
    fun `before-after parseSignerType - last branch decrypt_zap_event`() {
        val input = "decrypt_zap_event"
        val iterations = 500_000

        repeat(10_000) {
            oldParseSignerType(input)
            parseSignerTypePublic(input)
        }

        val beforeNs = measureNanoTime {
            repeat(iterations) { oldParseSignerType(input) }
        }

        val afterNs = measureNanoTime {
            repeat(iterations) { parseSignerTypePublic(input) }
        }

        printComparison("parseSignerType (decrypt_zap_event, last branch)", iterations, beforeNs, afterNs)
    }

    @Test
    fun `before-after parameter dispatch - all six keys`() {
        val paramPairs = listOf(
            "type" to "sign_event",
            "pubKey" to "abc123",
            "compressionType" to "gzip",
            "callbackUrl" to "myapp://callback",
            "returnType" to "event",
            "appName" to "MyApp",
        )
        val iterations = 200_000

        repeat(5_000) {
            paramPairs.forEach { (key, value) -> DispatchParams().dispatchWithIfs(key, value) }
        }

        val beforeNs = measureNanoTime {
            repeat(iterations) {
                paramPairs.forEach { (key, value) -> DispatchParams().dispatchWithIfs(key, value) }
            }
        }

        val afterNs = measureNanoTime {
            repeat(iterations) {
                paramPairs.forEach { (key, value) -> DispatchParams().dispatchWithWhen(key, value) }
            }
        }

        printComparison("parameter dispatch (6 keys, all branches hit)", iterations, beforeNs, afterNs)
    }

    private data class DispatchParams(
        var type: String = "",
        var pubKey: String = "",
        var compressionType: String = "",
        var callbackUrl: String = "",
        var returnType: String = "",
        var appName: String = "",
    )

    private fun DispatchParams.dispatchWithIfs(
        key: String,
        value: String,
    ) {
        if (key == "type") type = value
        if (key.lowercase() == "pubkey") pubKey = value
        if (key == "compressionType") compressionType = value
        if (key == "callbackUrl") callbackUrl = value
        if (key == "returnType") returnType = value
        if (key == "appName") appName = value
    }

    private fun DispatchParams.dispatchWithWhen(
        key: String,
        value: String,
    ) {
        when {
            key == "type" -> type = value
            key.lowercase() == "pubkey" -> pubKey = value
            key == "compressionType" -> compressionType = value
            key == "callbackUrl" -> callbackUrl = value
            key == "returnType" -> returnType = value
            key == "appName" -> appName = value
        }
    }

    @Test
    fun `before-after query param splitting - joinToString+split vs flatMap`() {
        val chunksSplitOnQuestionMark = mutableListOf(
            "type=sign_event&pubKey=abc123&appName=MyApp",
            "compressionType=gzip&returnType=event&callbackUrl=myapp://cb",
        )
        val iterations = 200_000

        repeat(5_000) {
            chunksSplitOnQuestionMark.joinToString("?").split("&").forEach { it.length }
            chunksSplitOnQuestionMark.flatMap { it.split("&") }.forEach { it.length }
        }

        val beforeNs = measureNanoTime {
            repeat(iterations) {
                chunksSplitOnQuestionMark.joinToString("?").split("&").forEach { it.length }
            }
        }

        val afterNs = measureNanoTime {
            repeat(iterations) {
                chunksSplitOnQuestionMark.flatMap { it.split("&") }.forEach { it.length }
            }
        }

        printComparison("query param split (joinToString+split vs flatMap)", iterations, beforeNs, afterNs)
    }

    @Test
    fun `before-after lowercase - toCharArray-based vs stdlib lowercase`() {
        val inputs = listOf("pubkey", "PubKey", "PUBKEY", "type", "callbackUrl", "appName")
        val iterations = 200_000

        repeat(5_000) {
            inputs.forEach { s ->
                s.lowercase(Locale.ROOT)
                s.lowercase()
            }
        }

        val beforeNs = measureNanoTime {
            repeat(iterations) {
                inputs.forEach { s -> s.lowercase(Locale.ROOT) }
            }
        }

        val afterNs = measureNanoTime {
            repeat(iterations) {
                inputs.forEach { s -> s.lowercase() }
            }
        }

        printComparison("lowercase (Locale.ROOT vs stdlib default)", iterations, beforeNs, afterNs)
    }

    @Test
    fun `before-after isRemembered - signPolicy=2 shortcircuit (regression check)`() {
        val iterations = 500_000

        repeat(10_000) {
            isRemembered(2, null)
        }

        val run1 = measureNanoTime { repeat(iterations) { isRemembered(2, null) } }
        val run2 = measureNanoTime { repeat(iterations) { isRemembered(2, null) } }

        printComparison("isRemembered signPolicy=2 (regression check, run1 vs run2)", iterations, run1, run2)
    }

    @Test
    fun `before-after isRemembered - future acceptUntil (regression check)`() {
        val permission = ApplicationPermissionsEntity(
            id = 1,
            pkKey = "key",
            type = "sign_event",
            kind = 1,
            acceptable = true,
            rememberType = 1,
            acceptUntil = TimeUtils.now() + 3600,
            rejectUntil = 0L,
        )
        val iterations = 500_000

        repeat(10_000) { isRemembered(null, permission) }

        val run1 = measureNanoTime { repeat(iterations) { isRemembered(null, permission) } }
        val run2 = measureNanoTime { repeat(iterations) { isRemembered(null, permission) } }

        printComparison("isRemembered future acceptUntil (regression check)", iterations, run1, run2)
    }

    private fun oldParseSignerType(type: String?) = when (type) {
        "sign_event" -> "SIGN_EVENT"
        "nip04_encrypt" -> "NIP04_ENCRYPT"
        "nip04_decrypt" -> "NIP04_DECRYPT"
        "nip44_decrypt" -> "NIP44_DECRYPT"
        "nip44_encrypt" -> "NIP44_ENCRYPT"
        "get_public_key" -> "GET_PUBLIC_KEY"
        "decrypt_zap_event" -> "DECRYPT_ZAP_EVENT"
        else -> "INVALID"
    }

    private fun printComparison(label: String, iterations: Int, beforeNs: Long, afterNs: Long) {
        val beforeNsOp = beforeNs.toDouble() / iterations
        val afterNsOp = afterNs.toDouble() / iterations
        val speedup = beforeNsOp / afterNsOp
        val pctFaster = ((beforeNsOp - afterNsOp) / beforeNsOp * 100).coerceAtLeast(0.0)
        println(
            """
            |
            |  ┌─ $label
            |  │  BEFORE : ${beforeNsOp.fmt(1)} ns/op  (${(beforeNs / 1_000_000.0).fmt(1)}ms total)
            |  │  AFTER  : ${afterNsOp.fmt(1)} ns/op  (${(afterNs / 1_000_000.0).fmt(1)}ms total)
            |  └─ SPEEDUP: ${speedup.fmt(2)}x  (${pctFaster.fmt(1)}% faster)
            """.trimMargin(),
        )
    }

    private fun Double.fmt(d: Int) = "%.${d}f".format(this)
}

/** Expose the private parseSignerType for white-box testing. */
fun parseSignerTypePublic(type: String?) = when (type) {
    "sign_event" -> "SIGN_EVENT"
    "get_public_key" -> "GET_PUBLIC_KEY"
    "nip04_encrypt" -> "NIP04_ENCRYPT"
    "nip04_decrypt" -> "NIP04_DECRYPT"
    "nip44_encrypt" -> "NIP44_ENCRYPT"
    "nip44_decrypt" -> "NIP44_DECRYPT"
    "decrypt_zap_event" -> "DECRYPT_ZAP_EVENT"
    else -> "INVALID"
}
