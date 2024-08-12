package com.greenart7c3.nostrsigner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.greenart7c3.nostrsigner.BuildConfig

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("KotlinConstantConditions")
        if (BuildConfig.FLAVOR == "offline") return

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Starting ConnectivityService")
            context.startForegroundService(
                Intent(
                    context,
                    ConnectivityService::class.java,
                ),
            )
        }
    }
}
