package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val CHECK_TIMEOUT_MS = 30_000L

class UpdateCheckWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (BuildFlavorChecker.isOfflineFlavor() || BuildConfig.IS_FDROID_BUILD) return Result.success()

        val amber = Amber.instance
        if (!amber.settings.autoCheckUpdates) return Result.success()

        val updater = amber.zapstoreUpdater ?: return Result.success()

        // Wait for app initialization to complete before checking
        amber.isStartingAppState.first { !it }

        try {
            amber.maybeCheckForUpdates()

            // If a check was actually started, wait for it to finish so the notification fires
            // before WorkManager considers the job done.
            if (updater.isChecking.value) {
                withTimeoutOrNull(CHECK_TIMEOUT_MS) {
                    updater.isChecking.first { !it }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(Amber.TAG, "UpdateCheckWorker: error checking for updates", e)
        }

        return Result.success()
    }
}
