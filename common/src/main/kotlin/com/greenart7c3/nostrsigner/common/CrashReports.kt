package com.greenart7c3.nostrsigner.common

import java.util.concurrent.TimeoutException

/** What both apps' crash reports share: which crashes to drop, and redaction. */
object CrashReports {
    /**
     * Crashes we never prompt the user to report because nobody can act on them:
     * - OutOfMemoryError: junk reports.
     * - Android finalizer-watchdog timeouts (e.g.
     *   `BinderInternal$GcWatcher.finalize() timed out after 10 seconds`): a known
     *   AOSP issue raised on the FinalizerDaemon thread with no app frames. The OS
     *   kills the process regardless; app code cannot catch, prevent, or fix it.
     */
    fun isJunkReport(e: Throwable): Boolean = e is OutOfMemoryError || isPlatformFinalizerTimeout(e)

    private fun isPlatformFinalizerTimeout(e: Throwable): Boolean = e is TimeoutException &&
        (
            e.message?.contains("finalize() timed out") == true ||
                e.stackTrace.any { it.className.startsWith("java.lang.Daemons\$FinalizerDaemon") }
            )

    /**
     * Defense-in-depth (GHSA-8844-q5vh-9j8f, L3): exception messages and stack
     * traces can embed attacker-controlled request data (parser/crypto
     * exceptions quoting bad input). Redacts Nostr key material (bech32
     * npub/nsec/etc, 64-char hex keys) and long base64 blobs so a crash report
     * cannot leak secrets.
     */
    fun redactKeys(input: String): String = input
        .replace(BECH32_REDACT, "<bech32>")
        .replace(HEXKEY_REDACT, "<hexkey>")
        .replace(BASE64_REDACT, "<base64>")

    // bech32 prefixes used by Nostr (npub, nsec, note, nprofile, nevent,
    // naddr, nrelay, ncryptsec). Match the hrp + data part; bech32 uses
    // the alphabet 023456789acdefghjklmnpqrstuvwxyz (no b, i, o, 1).
    private val BECH32_REDACT =
        Regex("\\b(npub|nsec|note|nprofile|nevent|naddr|nrelay|ncryptsec)1[023456789acdefghjklmnpqrstuvwxyz]{6,}\\b")

    // 64-char lowercase hex keys (pubkeys/event ids/private keys).
    private val HEXKEY_REDACT = Regex("\\b[0-9a-f]{64}\\b")

    // Long base64 runs (>=32 chars): NIP-04/NIP-44/44v3 ciphertexts, payloads.
    private val BASE64_REDACT = Regex("\\b[A-Za-z0-9+/]{32,}={0,2}\\b")
}
