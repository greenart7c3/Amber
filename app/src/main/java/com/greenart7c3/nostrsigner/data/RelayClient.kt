package com.greenart7c3.nostrsigner.data

import com.greenart7c3.nostrsigner.okhttp.OkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient

/** The relay connections: the Quartz client and the operations that keep its subscriptions current. */
interface RelayClient {
    val client: NostrClient

    /** Builds the WebSocket connections of a [NostrClient]; ad-hoc clients (relay probes) use it too. */
    val factory: OkHttpWebSocket.Builder

    /** Resubscribes with the current relay lists, connecting (or reconnecting) as needed; disconnects under the kill switch. */
    suspend fun checkForNewRelaysAndUpdateAllFilters(shouldReconnect: Boolean = false)

    fun reconnect()

    /** When relays were last disconnected on purpose; disconnects right after it are not failures. */
    var intentionalDisconnectTime: Long

    /** Disconnects without counting it as relay failures. */
    fun disconnectIntentionally()

    /** Whether the SOCKS proxy (built-in Tor or Orbot) accepts connections. */
    fun isSocksProxyAlive(proxyHost: String, proxyPort: Int): Boolean

    /** Drops every socket and dials again; for a change of network. */
    suspend fun resetRelayConnections(reason: String)
}
