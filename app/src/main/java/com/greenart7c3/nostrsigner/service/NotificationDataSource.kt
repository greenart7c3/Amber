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

import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.checkNotInMainThread
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.relays.Client
import com.greenart7c3.nostrsigner.relays.EOSETime
import com.greenart7c3.nostrsigner.relays.FeedType
import com.greenart7c3.nostrsigner.relays.JsonFilter
import com.greenart7c3.nostrsigner.relays.Relay
import com.greenart7c3.nostrsigner.relays.RelayPool
import com.greenart7c3.nostrsigner.relays.TypedFilter
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.Event

object NotificationDataSource : NostrDataSource("AccountData") {
    private val eventNotificationConsumer = EventNotificationConsumer(NostrSigner.instance)

    private fun createNotificationsFilter(): TypedFilter {
        var since = TimeUtils.now()
        val accounts = LocalPreferences.allSavedAccounts()
        accounts.forEach {
            val latest = NostrSigner.instance.getDatabase(it.npub).applicationDao().getLatestNotification()
            if (latest != null) {
                since = latest
            }
        }

        val pubKeys =
            accounts.mapNotNull {
                LocalPreferences.loadFromEncryptedStorage(it.npub)?.keyPair?.pubKey?.toHexKey()
            }

        val eoses =
            RelayPool.getAll().associate {
                Pair(it.url, EOSETime(since))
            }

        return TypedFilter(
            types = setOf(FeedType.FOLLOWS),
            filter =
                JsonFilter(
                    kinds = listOf(24133),
                    tags = mapOf("p" to pubKeys),
                    limit = 1,
                    since = eoses,
                ),
        )
    }

    override fun consume(
        event: Event,
        relay: Relay,
    ) {
        checkNotInMainThread()
        NotificationUtils.getOrCreateDMChannel(NostrSigner.instance.applicationContext)
        eventNotificationConsumer.consume(event)
    }

    private val accountChannel =
        requestNewChannel { _, _ ->
            invalidateFilters()
        }

    override fun updateChannelFilters() {
        accountChannel.typedFilters = listOf(createNotificationsFilter())
    }

    override fun auth(
        relay: Relay,
        challenge: String,
    ) {
        super.auth(relay, challenge)

        LocalPreferences.allSavedAccounts().forEach {
            val account = LocalPreferences.loadFromEncryptedStorage(it.npub) ?: return@forEach
            account.createAuthEvent(relay.url, challenge) { authEvent ->
                Client.send(
                    authEvent,
                    { },
                    relay.url,
                )
            }
        }
    }
}
