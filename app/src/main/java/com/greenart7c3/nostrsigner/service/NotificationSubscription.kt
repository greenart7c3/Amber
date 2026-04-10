/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.util.Log
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID

class NotificationSubscription(
    val client: NostrClient,
    val appContext: Context,
) : RelayConnectionListener {
    private val eventNotificationConsumer = EventNotificationConsumer(appContext)
    private val subIds = mutableMapOf<String, String>()

    init {
        // listens until the app crashes.
        client.addConnectionListener(this)
    }

    override fun onIncomingMessage(relay: IRelayClient, msgStr: String, msg: Message) {
        if (msg is EventMessage) {
            if (subIds.containsValue(msg.subId)) {
                eventNotificationConsumer.consume(msg.event, relay.url)
            }
        }
        super.onIncomingMessage(relay, msgStr, msg)
    }

    override fun onSent(relay: IRelayClient, cmdStr: String, cmd: Command, success: Boolean) {
        Log.d("NotificationSubscription", "onSend: ${relay.url}, $cmdStr, $success")
        super.onSent(relay, cmdStr, cmd, success)
    }

    /**
     * Call this method every time the relay list or the user list changes.
     *
     * Each connection with its own localKey gets a dedicated subscription on that connection's
     * relays, listening for events tagged with the connection's pubkey.
     * The main account subscription (real pubkey) is only opened when there are legacy
     * connections that don't yet have a localKey.
     */
    suspend fun updateFilter() {
        if (BuildFlavorChecker.isOfflineFlavor()) return
        val activeSubKeys = mutableSetOf<String>()

        LocalPreferences.allAccounts(appContext).forEach { account ->
            val since = computeSince()

            val allConnections = Amber.instance.getDatabase(account.npub).dao().getAll(account.hexKey)
            val connectionsWithLocalKey = allConnections.filter { it.localKey.isNotEmpty() }
            val hasLegacyConnections = allConnections.any { it.localKey.isEmpty() && it.relays.isNotEmpty() }

            // Per-connection subscription on each connection's own relays
            for (conn in connectionsWithLocalKey) {
                val connPubKey = conn.localPubKey
                val subKey = "${account.hexKey}_$connPubKey"
                activeSubKeys.add(subKey)
                if (!subIds.containsKey(subKey)) {
                    subIds[subKey] = UUID.randomUUID().toString()
                }
                val connRelays = conn.relays.ifEmpty { Amber.instance.getSavedRelays(account) }
                client.subscribe(
                    subIds[subKey]!!,
                    connRelays.associateWith {
                        listOf(
                            Filter(
                                kinds = listOf(NostrConnectEvent.KIND),
                                tags = mapOf("p" to listOf(connPubKey)),
                                limit = 1,
                                since = since,
                            ),
                        )
                    },
                )
            }

            // Main account subscription only for legacy connections (no localKey)
            if (hasLegacyConnections) {
                activeSubKeys.add(account.hexKey)
                if (!subIds.containsKey(account.hexKey)) {
                    subIds[account.hexKey] = UUID.randomUUID().toString()
                }
                val relays = Amber.instance.getSavedRelays(account)
                client.subscribe(
                    subIds[account.hexKey]!!,
                    relays.associateWith {
                        listOf(
                            Filter(
                                kinds = listOf(NostrConnectEvent.KIND),
                                tags = mapOf("p" to listOf(account.hexKey)),
                                limit = 1,
                                since = since,
                            ),
                        )
                    },
                )
            }
        }

        // Unsubscribe from any subscriptions belonging to deleted applications
        val staleSubKeys = subIds.keys.filter { it !in activeSubKeys }
        for (subKey in staleSubKeys) {
            subIds.remove(subKey)?.let { subId ->
                client.unsubscribe(subId)
            }
        }
    }

    private fun computeSince(): Long {
        var since = TimeUtils.now()
        val latest = if (Amber.instance.notificationCache.size() > 0) Amber.instance.notificationCache.snapshot().maxOf { it.value } else 0L
        if (latest > 0) since = latest
        return since
    }
}
