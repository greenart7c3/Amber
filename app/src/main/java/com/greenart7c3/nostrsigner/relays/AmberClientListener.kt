package com.greenart7c3.nostrsigner.relays

import android.util.Log
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.vitorpamplona.quartz.events.EventInterface
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AmberListenerSingleton {
    var accountStateViewModel: AccountStateViewModel? = null
    private var listener: AmberClientListener? = null

    fun setListener(
        onDone: () -> Unit,
        onLoading: (Boolean) -> Unit,
        accountStateViewModel: AccountStateViewModel?,
    ) {
        listener = AmberClientListener(onDone, onLoading, accountStateViewModel)
    }

    fun getListener(): AmberClientListener? {
        return listener
    }
}

class AmberClientListener(
    val onDone: () -> Unit,
    val onLoading: (Boolean) -> Unit,
    val accountStateViewModel: AccountStateViewModel?,
) : Client.Listener() {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onBeforeSend(relay: Relay, event: EventInterface) {
        super.onBeforeSend(relay, event)
        Log.d("AmberClientListener", "onBeforeSend: ${event.toJson()} ${relay.url}")
        GlobalScope.launch(Dispatchers.Default) {
            delay(10000)
            onLoading(false)
            Client.unsubscribe(this@AmberClientListener)
        }
    }

    override fun onSend(relay: Relay, event: EventInterface, success: Boolean) {
        super.onSend(relay, event, success)
        Log.d("AmberClientListener", "onSend: $success ${event.toJson()} ${relay.url}")
        if (!success) {
            onLoading(false)
            accountStateViewModel?.toast("Error", "Failed to send event. Try again.")
            Client.unsubscribe(this@AmberClientListener)
        }
    }

    override fun onSendResponse(eventId: String, success: Boolean, message: String, relay: Relay) {
        super.onSendResponse(eventId, success, message, relay)
        Log.d("AmberClientListener", "onSendResponse: $success $message ${relay.url}")
        onLoading(false)
        if (success) {
            onDone()
            accountStateViewModel?.toast("Success", "Event sent successfully")
        } else {
            accountStateViewModel?.toast("Error", message)
            Client.unsubscribe(this@AmberClientListener)
        }
    }

    override fun onError(error: Error, subscriptionId: String, relay: Relay) {
        super.onError(error, subscriptionId, relay)
        onLoading(false)
        accountStateViewModel?.toast("Error", error.message ?: "Unknown error")
        Client.unsubscribe(this@AmberClientListener)
    }
}
