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

class BootReceiver(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (BuildFlavorChecker.isOfflineFlavor()) return
        AmberLog.d(Amber.TAG, "Received intent: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_BOOT_COMPLETED,
            -> {
                AmberLog.d(Amber.TAG, "Received ${intent.action}")
                scope.launch {
                    if (!LocalPreferences.getStartServiceOnBoot(context)) {
                        AmberLog.d(Amber.TAG, "Skipping service start (${intent.action}) (disabled in settings)")
                        return@launch
                    }
                    Amber.instance.startService()
                }
            }
        }
    }
}
