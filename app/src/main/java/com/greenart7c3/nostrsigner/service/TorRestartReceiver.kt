package com.greenart7c3.nostrsigner.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.models.TorMode

class TorRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Amber.instance.settings.torMode != TorMode.BUILTIN) return
        TorManager.restart(context.applicationContext, Amber.instance.applicationIOScope)
    }
}
