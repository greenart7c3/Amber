package com.greenart7c3.nostrsigner.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.database.LogRetentionManager
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.coroutines.cancellation.CancellationException

private const val THREE_DAYS_MILLIS = 3 * 24 * 60 * 60 * 1000L

class ClearLogsWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        LocalPreferences.allSavedAccounts(Amber.instance).forEach {
            try {
                val now = System.currentTimeMillis()
                val threeDaysAgo = now - THREE_DAYS_MILLIS

                LogRetentionManager.prune(it.npub)

                val bunkerEventDao = Amber.instance.getDatabase(it.npub).bunkerEventDao()
                val deletedBunkerEvents = bunkerEventDao.deleteOld(threeDaysAgo / 1000)
                if (deletedBunkerEvents > 0) {
                    AmberLog.d(Amber.TAG, "Deleted $deletedBunkerEvents old bunker event entries")
                }

                val dao = Amber.instance.dao(it.npub)
                dao.updateExpiredPermissions(TimeUtils.now())
                val deleted = dao.deleteOldApplications(now / 1000)
                if (deleted > 0) {
                    Amber.instance.getLogDatabase(it.npub).dao().insertLogIfEnabled(
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
                AmberLog.e(Amber.TAG, "Error in ClearLogsWorker", e)
            }
        }
        return Result.success()
    }
}
