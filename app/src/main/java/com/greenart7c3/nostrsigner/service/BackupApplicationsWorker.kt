package com.greenart7c3.nostrsigner.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.greenart7c3.nostrsigner.AccountPreferences
import com.greenart7c3.nostrsigner.AccountStorage
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.amber
import com.greenart7c3.nostrsigner.anyAccountBackupEnabled
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

class BackupApplicationsWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val amber = applicationContext.amber
        if (BuildFlavorChecker.isOfflineFlavor()) return Result.success()

        SavedAccounts.all(applicationContext).forEach { info ->
            if (!AccountPreferences.getBackupApplications(applicationContext, info.npub)) return@forEach
            try {
                val account = AccountStorage.load(applicationContext, info.npub) ?: return@forEach
                ApplicationBackup.publishBackup(info.npub, account, amber.accountDatabases, amber.relayClient, amber)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val requireUnlockedDeviceRefusedWhileLocked = e is SecurityException
                if (requireUnlockedDeviceRefusedWhileLocked) {
                    AmberLog.d(Amber.TAG, "BackupApplicationsWorker: device locked, retrying ${info.npub} later")
                    return Result.retry()
                }
                AmberLog.e(Amber.TAG, "BackupApplicationsWorker: failed for ${info.npub}", e)
            }
        }

        return Result.success()
    }

    companion object {
        /** Schedules the daily applications backup, when any account has it on (never in the offline flavor). */
        fun schedule(context: Context) {
            if (BuildFlavorChecker.isOfflineFlavor()) return
            if (!anyAccountBackupEnabled(context)) return

            val periodicRequest = PeriodicWorkRequestBuilder<BackupApplicationsWorker>(1, TimeUnit.DAYS)
                .addTag("backupApplicationsWork")
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "BackupApplicationsWorker",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest,
            )
        }

        fun cancel(context: Context) {
            if (BuildFlavorChecker.isOfflineFlavor()) return
            WorkManager.getInstance(context).cancelUniqueWork("BackupApplicationsWorker")
        }
    }
}
