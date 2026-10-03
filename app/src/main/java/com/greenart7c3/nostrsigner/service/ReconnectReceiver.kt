package com.greenart7c3.nostrsigner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.greenart7c3.nostrsigner.Amber
import kotlinx.coroutines.launch

class ReconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Amber.instance.applicationIOScope.launch {
            // Manual reconnect: redial every relay now, ignoring dead-relay state and
            // reconnect backoff, and re-add any relay previously dropped from the pool.
            Amber.instance.resetRelayConnections("manual reconnect")
        }
    }
}
