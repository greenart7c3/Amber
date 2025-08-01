package com.greenart7c3.nostrsigner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.greenart7c3.nostrsigner.Amber
import kotlinx.coroutines.launch

class ReconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Amber.instance.applicationIOScope.launch {
            Amber.instance.reconnect()
        }
    }
}
