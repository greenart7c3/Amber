package com.greenart7c3.nostrsigner.desktop.core

import com.greenart7c3.nostrsigner.common.CrashReports
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.utils.TimeUtils
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Desktop counterpart of the Android crash reporter (`UnexpectedCrashSaver`,
 * `CrashReportCache`, `ReportSender`): an uncaught exception is saved as a
 * redacted report, offered to the user on the next unlock (or right away if
 * the app survived it), and sent to the developer as a NIP-17 DM only when
 * the user chooses to.
 */
object CrashReporter {
    /** Same recipient and relays as the Android `ReportSender`. */
    const val DEVELOPER_HEX_KEY = "7579076d9aff0a4cfdefa7e2045f2486c7e5d8bc63bfc6b45397233e1bbfcb19"
    private val REPORT_RELAYS = setOf(
        NormalizedRelayUrl(url = "wss://inbox.nostr.wine"),
        NormalizedRelayUrl(url = "wss://nostr.land"),
    )
    private const val THIRTY_DAYS_SECONDS = 30L * 86_400

    private val file: File get() = File(AppDirs.dataDir, "crash.trace")

    /** Marks a report encrypted with the master key; its plaintext is only readable after unlock. */
    internal const val ENCRYPTED_HEADER = "AMBERCRASH1:"
    private const val CONTEXT = "crash.trace"

    /** The report waiting for the user's decision, if any. */
    val pending = MutableStateFlow<String?>(null)

    /** Installs the uncaught-exception handler and loads a report left by a previous run. */
    fun install() {
        // An encrypted report waits for the unlock (see [loadAfterUnlock]).
        pending.value = runCatching { file.takeIf { it.exists() }?.readText()?.ifBlank { null }?.takeUnless { it.startsWith(ENCRYPTED_HEADER) } }.getOrNull()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { save(e) }
            if (previous != null) previous.uncaughtException(thread, e) else e.printStackTrace()
        }
    }

    /**
     * Persists the report synchronously (the JVM may be about to exit) and
     * surfaces it at once in case the app keeps running. The file stays until
     * the user sends or dismisses the report, so closing Amber first does not
     * lose it. The latest crash replaces an older unhandled one.
     */
    fun save(e: Throwable) {
        if (CrashReports.isJunkReport(e)) return
        val report = ReportAssembler.buildReport(e)
        AppDirs.writeAtomically(file, storedForm(e, report).toByteArray(Charsets.UTF_8))
        pending.value = report
    }

    /**
     * What goes on disk. Exception messages can quote request data that the
     * redaction misses (short secrets, decrypted text), so with a passphrase
     * set the report is encrypted with the master key, and when the key is
     * not available (locked) only exception types and stack frames are kept.
     * Before a passphrase exists there is no key to encrypt with.
     */
    private fun storedForm(e: Throwable, report: String): String {
        if (!PassphraseLock.isEnabled()) return report
        return runCatching { ENCRYPTED_HEADER + DesktopKeyStore.encryptString(report, CONTEXT) }
            .getOrElse { ReportAssembler.buildReport(e, includeMessages = false) }
    }

    /**
     * Called once the passphrase lock is unlocked: decrypts a report saved
     * encrypted by a previous run and offers it. One that cannot be decrypted
     * (another master key, tampered) is discarded.
     */
    fun loadAfterUnlock() {
        // Still locked: leave the report for the next unlock rather than lose it.
        if (pending.value != null || !DesktopKeyStore.isMasterKeyAvailable()) return
        val stored = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull() ?: return
        if (!stored.startsWith(ENCRYPTED_HEADER)) return
        pending.value = runCatching { DesktopKeyStore.decryptString(stored.removePrefix(ENCRYPTED_HEADER), CONTEXT) }
            .onFailure {
                AmberLogger.e("CrashReporter", "Discarding a crash report that cannot be decrypted", it)
                file.delete()
            }
            .getOrNull()
    }

    fun dismiss() {
        file.delete()
        pending.value = null
    }

    /**
     * Sends [report] (as edited by the user) gift-wrapped to the developer,
     * expiring after 30 days, through the regular relay route (Tor when
     * enabled). Returns whether any relay accepted it; the pending report is
     * cleared only then.
     */
    suspend fun send(account: DesktopAccount, report: String): Boolean {
        if (report.isBlank()) return false
        val template = ChatMessageEvent.build(msg = report, to = listOf(PTag(DEVELOPER_HEX_KEY))) {
            expiration(TimeUtils.now() + THIRTY_DAYS_SECONDS)
        }
        val wraps = NIP17Factory().createMessageNIP17(template, account.signer).wraps
        val client = AmberDesktop.newClient()
        return try {
            client.connect()
            // Every wrap (the developer's and the sender's own copy), as on Android.
            val accepted = wraps.map { client.publishAndConfirm(it, REPORT_RELAYS) }.any { it }
            if (accepted) dismiss()
            accepted
        } catch (e: Exception) {
            AmberLogger.e("CrashReporter", "Failed to send the crash report", e)
            false
        } finally {
            client.disconnect()
        }
    }
}

object ReportAssembler {
    /**
     * The report for [e]. Without [includeMessages] it holds exception types
     * and stack frames only, never a message (which can quote request data).
     */
    fun buildReport(e: Throwable, includeMessages: Boolean = true): String = buildString {
        append(e.javaClass.simpleName)
        append(": ")
        appendLine(BuildVariant.version + "-DESKTOP" + if (BuildVariant.isDebug) "-DEBUG" else "")
        appendLine()

        appendLine("| Prop | Value |")
        appendLine("|------|-------|")
        row("OS", "${System.getProperty("os.name")} ${System.getProperty("os.version")}")
        row("Arch", System.getProperty("os.arch"))
        row("Java", "${System.getProperty("java.vendor")} ${System.getProperty("java.version")}")
        System.getenv("XDG_CURRENT_DESKTOP")?.takeIf { it.isNotBlank() }?.let { row("Desktop", it) }
        System.getenv("XDG_SESSION_TYPE")?.takeIf { it.isNotBlank() }?.let { row("Session", it) }
        row("Thread", Thread.currentThread().name)
        appendLine()

        // Exception messages and stack traces can embed request data (parser
        // or crypto exceptions quoting bad input) and file paths: redact key
        // material, long base64 blobs and the home directory, as on Android.
        appendLine("```")
        appendThrowable(e, indent = "", includeMessages)
        appendLine("```")
    }

    private fun StringBuilder.row(name: String, value: String?) {
        append("| ").append(name).append(" | ").append(redact(value ?: "")).appendLine(" |")
    }

    // The whole cause chain (Android stops at the first cause); identity set
    // guards against cyclic causes.
    private fun StringBuilder.appendThrowable(e: Throwable, indent: String, includeMessages: Boolean) {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var current: Throwable? = e
        var depth = 0
        while (current != null && seen.add(current)) {
            val pad = indent + "    ".repeat(depth)
            if (depth > 0) {
                appendLine()
                append(pad.dropLast(4)).appendLine("Cause:")
            }
            append(pad).appendLine(if (includeMessages) redact(current.toString()) else current.javaClass.name)
            current.stackTrace.forEach { append(pad).append("    ").appendLine(redact(it.toString())) }
            current = current.cause
            depth++
        }
    }

    fun redact(input: String): String {
        val home = System.getProperty("user.home").orEmpty()
        val withoutHome = if (home.length > 1) input.replace(home, "~") else input
        return CrashReports.redactKeys(withoutHome)
    }
}
