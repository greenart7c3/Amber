package com.greenart7c3.nostrsigner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.greenart7c3.nostrsigner.amber
import kotlinx.coroutines.launch

class ReconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val amber = context.amber
        amber.applicationIOScope.launch {
            // Manual reconnect: redial every relay now, ignoring dead-relay state and
            // reconnect backoff, and re-add any relay previously dropped from the pool.
            amber.relayClient.resetRelayConnections("manual reconnect")
        }
    }
}
