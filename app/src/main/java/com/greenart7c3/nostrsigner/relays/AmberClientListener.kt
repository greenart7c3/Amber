package com.greenart7c3.nostrsigner.relays

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.service.NotificationUtils.sendErrorNotification
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.RelayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
object AmberListenerSingleton {
    var accountStateViewModel: AccountStateViewModel? = null
    private var listener: AmberClientListener? = null
    val latestErrorMessages = mutableListOf<String>()

    fun setListener(
        context: Context,
    ) {
        listener = AmberClientListener(context)
    }

    fun getListener(): AmberClientListener? {
        return listener
    }

    fun showErrorMessage() {
        if (latestErrorMessages.isEmpty()) return
        if (latestErrorMessages.last().isBlank()) return
        if (Amber.isAppInForeground) {
            accountStateViewModel?.toast("Error", latestErrorMessages.last())
        } else {
            val notificationManager: NotificationManager =
                Amber.instance.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.sendErrorNotification(
                id = latestErrorMessages.last().hashCode().toString(),
                channelId = "ErrorID",
                messageTitle = "Error sending bunker response",
                messageBody = latestErrorMessages.last(),
                picture = null,
                applicationContext = Amber.instance.applicationContext,
            )
        }
        latestErrorMessages.clear()
    }
}

class AmberClientListener(
    val context: Context,
) : NostrClient.Listener {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onAuth(relay: Relay, challenge: String) {
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getDatabase(account).applicationDao().insertLog(
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
    }

    override fun onBeforeSend(relay: Relay, event: Event) {
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getDatabase(account).applicationDao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url,
                        type = "onBeforeSend",
                        message = "Sending event ${event.id}",
                        time = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    override fun onSend(relay: Relay, msg: String, success: Boolean) {
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getDatabase(account).applicationDao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url,
                        type = "onSend",
                        message = "message: $msg success: $success",
                        time = System.currentTimeMillis(),
                    ),
                )
            }
        }
        if (!success) {
            if (msg.isNotBlank()) {
                AmberListenerSingleton.latestErrorMessages.add("Failed to send event.\n$msg")
            } else {
                AmberListenerSingleton.latestErrorMessages.add("Failed to send event. Try again.")
            }
        }
    }

    override fun onSendResponse(eventId: String, success: Boolean, message: String, relay: Relay) {
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getDatabase(account).applicationDao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url,
                        type = "onSendResponse",
                        message = "Success: $success Message: $message",
                        time = System.currentTimeMillis(),
                    ),
                )
            }
        }

        if (!success) {
            AmberListenerSingleton.latestErrorMessages.add(message)
        }
    }

    override fun onError(error: Error, subscriptionId: String, relay: Relay) {
        if (error.message?.trim()?.equals("Relay sent notice:") == true) return
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getDatabase(account).applicationDao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url,
                        type = "onError",
                        message = "${error.message}",
                        time = System.currentTimeMillis(),
                    ),
                )
            }
        }
        if (error.message?.contains("EACCES (Permission denied)") == true) {
            AmberListenerSingleton.latestErrorMessages.add(context.getString(R.string.network_permission_message))
        } else if (error.message?.contains("socket failed: EPERM (Operation not permitted)") == true) {
            AmberListenerSingleton.latestErrorMessages.add(context.getString(R.string.network_permission_message))
        } else if (Amber.instance.settings.useProxy && error.message?.contains("(port ${Amber.instance.settings.proxyPort})") == true) {
            AmberListenerSingleton.latestErrorMessages.add(context.getString(R.string.failed_to_connect_to_tor_orbot))
        } else {
            AmberListenerSingleton.latestErrorMessages.add(error.message ?: "Unknown error")
        }
    }

    override fun onEvent(event: Event, subscriptionId: String, relay: Relay, arrivalTime: Long, afterEOSE: Boolean) {
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getDatabase(account).applicationDao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url,
                        type = "onEvent",
                        message = "Received event ${event.id} from subscription $subscriptionId afterEOSE: $afterEOSE",
                        time = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    override fun onNotify(relay: Relay, description: String) {
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getDatabase(account).applicationDao().insertLog(
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
    }

    override fun onRelayStateChange(type: RelayState, relay: Relay) {
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getDatabase(account).applicationDao().insertLog(
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
}
