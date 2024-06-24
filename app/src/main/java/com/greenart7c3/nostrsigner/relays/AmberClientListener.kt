@file:OptIn(DelicateCoroutinesApi::class)

package com.greenart7c3.nostrsigner.relays

import android.util.Log
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.RelayPool
import com.vitorpamplona.quartz.events.Event
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
) : RelayPool.Listener {
    override fun onAuth(relay: Relay, challenge: String) {
        Log.d("AmberClientListener", "onAuth: $challenge ${relay.url}")
    }

    override fun onBeforeSend(relay: Relay, event: EventInterface) {
        Log.d("AmberClientListener", "onBeforeSend: ${event.toJson()} ${relay.url}")
        GlobalScope.launch(Dispatchers.Default) {
            delay(10000)
            onLoading(false)
            RelayPool.unregister(this@AmberClientListener)
        }
    }

    override fun onSend(relay: Relay, msg: String, success: Boolean) {
        Log.d("AmberClientListener", "onSend: $success $msg ${relay.url}")
        if (!success) {
            onLoading(false)
            accountStateViewModel?.toast("Error", "Failed to send event. Try again.")
            RelayPool.unregister(this@AmberClientListener)
        }
    }

    override fun onSendResponse(eventId: String, success: Boolean, message: String, relay: Relay) {
        Log.d("AmberClientListener", "onSendResponse: $success $message ${relay.url}")
        onLoading(false)
        if (success) {
            onDone()
            accountStateViewModel?.toast("Success", "Event sent successfully")
        } else {
            accountStateViewModel?.toast("Error", message)
            RelayPool.unregister(this@AmberClientListener)
        }
    }

    override fun onError(error: Error, subscriptionId: String, relay: Relay) {
        onLoading(false)
        accountStateViewModel?.toast("Error", error.message ?: "Unknown error")
        RelayPool.unregister(this@AmberClientListener)
    }

    override fun onEvent(event: Event, subscriptionId: String, relay: Relay, afterEOSE: Boolean) {
        Log.d("AmberClientListener", "onEvent: ${event.toJson()} ${relay.url}")
    }

    override fun onNotify(relay: Relay, description: String) {
        Log.d("AmberClientListener", "onNotify: $description ${relay.url}")
    }

    override fun onRelayStateChange(type: Relay.StateType, relay: Relay, channel: String?) {
        Log.d("AmberClientListener", "onRelayStateChange: $type ${relay.url}")
    }
}
