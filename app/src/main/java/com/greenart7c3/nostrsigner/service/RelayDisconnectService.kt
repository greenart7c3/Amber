package com.greenart7c3.nostrsigner.service

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.database.LogEntity
import com.vitorpamplona.ammolite.relays.RelayPool
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
                    val relay = RelayPool.getRelay(it)
                    if (relay != null && relay.isConnected()) {
                        LocalPreferences.currentAccount(applicationContext)?.let { npub ->
                            NostrSigner.getInstance().getDatabase(npub).applicationDao().insertLog(
                                LogEntity(
                                    id = 0,
                                    url = relay.url,
                                    type = "onRelayStateChange",
                                    message = "DISCONNECT",
                                    time = System.currentTimeMillis(),
                                ),
                            )
                        }
                        relay.disconnect()
                    }
                }
            }
        runBlocking { job.join() }
        return Result.success()
    }
}
