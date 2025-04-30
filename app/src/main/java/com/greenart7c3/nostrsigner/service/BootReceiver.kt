package com.greenart7c3.nostrsigner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.TimeUtils.ONE_WEEK
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    companion object {
        const val CLEAR_LOGS_ACTION = "CLEAR_AMBER_LOGS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("KotlinConstantConditions")
        if (BuildConfig.FLAVOR == "offline") return

        when (intent.action) {
            Intent.ACTION_PACKAGE_REPLACED -> {
                if (intent.dataString?.contains("com.greenart7c3.nostrsigner") == true && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    context.startForegroundService(
                        Intent(
                            context,
                            ConnectivityService::class.java,
                        ),
                    )
                }
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                context.startForegroundService(
                    Intent(
                        context,
                        ConnectivityService::class.java,
                    ),
                )
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                context.startForegroundService(
                    Intent(
                        context,
                        ConnectivityService::class.java,
                    ),
                )
            }
            CLEAR_LOGS_ACTION -> {
                Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
                    LocalPreferences.allSavedAccounts(Amber.instance).forEach {
                        Amber.instance.getDatabase(it.npub).let { database ->
                            try {
                                val oneWeek = System.currentTimeMillis() - ONE_WEEK
                                val oneWeekAgo = TimeUtils.oneWeekAgo()
                                val countHistory = database.applicationDao().countOldHistory(oneWeekAgo)
                                Log.d("NostrSigner", "Deleting $countHistory old history entries")
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

                                val countNotification = database.applicationDao().countOldNotification(oneWeekAgo)
                                Log.d("NostrSigner", "Deleting $countNotification old notification entries")
                                if (countNotification > 0) {
                                    var logs = database.applicationDao().getOldNotification(oneWeekAgo)
                                    var count = 0
                                    while (logs.isNotEmpty()) {
                                        count++
                                        logs.forEach { history ->
                                            database.applicationDao().deleteNotification(history)
                                        }
                                        logs = database.applicationDao().getOldNotification(oneWeekAgo)
                                    }
                                }

                                val countLog = database.applicationDao().countOldLog(oneWeek)
                                Log.d("NostrSigner", "Deleting $countLog old log entries")
                                if (countLog > 0) {
                                    var logs = database.applicationDao().getOldLog(oneWeek)
                                    var count = 0
                                    while (logs.isNotEmpty()) {
                                        count++
                                        logs.forEach { history ->
                                            database.applicationDao().deleteLog(history)
                                        }
                                        logs = database.applicationDao().getOldLog(oneWeek)
                                    }
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Log.e("NostrSigner", "Error deleting old log entries", e)
                            }
                        }
                    }
                }
            }
        }
    }
}
