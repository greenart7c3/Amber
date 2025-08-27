package com.greenart7c3.nostrsigner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import kotlinx.coroutines.launch

class KillSwitchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Amber.instance.applicationIOScope.launch {
            Amber.instance.settings.killSwitch.emit(!Amber.instance.settings.killSwitch.value)
            LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
            if (Amber.instance.settings.killSwitch.value) {
                Amber.instance.client.disconnect()
            } else {
                Amber.instance.checkForNewRelaysAndUpdateAllFilters(shouldReconnect = true)
            }
        }
    }
}
