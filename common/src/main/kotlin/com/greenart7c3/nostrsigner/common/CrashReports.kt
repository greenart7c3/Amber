package com.greenart7c3.nostrsigner.common

import java.util.concurrent.TimeoutException

/** What both apps' crash reports share: which crashes to drop, and redaction. */
object CrashReports {
    /**
     * Crashes we never prompt the user to report because nobody can act on them:
     * Android finalizer-watchdog timeouts (e.g.
     * `BinderInternal$GcWatcher.finalize() timed out after 10 seconds`), a known
     * AOSP issue raised on the FinalizerDaemon thread with no app frames. The OS
     * kills the process regardless; app code cannot catch, prevent, or fix it.
     *
     * `OutOfMemoryError` is reported: with the heap numbers in the report it is
     * the only way to see what exhausted memory in the field.
     */
    fun isJunkReport(e: Throwable): Boolean = isPlatformFinalizerTimeout(e)

    private fun isPlatformFinalizerTimeout(e: Throwable): Boolean = e is TimeoutException &&
        (
            e.message?.contains("finalize() timed out") == true ||
                e.stackTrace.any { it.className.startsWith($$"java.lang.Daemons$FinalizerDaemon") }
            )

    /**
     * Defense-in-depth (GHSA-8844-q5vh-9j8f, L3): exception messages and stack
     * traces can embed attacker-controlled request data (parser/crypto
     * exceptions quoting bad input). Redacts Nostr key material (bech32
     * npub/nsec/etc, 64-char hex keys) and long base64 blobs so a crash report
     * cannot leak secrets.
     */
    fun redactKeys(input: String): String = input
        .replace(NOSTR_BECH32_ENTITY, "<bech32>")
        .replace(HEX_KEY_OR_EVENT_ID, "<hexkey>")
        .replace(BASE64_CIPHERTEXT_OR_PAYLOAD, "<base64>")

    private const val BECH32_ALPHABET = "023456789acdefghjklmnpqrstuvwxyz"
    private val NOSTR_BECH32_PREFIXES = listOf("npub", "nsec", "note", "nprofile", "nevent", "naddr", "nrelay", "ncryptsec")
    private val NOSTR_BECH32_ENTITY =
        Regex("\\b(${NOSTR_BECH32_PREFIXES.joinToString("|")})1[$BECH32_ALPHABET]{6,}\\b")

    private val HEX_KEY_OR_EVENT_ID = Regex("\\b[0-9a-f]{64}\\b")

    private val BASE64_CIPHERTEXT_OR_PAYLOAD = Regex("\\b[A-Za-z0-9+/]{32,}={0,2}\\b")
}
