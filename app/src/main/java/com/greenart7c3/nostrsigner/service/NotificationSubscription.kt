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
import com.greenart7c3.nostrsigner.LocalPreferences
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID

class NotificationSubscription(
    val client: NostrClient,
    val appContext: Context,
) : IRelayClientListener {
    private val eventNotificationConsumer = EventNotificationConsumer(appContext)
    private val subId = UUID.randomUUID().toString()

    init {
        // listens until the app crashes.
        client.subscribe(this)
    }

    override fun onEvent(relay: IRelayClient, subId: String, event: Event, arrivalTime: Long, afterEOSE: Boolean) {
        if (this.subId == subId) {
            eventNotificationConsumer.consume(event, relay.url)
        }
    }

    override fun onSend(relay: IRelayClient, msg: String, success: Boolean) {
        Log.d("NotificationSubscription", "onSend: ${relay.url}, $msg, $success")
        super.onSend(relay, msg, success)
    }

    /**
     * Call this method every time the relay list or the user list changes
     */
    fun updateFilter() {
        client.openReqSubscription(subId, createNotificationsFilter())
    }

    private fun createNotificationsFilter(): Map<NormalizedRelayUrl, List<Filter>> {
        // TODO: If you break relays per account, you can change this to only send the requests to the right relays for each account.
        val relays = Amber.instance.getSavedRelays()

        var since = TimeUtils.now()
        val accounts = LocalPreferences.allSavedAccounts(appContext)
        val latest = if (Amber.instance.notificationCache.size() > 0) Amber.instance.notificationCache.snapshot().maxOf { it.value } else 0L
        if (latest > 0) {
            since = latest
        }

        val pubKeys = accounts.map { it.npub.bechToBytes().toHexKey() }

        return relays.associateWith {
            listOf(
                Filter(
                    kinds = listOf(NostrConnectEvent.KIND),
                    tags = mapOf("p" to pubKeys),
                    limit = 1,
                    since = since,
                ),
            )
        }
    }
}
