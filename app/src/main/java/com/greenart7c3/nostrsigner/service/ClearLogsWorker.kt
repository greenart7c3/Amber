package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.database.LogEntity
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.TimeUtils.ONE_WEEK
import kotlin.coroutines.cancellation.CancellationException

class ClearLogsWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        LocalPreferences.allSavedAccounts(Amber.instance).forEach {
            try {
                val now = System.currentTimeMillis()
                val oneWeek = now - (ONE_WEEK * 1000L)
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

                val database = Amber.instance.getDatabase(it.npub)
                database.dao().updateExpiredPermissions(TimeUtils.now())
                val deleted = database.dao().deleteOldApplications(now / 1000)
                if (deleted > 0) {
                    logDatabase.dao().insertLog(
                        LogEntity(
                            id = 0,
                            url = "",
                            type = "deleteApplications",
                            message = "Deleted $deleted expired applications",
                            time = now,
                        ),
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(Amber.TAG, "Error in ClearLogsWorker", e)
            }
        }
        return Result.success()
    }
}
