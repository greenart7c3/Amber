package com.greenart7c3.nostrsigner.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.amber
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.database.LogRetentionManager
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

private const val THREE_DAYS_MILLIS = 3 * 24 * 60 * 60 * 1000L

class ClearLogsWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    val amber = applicationContext.amber
    override suspend fun doWork(): Result {
        SavedAccounts.all(amber).forEach {
            try {
                val now = System.currentTimeMillis()
                val threeDaysAgo = now - THREE_DAYS_MILLIS

                LogRetentionManager.prune(amber, amber.accountDatabases, it.npub)

                val bunkerEventDao = amber.accountDatabases.getDatabase(it.npub).bunkerEventDao()
                val deletedBunkerEvents = bunkerEventDao.deleteOld(threeDaysAgo / 1000)
                if (deletedBunkerEvents > 0) {
                    AmberLog.d(Amber.TAG, "Deleted $deletedBunkerEvents old bunker event entries")
                }

                val dao = amber.accountDatabases.daos(it.npub).permissionMaintenanceDao
                dao.updateExpiredPermissions(TimeUtils.now())
                val deleted = dao.deleteOldApplications(now / 1000)
                if (deleted > 0) {
                    amber.accountDatabases.getLogDatabase(it.npub).dao().insertLogIfEnabled(
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

    companion object {
        /** Cleans up once now and then every day; KEEP leaves a run or schedule that already exists alone. */
        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniqueWork(
                "ClearLogsWorkerOneTime",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ClearLogsWorker>().addTag("clearLogsWorkOneTime").build(),
            )
            workManager.enqueueUniquePeriodicWork(
                "ClearLogsWorker",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ClearLogsWorker>(24, TimeUnit.HOURS).addTag("clearLogsWork").build(),
            )
        }
    }
}
