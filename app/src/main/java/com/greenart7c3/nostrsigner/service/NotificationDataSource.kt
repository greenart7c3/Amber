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
import com.greenart7c3.nostrsigner.database.LogEntity
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.NostrDataSource
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object NotificationDataSource : NostrDataSource(NostrSigner.getInstance().client, "AccountData") {
    private val eventNotificationConsumer = EventNotificationConsumer(NostrSigner.getInstance())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val clientListener =
        object : NostrClient.Listener {
            override fun onEvent(
                event: Event,
                subscriptionId: String,
                relay: Relay,
                afterEOSE: Boolean,
            ) {
                scope.launch {
                    LocalPreferences.currentAccount(NostrSigner.getInstance())?.let { account ->
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
            }

            override fun onRelayStateChange(
                type: Relay.StateType,
                relay: Relay,
                subscriptionId: String?,
            ) {
                scope.launch {
                    LocalPreferences.currentAccount(NostrSigner.getInstance())?.let { account ->
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

            override fun onAuth(
                relay: Relay,
                challenge: String,
            ) {
                scope.launch {
                    LocalPreferences.currentAccount(NostrSigner.getInstance())?.let { account ->
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
                auth(relay, challenge)
            }

            override fun onNotify(
                relay: Relay,
                description: String,
            ) {
                scope.launch {
                    LocalPreferences.currentAccount(NostrSigner.getInstance())?.let { account ->
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
                notify(relay, description)
            }
        }

    init {
        scope.launch {
            LocalPreferences.loadSettingsFromEncryptedStorage()
            client.subscribe(clientListener)
        }
    }

    override fun start() {
        if (!client.isSubscribed(clientListener)) {
            client.subscribe(clientListener)
        }
        super.start()
    }

    override fun stop() {
        super.stop()
        client.unsubscribe(clientListener)
    }

    private fun createNotificationsFilter(): TypedFilter {
        var since = TimeUtils.now()
        val accounts = LocalPreferences.allSavedAccounts(NostrSigner.getInstance())
        accounts.forEach {
            val latest = NostrSigner.getInstance().getDatabase(it.npub).applicationDao().getLatestNotification()
            if (latest != null) {
                since = latest
            }
        }

        val pubKeys =
            accounts.mapNotNull {
                LocalPreferences.loadFromEncryptedStorage(NostrSigner.getInstance(), it.npub)?.signer?.keyPair?.pubKey?.toHexKey()
            }

        val eoses =
            NostrSigner.getInstance().client.getAll().associate {
                Pair(it.url, EOSETime(since))
            }

        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = SincePerRelayFilter(
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
        NotificationUtils.getOrCreateDMChannel(NostrSigner.getInstance().applicationContext)
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

        LocalPreferences.allSavedAccounts(NostrSigner.getInstance()).forEach {
            val account = LocalPreferences.loadFromEncryptedStorage(NostrSigner.getInstance(), it.npub) ?: return@forEach
            account.createAuthEvent(relay.url, challenge) { authEvent ->
                NostrSigner.getInstance().client.sendIfExists(authEvent, relay)
            }
        }
    }
}
