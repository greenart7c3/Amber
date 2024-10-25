package com.greenart7c3.nostrsigner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.greenart7c3.nostrsigner.BuildConfig

class BootReceiver : BroadcastReceiver() {
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
        }
    }
}
