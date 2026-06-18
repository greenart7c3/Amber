package com.greenart7c3.nostrsigner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    override fun onReceive(context: Context, intent: Intent) {
        if (BuildFlavorChecker.isOfflineFlavor()) return
        AmberLog.d(Amber.TAG, "Received intent: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_BOOT_COMPLETED,
            -> {
                AmberLog.d(Amber.TAG, "Received ${intent.action}")
                scope.launch {
                    val shouldStartService = LocalPreferences.getStartServiceOnBoot(context)
                    if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
                        !shouldStartService
                    ) {
                        AmberLog.d(Amber.TAG, "Skipping service start on boot (disabled in settings)")
                        return@launch
                    }
                    Amber.instance.startService()
                }
            }
        }
    }
}
