package com.greenart7c3.nostrsigner.relays

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.service.NotificationUtils.sendErrorNotification
import com.greenart7c3.nostrsigner.ui.ToastManager
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AmberListenerSingleton {
    val latestErrorMessages = mutableListOf<String>()

    fun showErrorMessage() {
        if (latestErrorMessages.isEmpty()) return
        if (latestErrorMessages.last().isBlank()) return
        if (Amber.isAppInForeground) {
            ToastManager.toast("Error", latestErrorMessages.last())
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
) : RelayConnectionListener {
    private var reconnectJob: Job? = null
    private var reconnectDelay = 5_000L
    private var lastDisconnectTime = 0L

    // Per-relay consecutive connection-failure counts. A relay that fails to
    // connect MAX_RECONNECT_ATTEMPTS times in a row is treated as permanently
    // dead and stops scheduling reconnects, so an unreachable relay can't keep
    // waking the radio every minute forever and draining the battery. The count
    // is reset whenever the relay connects successfully (onConnected), and a dead
    // relay still gets a fresh chance on the next OS network change, because the
    // ConnectivityService network callback calls client.connect(), which retries
    // every relay regardless of this backoff state.
    private val failureCounts = ConcurrentHashMap<String, Int>()

    private fun scheduleReconnect(relayUrl: String) {
        val failures = (failureCounts[relayUrl] ?: 0) + 1
        failureCounts[relayUrl] = failures
        if (failures > MAX_RECONNECT_ATTEMPTS) {
            if (BuildConfig.DEBUG) {
                Log.d(Amber.TAG, "Relay $relayUrl marked dead after $failures failed attempts; skipping reconnect")
            }
            return
        }
        reconnectWithBackoff()
    }

    private fun reconnectWithBackoff() {
        val now = System.currentTimeMillis()
        if (now - lastDisconnectTime > 60_000) {
            reconnectDelay = 5_000L
        }
        lastDisconnectTime = now

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            if (BuildConfig.DEBUG) Log.d(Amber.TAG, "Reconnecting in ${reconnectDelay / 1000}s...")
            delay(reconnectDelay)
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(60_000L)
            if (!BuildFlavorChecker.isOfflineFlavor() && !Amber.instance.settings.killSwitch.value) {
                Amber.instance.reconnect()
            }
        }
    }

    private fun saveLog(
        url: String,
        type: String,
        message: String,
    ) {
        scope.launch {
            val account = LocalPreferences.currentAccount(context)
            if (account != null) {
                Amber.instance.getLogDatabase(account).dao().insertLog(
                    LogEntity(
                        id = 0,
                        url = url,
                        type = type,
                        message = message,
                        time = System.currentTimeMillis(),
                    ),
                )
            } else {
                LocalPreferences.allSavedAccounts(context).forEach {
                    Amber.instance.getLogDatabase(it.npub).dao().insertLog(
                        LogEntity(
                            id = 0,
                            url = url,
                            type = type,
                            message = message,
                            time = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }
    }

    override fun onCannotConnect(relay: IRelayClient, errorMessage: String) {
        if (BuildConfig.DEBUG) Log.d(Amber.TAG, "onCannotConnect: ${relay.url.url} error: $errorMessage")
        saveLog(relay.url.url, "onCannotConnect", errorMessage)

        if (System.currentTimeMillis() - Amber.instance.intentionalDisconnectTime < 2_000) {
            super.onCannotConnect(relay, errorMessage)
            return
        }

        scheduleReconnect(relay.url.url)
        super.onCannotConnect(relay, errorMessage)
    }

    override fun onSent(relay: IRelayClient, cmdStr: String, cmd: Command, success: Boolean) {
        if (BuildConfig.DEBUG) Log.d(Amber.TAG, "onSent: ${relay.url.url} success: $success cmd: $cmdStr")

        if (cmd is ReqCmd) {
            saveLog(relay.url.url, "onSent", "Subscribed to relay: $success")
        }
        if (cmd is EventCmd) {
            saveLog(relay.url.url, "onSend", "Sent event to relay: $success")
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
        if (BuildConfig.DEBUG) Log.d(Amber.TAG, "onIncomingMessage: ${relay.url.url} msg: $msgStr")

        if (msg is OkMessage) {
            saveLog(relay.url.url, "onIncomingMessage", "Relay accepted message: ${msg.success}")
        }

        if (msg is OkMessage && !msg.success) {
            AmberListenerSingleton.latestErrorMessages.add(msg.message)
        }

        super.onIncomingMessage(relay, msgStr, msg)
    }

    override fun onDisconnected(relay: IRelayClient) {
        if (BuildConfig.DEBUG) Log.d(Amber.TAG, "onDisconnected: ${relay.url.url}")
        saveLog(relay.url.url, "onDisconnected", "Disconnected")

        if (System.currentTimeMillis() - Amber.instance.intentionalDisconnectTime < 2_000) {
            if (BuildConfig.DEBUG) Log.d(Amber.TAG, "onDisconnected: ${relay.url.url} intentional, skipping reconnect")
            super.onDisconnected(relay)
            return
        }

        scheduleReconnect(relay.url.url)
        super.onDisconnected(relay)
    }

    override fun onConnecting(relay: IRelayClient) {
        if (BuildConfig.DEBUG) Log.d(Amber.TAG, "onConnecting: ${relay.url.url}")
        saveLog(relay.url.url, "onConnecting", "Connecting")
        super.onConnecting(relay)
    }

    override fun onConnected(relay: IRelayClient, pingMillis: Int, compressed: Boolean) {
        if (BuildConfig.DEBUG) Log.d(Amber.TAG, "onConnected: ${relay.url.url} ping: ${pingMillis}ms compressed: $compressed")
        saveLog(relay.url.url, "onConnected", "Connected")
        // Relay recovered: clear its failure streak so it is eligible for the
        // normal reconnect-with-backoff path again.
        failureCounts.remove(relay.url.url)
        super.onConnected(relay, pingMillis, compressed)
    }

    companion object {
        // After this many consecutive failures a relay is considered permanently
        // dead and is no longer scheduled for reconnection until it recovers on a
        // network change. With the 60s backoff cap this is roughly 10 minutes of
        // retrying before giving up.
        private const val MAX_RECONNECT_ATTEMPTS = 10
    }
}
