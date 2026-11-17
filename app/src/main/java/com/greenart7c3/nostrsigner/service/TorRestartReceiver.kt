package com.greenart7c3.nostrsigner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.greenart7c3.nostrsigner.amber
import com.greenart7c3.nostrsigner.models.TorMode

class TorRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val amber = context.amber
        if (amber.settings.torMode != TorMode.BUILTIN) return
        TorManager.restart(context.applicationContext, amber.applicationIOScope)
    }
}
