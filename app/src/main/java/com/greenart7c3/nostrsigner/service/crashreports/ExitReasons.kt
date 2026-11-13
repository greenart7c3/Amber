/**
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.greenart7c3.nostrsigner.service.crashreports

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.common.CrashReports
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Why the previous process died, when it did not die of a Java exception.
 *
 * The crash report screen is fed by [CrashReportCache], which only ever sees exceptions the
 * runtime handed to the uncaught-exception handler. ANRs, low-memory kills, native crashes and
 * initialization failures never get there, so nothing was reportable for them. Android keeps
 * those records in [ActivityManager.getHistoricalProcessExitReasons]; this object turns the ones
 * that matter into plain text that is added to the crash report (ported from Citrine).
 *
 * Records are de-duplicated with a "last seen" timestamp in plain SharedPreferences (an app-wide
 * file, not a per-account one), and the formatted records are kept in a second key so both the
 * log line written from `Amber.onCreate` and the crash report see the same text.
 */
object ExitReasons {
    private const val PREFS = "exit_reasons"
    private const val KEY_LAST_SEEN = "last_seen_timestamp"
    private const val KEY_PENDING = "pending_records"
    private const val MAX_RECORDS = 5
    private const val MAX_TRACE_CHARS = 2_000

    private val detectionLock = Any()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    // Exit reasons worth showing to a user: all of them mean the process was killed without
    // reaching the uncaught-exception handler. The constants are compile-time ints copied into
    // this class, so building the set is safe below API 30; it is only consulted after [detect]'s
    // API check.
    @SuppressLint("InlinedApi")
    private val ABNORMAL_REASONS = setOf(
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_LOW_MEMORY,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        ApplicationExitInfo.REASON_SIGNALED,
    )

    /**
     * True when [reason] is a death the in-app log and dialog should surface. [importance] keeps
     * ordinary user actions (force stop, swipe away, package update) and background cache trims
     * out of the report: Android reports those with the same reason codes. Lower is more
     * important, so the process must have been at least as important as a foreground service.
     */
    fun isAbnormal(reason: Int, importance: Int): Boolean = importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE && reason in ABNORMAL_REASONS

    /** Short label for [reason], matching the constant names in [ApplicationExitInfo]. */
    fun reasonLabel(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        else -> "REASON_$reason"
    }

    /**
     * Reads the exit records newer than the last one already stored, appends the abnormal ones to
     * the pending report text, and returns them. Safe to call repeatedly: the stored timestamp
     * only advances past the records that were read.
     */
    fun detect(context: Context): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        return synchronized(detectionLock) {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return emptyList()
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lastSeen = prefs.getLong(KEY_LAST_SEEN, 0L)

            val records = try {
                manager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_RECORDS)
            } catch (e: Exception) {
                AmberLog.e("ExitReasons", "Could not read process exit reasons", e)
                null
            }
            if (records.isNullOrEmpty()) return emptyList()

            val fresh = records
                .filter { it.timestamp > lastSeen && isAbnormal(it.reason, it.importance) }
                .map { describe(it) }

            prefs.edit {
                putLong(KEY_LAST_SEEN, records.maxOf { it.timestamp })
                if (fresh.isNotEmpty()) {
                    // Keep earlier unread records: a crash on startup must not erase the ANR
                    // that happened before it.
                    val previous = prefs.getString(KEY_PENDING, null)
                    val combined = listOfNotNull(previous?.takeIf { it.isNotBlank() }, fresh.joinToString("\n\n"))
                    putString(KEY_PENDING, combined.joinToString("\n\n"))
                }
            }
            fresh
        }
    }

    /** Abnormal exit records waiting to be shown to the user, oldest first. */
    fun pending(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PENDING, null)

    /** Drops the pending records once the user has seen (or sent) the report. */
    fun clearPending(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove(KEY_PENDING) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun describe(info: ApplicationExitInfo): String = buildString {
        append("Previous exit: ")
        append(reasonLabel(info.reason))
        append(" at ")
        append(timestampFormat.format(Date(info.timestamp)))
        append(" (importance=")
        append(info.importance)
        append(", pss=")
        append(info.pss)
        append("kB, rss=")
        append(info.rss)
        append("kB)")

        val description = info.description
        if (!description.isNullOrBlank()) {
            append("\n  ")
            append(CrashReports.redactKeys(description))
        }

        if (info.reason == ApplicationExitInfo.REASON_ANR) {
            anrTrace(info)?.let {
                append("\n  ANR trace (first lines):\n")
                append(CrashReports.redactKeys(it))
            }
        }
    }

    // ANRs ship the full traces file; only its first lines are useful in a chat message.
    @RequiresApi(Build.VERSION_CODES.R)
    private fun anrTrace(info: ApplicationExitInfo): String? = try {
        info.traceInputStream?.use { stream ->
            InputStreamReader(stream).use { reader ->
                val buffer = CharArray(MAX_TRACE_CHARS)
                val read = reader.read(buffer)
                if (read <= 0) null else String(buffer, 0, read)
            }
        }
    } catch (e: Exception) {
        AmberLog.e("ExitReasons", "Could not read ANR trace", e)
        null
    }
}
