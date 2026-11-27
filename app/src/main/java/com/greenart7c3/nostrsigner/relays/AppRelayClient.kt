package com.greenart7c3.nostrsigner.relays

import android.content.Context
import android.net.TrafficStats
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.common.relays.RelayHealthTracker
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.network_permission_message
import com.greenart7c3.nostrsigner.commonsui.warning
import com.greenart7c3.nostrsigner.data.RelayClient
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.models.TorMode
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.greenart7c3.nostrsigner.okhttp.OkHttpWebSocket
import com.greenart7c3.nostrsigner.service.PrivateAddress
import com.greenart7c3.nostrsigner.service.TorManager
import com.greenart7c3.nostrsigner.ui.ToastManager
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The app's relay connections: the Quartz client, how it dials (Tor or not), its status, and
 * the connect / reconnect / reset logic. The subscriptions on top of it are passed in as
 * functions, since they take this client themselves.
 *
 * @param appContext the Application; [AmberRelayStats] builds the relay-status notification with it.
 * @param isSettingsLoaded false until the stored settings are loaded; until then every dial goes through the proxy.
 * @param updateSubscriptions sends the current relay lists to the subscriptions (the profile one only while the UI is visible).
 */
class AppRelayClient(
    appContext: Context,
    private val settingsStore: SettingsStore,
    private val isSettingsLoaded: () -> Boolean,
    private val scope: CoroutineScope,
    private val updateSubscriptions: suspend () -> Unit,
    private val closeSubscriptions: () -> Unit,
) : RelayClient {
    @Suppress("NoComments")
    override val factory = OkHttpWebSocket.Builder(
        httpClient = { url ->
            if (PrivateAddress.isPrivate(url.url)) return@Builder HttpClientManager.getLocalHttpClient()
            val useProxy = when {
                // Settings load asynchronously; until they land, torMode's default (DISABLED)
                // is a guess, not a decision. Route through the proxy client — preset from
                // plain prefs in onCreate — so a BUILTIN/ORBOT user's pre-Tor dials (network
                // callbacks, profile fetches) hit the fail-closed placeholder instead of the
                // clearnet.
                !isSettingsLoaded() -> true
                else -> settingsStore.settings.torMode != TorMode.DISABLED
            }
            HttpClientManager.getHttpClient(useProxy)
        },
        canConnectRelay = ::canDialRelay,
    )

    override val client: NostrClient = NostrClient(factory, scope)

    override val relayStats = RelayStats(client)

    override val stats = AmberRelayStats(client, appContext, settingsStore = settingsStore, relayStats = relayStats, scope = scope)

    @Volatile override var intentionalDisconnectTime = 0L

    init {
        disconnectWhenIdleOnEmptyRelays()
    }

    private fun canDialRelay(url: NormalizedRelayUrl): Boolean = when {
        PrivateAddress.isPrivate(url.url) -> true
        !isSettingsLoaded() -> false
        settingsStore.settings.torMode == TorMode.BUILTIN -> TorManager.isRunning.value && TorManager.socksPort.value > 0
        else -> true
    }

    private fun hasActiveSubscriptions(): Boolean = client.registrySizes().let { it.liveRequests > 0 || it.liveCounts > 0 }

    private fun disconnectWhenIdleOnEmptyRelays() {
        scope.launch {
            var hadRelays = false
            client.availableRelaysFlow().collect { relays ->
                if (relays.isNotEmpty()) {
                    hadRelays = true
                } else if (hadRelays) {
                    hadRelays = false
                    if (client.isActive() && !hasActiveSubscriptions()) {
                        disconnectIntentionally()
                    }
                }
            }
        }
    }

    override fun ensureConnected() {
        if (BuildFlavorChecker.isOfflineFlavor() || settingsStore.settings.killSwitch.value) return
        if (!client.isActive()) client.connect()
    }

    override fun isSocksProxyAlive(proxyHost: String, proxyPort: Int): Boolean {
        if (settingsStore.settings.torMode == TorMode.BUILTIN) {
            val port = TorManager.socksPort.value
            return port != 0 &&
                try {
                    if (TrafficStats.getThreadStatsTag() == -1) {
                        TrafficStats.setThreadStatsTag(0x0001)
                    }
                    val socket = Socket()
                    socket.connect(InetSocketAddress(proxyHost, port), PROXY_PROBE_TIMEOUT_MILLIS)
                    TrafficStats.tagSocket(socket)
                    socket.close()
                    true
                } catch (_: Exception) {
                    false
                }
        }
        try {
            if (TrafficStats.getThreadStatsTag() == -1) {
                TrafficStats.setThreadStatsTag(0x0001)
            }
            val socket = Socket()
            socket.connect(InetSocketAddress(proxyHost, proxyPort), PROXY_PROBE_TIMEOUT_MILLIS)
            TrafficStats.tagSocket(socket)
            socket.close()
            return true
        } catch (e: Exception) {
            if (e.message?.contains("EACCES (Permission denied)") == true) {
                ToastManager.toast(getStringBlocking(Res.string.warning), getStringBlocking(Res.string.network_permission_message))
            } else if (e.message?.contains("socket failed: EPERM (Operation not permitted)") == true) {
                ToastManager.toast(getStringBlocking(Res.string.warning), getStringBlocking(Res.string.network_permission_message))
            }
            AmberLog.e(Amber.TAG, "Failed to connect to proxy", e)
            return false
        }
    }

    override suspend fun resubscribeAll() {
        closeSubscriptions()
        disconnectIntentionally()
        checkForNewRelaysAndUpdateAllFilters(shouldReconnect = true)
    }

    override fun disconnectIntentionally() {
        intentionalDisconnectTime = System.currentTimeMillis()
        client.disconnect()
    }

    @Suppress("NoComments")
    override fun reconnect() {
        if (settingsStore.settings.killSwitch.value) {
            return
        }
        AmberLog.d(Amber.TAG, "reconnecting relays")
        RelayHealthTracker.reset()
        client.resetBackoff()
        val wasActive = client.isActive()
        // Always call connect() so that failed relays (socket == null) are retried
        // directly, bypassing BasicRelayClient's internal backoff delay. For relays
        // that are already connected (socket != null), connect() is a safe no-op.
        client.connect()
        client.reconnect(wasActive)
        if (!BuildFlavorChecker.isOfflineFlavor()) {
            stats.updateNotification()
        }
    }

    /**
     * Drops every relay socket and dials again from scratch. Called when the device's
     * network identity changes (Wi-Fi <-> mobile, a VPN coming up or going down, the
     * network regaining internet access).
     *
     * Sockets opened on the previous network are bound to routes that may no longer
     * exist: they keep reporting "connected" and only fail once OkHttp's ping times out,
     * minutes later. Relays that failed while the device was offline also carry state
     * that would keep them down on the new network: Quartz's per-relay backoff (up to
     * five minutes, set at once on an unresolved host) and RelayHealthTracker's dead
     * list, which removes them from the subscriptions entirely. All of it is about the
     * old network, so it is cleared here.
     */
    @Suppress("NoComments")
    override suspend fun resetRelayConnections(reason: String) {
        if (BuildFlavorChecker.isOfflineFlavor()) return
        if (settingsStore.settings.killSwitch.value) return

        AmberLog.d(Amber.TAG, "Resetting relay connections: $reason")
        RelayHealthTracker.reset()

        disconnectIntentionally()
        // disconnect() also clears each relay's backoff; this covers relays without a socket.
        client.resetBackoff()
        client.connect()

        checkForNewRelaysAndUpdateAllFilters()
        stats.updateNotification()
    }

    override suspend fun checkForNewRelaysAndUpdateAllFilters(shouldReconnect: Boolean) {
        if (settingsStore.settings.killSwitch.value) {
            disconnectIntentionally()
            return
        }

        val wasActive = client.isActive()
        if (!BuildFlavorChecker.isOfflineFlavor()) {
            updateSubscriptions()
        }
        if (!hasActiveSubscriptions()) {
            if (wasActive) {
                disconnectIntentionally()
            }
            return
        }
        AmberLog.d(Amber.TAG, "checkForNewRelaysAndUpdateAllFilters wasActive: $wasActive")
        if (!wasActive) {
            delay(3.seconds)
            client.connect()
        }

        if (shouldReconnect) {
            client.reconnect(wasActive)
        }
    }
}

private const val PROXY_PROBE_TIMEOUT_MILLIS = 5000
