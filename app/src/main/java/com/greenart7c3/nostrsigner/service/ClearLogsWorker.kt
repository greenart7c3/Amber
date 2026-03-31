package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.database.LogEntity
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.coroutines.cancellation.CancellationException

private const val THREE_DAYS_MILLIS = 3 * 24 * 60 * 60 * 1000L
private const val MAX_LOG_ENTRIES = 1000
private const val MAX_HISTORY_ENTRIES = 2000

class ClearLogsWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        LocalPreferences.allSavedAccounts(Amber.instance).forEach {
            try {
                val now = System.currentTimeMillis()
                val threeDaysAgo = now - THREE_DAYS_MILLIS
                val oneWeekAgo = TimeUtils.oneWeekAgo()

                val historyDatabase = Amber.instance.getHistoryDatabase(it.npub)
                val historyDao = historyDatabase.dao()

                val deletedHistory = historyDao.deleteOldHistory(oneWeekAgo)
                if (deletedHistory > 0) {
                    Log.d(Amber.TAG, "Deleted $deletedHistory old history entries")
                }
                val excessHistory = historyDao.deleteExcessHistory(MAX_HISTORY_ENTRIES)
                if (excessHistory > 0) {
                    Log.d(Amber.TAG, "Trimmed $excessHistory excess history entries (cap: $MAX_HISTORY_ENTRIES)")
                }

                val logDatabase = Amber.instance.getLogDatabase(it.npub)
                val logDao = logDatabase.dao()

                val deletedLogs = logDao.deleteOldLog(threeDaysAgo)
                if (deletedLogs > 0) {
                    Log.d(Amber.TAG, "Deleted $deletedLogs old log entries")
                }
                val excessLogs = logDao.deleteExcessLogs(MAX_LOG_ENTRIES)
                if (excessLogs > 0) {
                    Log.d(Amber.TAG, "Trimmed $excessLogs excess log entries (cap: $MAX_LOG_ENTRIES)")
                }

                // Reclaim freed space from SQLite pages
                logDatabase.openHelper.writableDatabase.execSQL("VACUUM")
                historyDatabase.openHelper.writableDatabase.execSQL("VACUUM")

                val database = Amber.instance.getDatabase(it.npub)
                database.dao().updateExpiredPermissions(TimeUtils.now())
                val deleted = database.dao().deleteOldApplications(now / 1000)
                if (deleted > 0) {
                    logDao.insertLog(
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
