package com.greenart7c3.nostrsigner.service

import android.util.Log
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.Amber.Companion.TAG
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.checkNotInMainThread
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.relays.AmberRelayStats
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.datasources.NostrDataSource
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.RelayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ProfileDataSource(
    client: NostrClient,
    val account: Account,
    val onReceiveEvent: (Event) -> Unit,
) : NostrDataSource(client) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val clientListener =
        object : NostrClient.Listener {
            override fun onEvent(event: Event, subscriptionId: String, relay: Relay, arrivalTime: Long, afterEOSE: Boolean) {
                scope.launch {
                    LocalPreferences.currentAccount(Amber.instance)?.let { account ->
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

            override fun onRelayStateChange(type: RelayState, relay: Relay) {
                Log.d(Amber.TAG, "onRelayStateChange: $type")
                AmberRelayStats.updateNotification()
                scope.launch {
                    LocalPreferences.currentAccount(Amber.instance)?.let { account ->
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

            override fun onError(error: Error, subscriptionId: String, relay: Relay) {
                scope.launch {
                    LocalPreferences.currentAccount(Amber.instance)?.let { account ->
                        Amber.instance.getDatabase(account).applicationDao().insertLog(
                            LogEntity(
                                id = 0,
                                url = relay.url,
                                type = "onError",
                                message = error.message ?: "Unknown error",
                                time = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
                super.onError(error, subscriptionId, relay)
            }

            override fun onAuth(
                relay: Relay,
                challenge: String,
            ) {
                account.createAuthEvent(
                    relayUrl = relay.url,
                    challenge = challenge,
                    onReady = {
                        client.sendIfExists(it, relay)
                    },
                )
            }

            override fun onNotify(
                relay: Relay,
                description: String,
            ) {
                scope.launch {
                    LocalPreferences.currentAccount(Amber.instance)?.let { account ->
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
                notify(relay, description)
            }

            override fun onSendResponse(eventId: String, success: Boolean, message: String, relay: Relay) {
                if (success) {
                    AmberRelayStats.addSent(relay.url)
                } else {
                    AmberRelayStats.addFailed(relay.url)
                }
                super.onSendResponse(eventId, success, message, relay)
            }
        }

    init {
        scope.launch {
            client.subscribe(clientListener)
        }
    }

    override fun start() {
        if (!client.isSubscribed(clientListener)) {
            client.subscribe(clientListener)
        }
        Amber.instance.applicationIOScope.launch {
            delay(30000)
            stop()
            client.getAll().forEach {
                Log.d(TAG, "disconnecting profile relay ${it.url}")
                it.disconnect()
            }
        }
        super.start()
    }

    override fun stop() {
        super.stop()
        Log.d(Amber.TAG, "stopping profile datasource")
        client.unsubscribe(clientListener)
    }

    private fun createNotificationsFilter(): TypedFilter {
        return TypedFilter(
            types = COMMON_FEED_TYPES,
            filter = SincePerRelayFilter(
                kinds = listOf(MetadataEvent.KIND),
                authors = listOf(account.hexKey),
                limit = 1,
            ),
        )
    }

    override fun consume(
        event: Event,
        relay: Relay,
    ) {
        checkNotInMainThread()
        onReceiveEvent(event)
    }

    private val accountChannel =
        requestNewSubscription { _, _ ->
            invalidateFilters()
        }

    override fun auth(
        relay: Relay,
        challenge: String,
    ) {
        super.auth(relay, challenge)

        account.createAuthEvent(
            relayUrl = relay.url,
            challenge = challenge,
            onReady = {
                client.sendIfExists(it, relay)
            },
        )
    }

    override fun updateSubscriptions() {
        accountChannel.typedFilters = listOf(createNotificationsFilter())
    }
}
