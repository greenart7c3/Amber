package com.greenart7c3.nostrsigner.service.crashreports

import android.content.Context
import com.greenart7c3.nostrsigner.data.PendingReports
import kotlinx.coroutines.flow.MutableStateFlow

/** The app's [PendingReports]: one instance, held by Amber for the life of the process. */
class AppPendingReports(
    // The Application context: the exit records live in its preferences.
    private val appContext: Context,
) : PendingReports {
    override var pendingCrashReport: String? = null

    override val pendingTranslationReport = MutableStateFlow<String?>(null)

    override fun clearPendingCrashReport() {
        pendingCrashReport = null
        ExitReasons.clearPending(appContext)
    }
}
