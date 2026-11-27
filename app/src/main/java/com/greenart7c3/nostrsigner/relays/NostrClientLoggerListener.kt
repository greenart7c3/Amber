package com.greenart7c3.nostrsigner.relays

import android.app.NotificationManager
import android.content.Context
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.common.relays.RelayHealthTracker
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.RelayClient
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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object AmberListenerSingleton {
    val latestErrorMessages = mutableListOf<String>()

    fun showErrorMessage(context: Context) {
        if (latestErrorMessages.isEmpty()) return
        if (latestErrorMessages.last().isBlank()) return
        if (Amber.isAppInForeground) {
            ToastManager.toast("Error", latestErrorMessages.last())
        } else {
            val notificationManager: NotificationManager =
                context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.sendErrorNotification(
                id = latestErrorMessages.last().hashCode().toString(),
                channelId = "ErrorID",
                messageTitle = "Error sending bunker response",
                messageBody = latestErrorMessages.last(),
                picture = null,
                applicationContext = context.applicationContext,
            )
        }
        latestErrorMessages.clear()
    }
}

class NostrClientLoggerListener(
    val context: Context,
    val stats: AmberRelayStats,
    val scope: CoroutineScope,
    private val relayClient: RelayClient,
    private val accountRepository: AccountRepository,
    private val onRelayMarkedDead: suspend () -> Unit = {},
) : RelayConnectionListener {
    private fun recordFailureAndDropDeadRelay(relay: NormalizedRelayUrl) {
        if (!RelayHealthTracker.recordFailure(relay)) {
            AmberLog.d(Amber.TAG, "Relay ${relay.url} marked dead; dropping from subscriptions")
            scope.launch {
                onRelayMarkedDead()
            }
        }
    }

    private fun saveLog(
        url: String,
        type: String,
        message: String,
    ) {
        scope.launch {
            val account = SavedAccounts.current(context)
            if (account != null) {
                accountRepository.getLogDatabase(account).dao().insertLogIfEnabled(
                    LogEntity(
                        id = 0,
                        url = url,
                        type = type,
                        message = message,
                        time = System.currentTimeMillis(),
                    ),
                )
            } else {
                SavedAccounts.all(context).forEach {
                    accountRepository.getLogDatabase(it.npub).dao().insertLogIfEnabled(
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
        AmberLog.d(Amber.TAG, "onCannotConnect: ${relay.url.url} error: $errorMessage")
        saveLog(relay.url.url, "onCannotConnect", errorMessage)

        if (System.currentTimeMillis() - relayClient.intentionalDisconnectTime < 2_000) {
            super.onCannotConnect(relay, errorMessage)
            return
        }

        recordFailureAndDropDeadRelay(relay.url)
        super.onCannotConnect(relay, errorMessage)
    }

    @Suppress("NoComments")
    override fun onSent(relay: IRelayClient, cmdStr: String, cmd: Command, success: Boolean) {
        // Defense-in-depth (GHSA-8844-q5vh-9j8f, L2): never log raw relay
        // frames verbatim — they can carry NIP-46 envelopes, DM/gift-wrap
        // ciphertexts and event content. Log only the command type and the
        // wire size, which is enough for debugging without leaking payloads.
        AmberLog.d(Amber.TAG, "onSent: ${relay.url.url} success: $success cmd: ${cmd.javaClass.simpleName} bytes: ${cmdStr.toByteArray().size}")

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

    @Suppress("NoComments")
    override suspend fun onIncomingMessage(relay: IRelayClient, msgStr: String, msg: Message) {
        // Defense-in-depth (GHSA-8844-q5vh-9j8f, L2): log only the message type and wire size, as in onSent.
        AmberLog.d(Amber.TAG, "onIncomingMessage: ${relay.url.url} msg: ${msg.javaClass.simpleName} bytes: ${msgStr.toByteArray().size}")

        if (msg is OkMessage) {
            saveLog(relay.url.url, "onIncomingMessage", "Relay accepted message: ${msg.success}")
        }

        if (msg is OkMessage && !msg.success) {
            AmberListenerSingleton.latestErrorMessages.add(msg.message)
        }

        super.onIncomingMessage(relay, msgStr, msg)
    }

    override fun onDisconnected(relay: IRelayClient) {
        AmberLog.d(Amber.TAG, "onDisconnected: ${relay.url.url}")
        saveLog(relay.url.url, "onDisconnected", "Disconnected")
        super.onDisconnected(relay)
    }

    override fun onConnecting(relay: IRelayClient) {
        AmberLog.d(Amber.TAG, "onConnecting: ${relay.url.url}")
        saveLog(relay.url.url, "onConnecting", "Connecting")
        super.onConnecting(relay)
    }

    override fun onConnected(relay: IRelayClient, pingMillis: Int, compressed: Boolean) {
        AmberLog.d(Amber.TAG, "onConnected: ${relay.url.url} ping: ${pingMillis}ms compressed: $compressed")
        saveLog(relay.url.url, "onConnected", "Connected")
        RelayHealthTracker.recordSuccess(relay.url)
        super.onConnected(relay, pingMillis, compressed)
    }
}
