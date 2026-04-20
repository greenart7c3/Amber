package com.greenart7c3.nostrsigner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.greenart7c3.nostrsigner.Amber
import kotlin.system.exitProcess

class StopServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(Amber.TAG, "StopServiceReceiver: stopping service and killing process")

        try {
            Amber.instance.client.disconnect()
        } catch (e: Exception) {
            Log.e(Amber.TAG, "Failed to disconnect client on stop", e)
        }

        try {
            context.stopService(Intent(context, ConnectivityService::class.java))
        } catch (e: Exception) {
            Log.e(Amber.TAG, "Failed to stop ConnectivityService", e)
        }

        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancelAll()

        Process.killProcess(Process.myPid())
        exitProcess(0)
    }
}
