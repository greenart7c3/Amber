package com.greenart7c3.nostrsigner.relays

import android.annotation.SuppressLint
import android.content.Context
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.vitorpamplona.ammolite.relays.Client
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventInterface

@SuppressLint("StaticFieldLeak")
object AmberListenerSingleton {
    var accountStateViewModel: AccountStateViewModel? = null
    private var listener: AmberClientListener? = null

    fun setListener(
        context: Context,
        accountStateViewModel: AccountStateViewModel?,
    ) {
        listener = AmberClientListener(context, accountStateViewModel)
    }

    fun getListener(): AmberClientListener? {
        return listener
    }
}

class AmberClientListener(
    val context: Context,
    val accountStateViewModel: AccountStateViewModel?,
) : Client.Listener {
    override fun onAuth(relay: Relay, challenge: String) {
        LocalPreferences.currentAccount(context)?.let { account ->
            NostrSigner.getInstance().getDatabase(account).applicationDao().insertLog(
                LogEntity(
                    id = 0,
                    url = relay.url,
                    type = "onAuth",
                    message = "Authenticating",
                    time = System.currentTimeMillis(),
                ),
            )
        }
    }

    override fun onBeforeSend(relay: Relay, event: EventInterface) {
        LocalPreferences.currentAccount(context)?.let { account ->
            NostrSigner.getInstance().getDatabase(account).applicationDao().insertLog(
                LogEntity(
                    id = 0,
                    url = relay.url,
                    type = "onBeforeSend",
                    message = "Sending event ${event.id()}",
                    time = System.currentTimeMillis(),
                ),
            )
        }
    }

    override fun onSend(relay: Relay, msg: String, success: Boolean) {
        LocalPreferences.currentAccount(context)?.let { account ->
            NostrSigner.getInstance().getDatabase(account).applicationDao().insertLog(
                LogEntity(
                    id = 0,
                    url = relay.url,
                    type = "onSend",
                    message = "message: $msg success: $success",
                    time = System.currentTimeMillis(),
                ),
            )
        }
        if (!success) {
            if (msg.isNotBlank()) {
                accountStateViewModel?.toast("Error", "Failed to send event.\n$msg")
            } else {
                accountStateViewModel?.toast("Error", "Failed to send event. Try again.")
            }
        }
    }

    override fun onSendResponse(eventId: String, success: Boolean, message: String, relay: Relay) {
        LocalPreferences.currentAccount(context)?.let { account ->
            NostrSigner.getInstance().getDatabase(account).applicationDao().insertLog(
                LogEntity(
                    id = 0,
                    url = relay.url,
                    type = "onSendResponse",
                    message = "Success: $success Message: $message",
                    time = System.currentTimeMillis(),
                ),
            )
        }

        if (!success) {
            accountStateViewModel?.toast("Error", message)
        }
    }

    override fun onError(error: Error, subscriptionId: String, relay: Relay) {
        if (error.message?.trim()?.equals("Relay sent notice:") == true) return
        LocalPreferences.currentAccount(context)?.let { account ->
            NostrSigner.getInstance().getDatabase(account).applicationDao().insertLog(
                LogEntity(
                    id = 0,
                    url = relay.url,
                    type = "onError",
                    message = "${error.message}",
                    time = System.currentTimeMillis(),
                ),
            )
        }

        accountStateViewModel?.toast("Error", error.message ?: "Unknown error")
    }

    override fun onEvent(event: Event, subscriptionId: String, relay: Relay, afterEOSE: Boolean) {
        LocalPreferences.currentAccount(context)?.let { account ->
            NostrSigner.getInstance().getDatabase(account).applicationDao().insertLog(
                LogEntity(
                    id = 0,
                    url = relay.url,
                    type = "onEvent",
                    message = "Received event ${event.id()} from subscription $subscriptionId afterEOSE: $afterEOSE",
                    time = System.currentTimeMillis(),
                ),
            )
        }
    }

    override fun onNotify(relay: Relay, description: String) {
        LocalPreferences.currentAccount(context)?.let { account ->
            NostrSigner.getInstance().getDatabase(account).applicationDao().insertLog(
                LogEntity(
                    id = 0,
                    url = relay.url,
                    type = "onNotify",
                    message = description,
                    time = System.currentTimeMillis(),
                ),
            )
        }
    }

    override fun onRelayStateChange(type: Relay.StateType, relay: Relay, subscriptionId: String?) {
        LocalPreferences.currentAccount(context)?.let { account ->
            NostrSigner.getInstance().getDatabase(account).applicationDao().insertLog(
                LogEntity(
                    id = 0,
                    url = relay.url,
                    type = "onRelayStateChange",
                    message = type.name,
                    time = System.currentTimeMillis(),
                ),
            )
        }
    }
}
