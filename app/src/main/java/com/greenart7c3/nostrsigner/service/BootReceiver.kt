package com.greenart7c3.nostrsigner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (BuildFlavorChecker.isOfflineFlavor()) return
        Log.d(Amber.TAG, "Received intent: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_BOOT_COMPLETED,
            -> {
                Log.d(Amber.TAG, "Received ${intent.action}")
                Amber.instance.applicationIOScope.launch {
                    Amber.instance.isStartingAppState.first { !it }
                    Amber.instance.startService()
                }
            }
        }
    }
}
