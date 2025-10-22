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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayState
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
object AmberListenerSingleton {
    var accountStateViewModel: AccountStateViewModel? = null
    val latestErrorMessages = mutableListOf<String>()

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

class NostrClientLoggerListener(
    val context: Context,
    val stats: AmberRelayStats,
    val scope: CoroutineScope,
) : IRelayClientListener {
    override fun onAuth(relay: IRelayClient, challenge: String) {
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getLogDatabase(account).logDao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url.url,
                        type = "onAuth",
                        message = "Authenticating",
                        time = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    override fun onBeforeSend(relay: IRelayClient, event: Event) {
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getLogDatabase(account).logDao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url.url,
                        type = "onBeforeSend",
                        message = "Sending event ${event.id}",
                        time = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    override fun onSend(relay: IRelayClient, msg: String, success: Boolean) {
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getLogDatabase(account).logDao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url.url,
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

    override fun onSendResponse(relay: IRelayClient, eventId: String, success: Boolean, message: String) {
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getLogDatabase(account).logDao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url.url,
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

        if (success) {
            stats.addSent(relay.url)
        } else {
            stats.addFailed(relay.url)
        }
    }

    override fun onError(relay: IRelayClient, subId: String, error: Error) {
        if (error.message?.trim()?.equals("Relay sent notice:") == true) return
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getLogDatabase(account).logDao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url.url,
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

    override fun onEvent(relay: IRelayClient, subId: String, event: Event, arrivalTime: Long, afterEOSE: Boolean) {
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getLogDatabase(account).logDao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url.url,
                        type = "onEvent",
                        message = "Received event ${event.id} from subscription $subId afterEOSE: $afterEOSE",
                        time = System.currentTimeMillis(),
                    ),
                )
            }

            Amber.instance.stats.addReceived(relay.url)
        }
    }

    override fun onNotify(relay: IRelayClient, description: String) {
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getLogDatabase(account).logDao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url.url,
                        type = "onNotify",
                        message = description,
                        time = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    override fun onRelayStateChange(relay: IRelayClient, type: RelayState) {
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getLogDatabase(account).logDao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url.url,
                        type = "onRelayStateChange",
                        message = type.name,
                        time = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }
}
