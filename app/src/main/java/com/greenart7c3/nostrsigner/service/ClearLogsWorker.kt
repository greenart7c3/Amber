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
            Amber.instance.getDatabase(it.npub).let { database ->
                try {
                    val oneWeek = System.currentTimeMillis() - (ONE_WEEK * 1000L)
                    val oneWeekAgo = TimeUtils.oneWeekAgo()
                    val countHistory = database.applicationDao().countOldHistory(oneWeekAgo)
                    Log.d(Amber.TAG, "Deleting $countHistory old history entries")
                    if (countHistory > 0) {
                        var logs = database.applicationDao().getOldHistory(oneWeekAgo)
                        var count = 0
                        while (logs.isNotEmpty()) {
                            count++
                            logs.forEach { history ->
                                database.applicationDao().deleteHistory(history)
                            }
                            logs = database.applicationDao().getOldHistory(oneWeekAgo)
                        }
                    }

                    val countLog = database.applicationDao().countOldLog(oneWeek)
                    Log.d(Amber.TAG, "Deleting $countLog old log entries from ${com.greenart7c3.nostrsigner.models.TimeUtils.formatLongToCustomDateTimeWithSeconds(oneWeek)}")
                    if (countLog > 0) {
                        var logs = database.applicationDao().getOldLog(oneWeek)
                        var count = 0
                        while (logs.isNotEmpty()) {
                            count++
                            logs.forEach { history ->
                                Log.d(Amber.TAG, "Deleting log entry ${com.greenart7c3.nostrsigner.models.TimeUtils.formatLongToCustomDateTimeWithSeconds(history.time)}")
                                database.applicationDao().deleteLog(history)
                            }
                            logs = database.applicationDao().getOldLog(oneWeek)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(Amber.TAG, "Error deleting old log entries", e)
                }
            }
        }
        return Result.success()
    }
}
