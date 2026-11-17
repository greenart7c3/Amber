package com.greenart7c3.nostrsigner.data

import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient

/** The relay connections: the Quartz client and the operations that keep its subscriptions current. */
interface RelayClient {
    val client: NostrClient

    /** Resubscribes with the current relay lists, connecting (or reconnecting) as needed; disconnects under the kill switch. */
    suspend fun checkForNewRelaysAndUpdateAllFilters(shouldReconnect: Boolean = false)

    fun reconnect()

    /** Disconnects without counting it as relay failures. */
    fun disconnectIntentionally()

    /** Drops every socket and dials again; for a change of network. */
    suspend fun resetRelayConnections(reason: String)
}
