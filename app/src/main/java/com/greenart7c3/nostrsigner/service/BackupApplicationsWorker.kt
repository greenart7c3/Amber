package com.greenart7c3.nostrsigner.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import kotlin.coroutines.cancellation.CancellationException

class BackupApplicationsWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (BuildFlavorChecker.isOfflineFlavor()) return Result.success()

        LocalPreferences.allSavedAccounts(applicationContext).forEach { info ->
            if (!LocalPreferences.getBackupApplications(applicationContext, info.npub)) return@forEach
            try {
                val account = LocalPreferences.loadFromEncryptedStorage(applicationContext, info.npub) ?: return@forEach
                ApplicationBackup.publishBackup(info.npub, account)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // With requireUnlockedDevice enabled the gate refuses signing
                // while the device is locked — usually the case for a daily
                // background worker. Retry later instead of silently skipping
                // the day's backup.
                if (e is SecurityException) {
                    AmberLog.d(Amber.TAG, "BackupApplicationsWorker: device locked, retrying ${info.npub} later")
                    return Result.retry()
                }
                AmberLog.e(Amber.TAG, "BackupApplicationsWorker: failed for ${info.npub}", e)
            }
        }

        return Result.success()
    }
}
