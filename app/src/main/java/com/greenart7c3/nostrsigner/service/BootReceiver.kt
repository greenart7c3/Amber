package com.greenart7c3.nostrsigner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
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
        }
    }
}
