package com.greenart7c3.nostrsigner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    override fun onReceive(context: Context, intent: Intent) {
        if (BuildFlavorChecker.isOfflineFlavor()) return
        Log.d(Amber.TAG, "Received intent: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_BOOT_COMPLETED,
            -> {
                Log.d(Amber.TAG, "Received ${intent.action}")
                scope.launch {
                    val shouldStartService = LocalPreferences.getStartServiceOnBoot(context)
                    if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
                        !shouldStartService
                    ) {
                        Log.d(Amber.TAG, "Skipping service start on boot (disabled in settings)")
                        return@launch
                    }
                    Amber.instance.isStartingAppState.first { !it }
                    Amber.instance.startService()
                }
            }
        }
    }
}
