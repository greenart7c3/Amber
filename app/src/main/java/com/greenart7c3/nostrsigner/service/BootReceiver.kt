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

        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val packageManager = context.packageManager
            val intent2 = packageManager.getLaunchIntentForPackage("com.greenart7c3.nostrsigner.debug")
            if (intent2 != null) {
                intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(intent2)
                Runtime.getRuntime().exit(0)
            }
        }
    }
}
