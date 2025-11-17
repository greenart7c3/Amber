package com.greenart7c3.nostrsigner.relays

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.service.NotificationUtils.sendErrorNotification
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
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
    override fun onCannotConnect(relay: IRelayClient, errorMessage: String) {
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getLogDatabase(account).dao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url.url,
                        type = "onCannotConnect",
                        message = errorMessage,
                        time = System.currentTimeMillis(),
                    ),
                )
            }
        }
        super.onCannotConnect(relay, errorMessage)
    }

    override fun onSent(relay: IRelayClient, cmdStr: String, cmd: Command, success: Boolean) {
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getLogDatabase(account).dao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url.url,
                        type = "onSend",
                        message = "message: $cmdStr success: $success",
                        time = System.currentTimeMillis(),
                    ),
                )
            }
        }
        if (!success) {
            AmberListenerSingleton.latestErrorMessages.add("Failed to send event. Try again.")
        }

        if (success) {
            stats.addSent(relay.url)
        } else {
            stats.addFailed(relay.url)
        }

        super.onSent(relay, cmdStr, cmd, success)
    }

    override fun onIncomingMessage(relay: IRelayClient, msgStr: String, msg: Message) {
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getLogDatabase(account).dao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url.url,
                        type = "onIncomingMessage",
                        message = "message: $msgStr",
                        time = System.currentTimeMillis(),
                    ),
                )
            }
        }

        if (msg is OkMessage && !msg.success) {
            AmberListenerSingleton.latestErrorMessages.add(msg.message)
        }

        super.onIncomingMessage(relay, msgStr, msg)
    }

    override fun onDisconnected(relay: IRelayClient) {
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getLogDatabase(account).dao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url.url,
                        type = "onDisconnected",
                        message = "Disconnected from relay ${relay.url.url}",
                        time = System.currentTimeMillis(),
                    ),
                )
            }
        }
        super.onDisconnected(relay)
    }

    override fun onConnecting(relay: IRelayClient) {
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getLogDatabase(account).dao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url.url,
                        type = "onConnecting",
                        message = "Connecting to relay ${relay.url.url}",
                        time = System.currentTimeMillis(),
                    ),
                )
            }
        }
        super.onConnecting(relay)
    }

    override fun onConnected(relay: IRelayClient, pingMillis: Int, compressed: Boolean) {
        scope.launch {
            LocalPreferences.currentAccount(context)?.let { account ->
                Amber.instance.getLogDatabase(account).dao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url.url,
                        type = "onConnected",
                        message = "Connected to relay ${relay.url.url}",
                        time = System.currentTimeMillis(),
                    ),
                )
            }
        }
        super.onConnected(relay, pingMillis, compressed)
    }
}
