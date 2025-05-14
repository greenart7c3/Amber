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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    companion object {
        const val CLEAR_LOGS_ACTION = "CLEAR_AMBER_LOGS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("KotlinConstantConditions")
        if (BuildConfig.FLAVOR == "offline") return
        Log.d(Amber.TAG, "Received intent: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.d(Amber.TAG, "Received ACTION_PACKAGE_REPLACED")
                if (intent.dataString?.contains("com.greenart7c3.nostrsigner") == true && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    Amber.instance.applicationIOScope.launch {
                        while (Amber.instance.isStartingAppState.value) {
                            delay(1000)
                        }
                        Amber.instance.startService()
                    }
                }
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(Amber.TAG, "Received ACTION_MY_PACKAGE_REPLACED")
                Amber.instance.applicationIOScope.launch {
                    while (Amber.instance.isStartingAppState.value) {
                        delay(1000)
                    }
                    Amber.instance.startService()
                }
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(Amber.TAG, "Received ACTION_BOOT_COMPLETED")
                Amber.instance.applicationIOScope.launch {
                    while (Amber.instance.isStartingAppState.value) {
                        delay(1000)
                    }
                    Amber.instance.startService()
                }
            }
            CLEAR_LOGS_ACTION -> {
                Log.d(Amber.TAG, "Received CLEAR_LOGS_ACTION")
                Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
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

                                val countNotification = database.applicationDao().countOldNotification(oneWeekAgo)
                                Log.d(Amber.TAG, "Deleting $countNotification old notification entries")
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
                }
            }
        }
    }
}
