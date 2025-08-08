package com.greenart7c3.nostrsigner.service

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class RelayDisconnectService(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun doWork(): Result {
        val job =
            ioScope.launch {
                val url = inputData.getString("relay")
                delay(60000)
                url?.let {
                    // TODO: this class is not being used, but this new way of using Nostr client requires removing the relay from all filters to disconnect it
                    // val relay = Amber.instance.client.getRelay(it)
                    // if (relay != null && relay.isConnected()) {
                    //    LocalPreferences.currentAccount(applicationContext)?.let { npub ->
                    //        Amber.instance.getDatabase(npub).applicationDao().insertLog(
                    //            LogEntity(
                    //                id = 0,
                    //                url = relay.url,
                    //                type = "onRelayStateChange",
                    //                message = "DISCONNECT",
                    //                time = System.currentTimeMillis(),
                    //            ),
                    //        )
                    //    }
                    //    relay.disconnect()
                    // }
                }
            }
        runBlocking { job.join() }
        return Result.success()
    }
}
