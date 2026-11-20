package com.greenart7c3.nostrsigner.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.amber
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private val CHECK_TIMEOUT = 30.seconds

class UpdateCheckWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (BuildFlavorChecker.isOfflineFlavor() || BuildFlavorChecker.isFdroidBuild()) return Result.success()

        val amber = applicationContext.amber
        if (!amber.settings.autoCheckUpdates) return Result.success()

        val updater = amber.zapstoreUpdater ?: return Result.success()

        try {
            amber.maybeCheckForUpdates()

            // If a check was actually started, wait for it to finish so the notification fires
            // before WorkManager considers the job done.
            if (updater.isChecking.value) {
                withTimeoutOrNull(CHECK_TIMEOUT) {
                    updater.isChecking.first { !it }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AmberLog.e(Amber.TAG, "UpdateCheckWorker: error checking for updates", e)
        }

        return Result.success()
    }

    companion object {
        /** Checks Zapstore for an update once a day (never in the offline flavor or the F-Droid build). */
        fun schedule(context: Context) {
            if (BuildFlavorChecker.isOfflineFlavor() || BuildFlavorChecker.isFdroidBuild()) return

            val periodicRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .addTag("updateCheckWork")
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("UpdateCheckWorker", ExistingPeriodicWorkPolicy.KEEP, periodicRequest)
        }
    }
}
