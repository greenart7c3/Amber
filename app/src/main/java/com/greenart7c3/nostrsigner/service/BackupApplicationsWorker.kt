package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import kotlin.coroutines.cancellation.CancellationException

class BackupApplicationsWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (BuildFlavorChecker.isOfflineFlavor()) return Result.success()

        LocalPreferences.allSavedAccounts(applicationContext).forEach { info ->
            try {
                val account = LocalPreferences.loadFromEncryptedStorage(applicationContext, info.npub) ?: return@forEach
                if (!account.backupApplications) return@forEach
                ApplicationBackup.publishBackup(info.npub, account)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(Amber.TAG, "BackupApplicationsWorker: failed for ${info.npub}", e)
            }
        }

        return Result.success()
    }
}
