package com.greenart7c3.nostrsigner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.relays.RelayHealthTracker
import kotlinx.coroutines.launch

class ReconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Amber.instance.applicationIOScope.launch {
            // Manual reconnect: clear dead-relay state and re-run the filters so any
            // relay previously dropped from the pool is re-added before reconnecting.
            RelayHealthTracker.reset()
            Amber.instance.checkForNewRelaysAndUpdateAllFilters(shouldReconnect = true)
        }
    }
}
