package com.greenart7c3.nostrsigner.data

import kotlinx.coroutines.flow.MutableStateFlow

/** Reports waiting for the user to send or dismiss them. */
interface PendingReports {
    /** The crash report the last run left behind (set when the main screen finds one), until the user sends or dismisses it. */
    var pendingCrashReport: String?

    /** Drops the crash report and the exit records it included. */
    fun clearPendingCrashReport()

    /** An untranslated-text report, set by the screen that found it and read by the report screen. */
    val pendingTranslationReport: MutableStateFlow<String?>
}
