package com.greenart7c3.nostrsigner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.amber
import kotlinx.coroutines.launch

class KillSwitchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val amber = context.amber
        amber.applicationIOScope.launch {
            amber.settings.killSwitch.emit(!amber.settings.killSwitch.value)
            LocalPreferences.saveSettingsToEncryptedStorage(amber.settings)
            if (amber.settings.killSwitch.value) {
                amber.client.disconnect()
            } else {
                amber.checkForNewRelaysAndUpdateAllFilters(shouldReconnect = true)
            }
        }
    }
}
