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

class ClearLogsWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        LocalPreferences.allSavedAccounts(Amber.instance).forEach {
            try {
                val oneWeek = System.currentTimeMillis() - (ONE_WEEK * 1000L)
                val oneWeekAgo = TimeUtils.oneWeekAgo()
                val historyDatabase = Amber.instance.getHistoryDatabase(it.npub)
                val countHistory = historyDatabase.dao().countOldHistory(oneWeekAgo)
                Log.d(Amber.TAG, "Deleting $countHistory old history entries")
                if (countHistory > 0) {
                    var logs = historyDatabase.dao().getOldHistory(oneWeekAgo)
                    var count = 0
                    while (logs.isNotEmpty()) {
                        count++
                        logs.forEach { history ->
                            historyDatabase.dao().deleteHistory(history)
                        }
                        logs = historyDatabase.dao().getOldHistory(oneWeekAgo)
                    }
                }

                val logDatabase = Amber.instance.getLogDatabase(it.npub)
                val countLog = logDatabase.logDao().countOldLog(oneWeek)
                Log.d(Amber.TAG, "Deleting $countLog old log entries from ${com.greenart7c3.nostrsigner.models.TimeUtils.formatLongToCustomDateTimeWithSeconds(oneWeek)}")
                if (countLog > 0) {
                    var logs = logDatabase.logDao().getOldLog(oneWeek)
                    var count = 0
                    while (logs.isNotEmpty()) {
                        count++
                        logs.forEach { history ->
                            Log.d(Amber.TAG, "Deleting log entry ${com.greenart7c3.nostrsigner.models.TimeUtils.formatLongToCustomDateTimeWithSeconds(history.time)}")
                            logDatabase.logDao().deleteLog(history)
                        }
                        logs = logDatabase.logDao().getOldLog(oneWeek)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(Amber.TAG, "Error deleting old log entries", e)
            }
        }
        return Result.success()
    }
}
