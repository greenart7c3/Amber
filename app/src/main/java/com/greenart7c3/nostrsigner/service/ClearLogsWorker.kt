package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.TimeUtils.ONE_WEEK
import kotlin.coroutines.cancellation.CancellationException

class ClearLogsWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        LocalPreferences.allSavedAccounts(Amber.instance).forEach {
            try {
                val oneWeek = System.currentTimeMillis() - (ONE_WEEK * 1000L)
                val oneWeekAgo = TimeUtils.oneWeekAgo()
                val historyDatabase = Amber.instance.getHistoryDatabase(it.npub)
                val deletedHistory = historyDatabase.dao().deleteOldHistory(oneWeekAgo)
                if (deletedHistory > 0) {
                    Log.d(Amber.TAG, "Deleted $deletedHistory old history entries")
                }

                val logDatabase = Amber.instance.getLogDatabase(it.npub)
                val deletedLogs = logDatabase.dao().deleteOldLog(oneWeek)
                if (deletedLogs > 0) {
                    Log.d(Amber.TAG, "Deleted $deletedLogs old log entries")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(Amber.TAG, "Error deleting old log entries", e)
            }
        }
        return Result.success()
    }
}
