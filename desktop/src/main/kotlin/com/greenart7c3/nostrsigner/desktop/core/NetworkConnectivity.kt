package com.greenart7c3.nostrsigner.desktop.core

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Desktop counterpart of Android's `ConnectivityService` network callback.
 *
 * There is no OS connectivity callback on the JVM, so availability is probed
 * on a short interval (TCP to a public DNS resolver — no payload, no
 * accounts). The probe follows the relay routing rule: when Tor is on it
 * dials through the same SOCKS address, so it never beacons the real IP
 * around the tunnel; transitions drive the same actions as Android:
 *
 * - network back ([Transition.ONLINE]): give previously-dead relays a fresh
 *   chance ([RelayHealthTracker.reset]) and connect + reconnect;
 * - network lost ([Transition.OFFLINE]): disconnect intentionally so the
 *   relay listeners do not schedule a reconnect storm against a dead link —
 *   unless a local relay is in use, which keeps working without internet;
 * - every [UPDATE_FILTER_PERIOD_MS], refresh the subscriptions as a safety
 *   net so relays excluded as dead come back once healthy.
 */
object NetworkConnectivity {
    private const val PROBE_TIMEOUT_MS = 3000
    private const val CHECK_INTERVAL_MS = 10_000L
    private const val UPDATE_FILTER_PERIOD_MS = 5 * 60 * 1000L

    private val probeTargets = listOf(
        InetSocketAddress("1.1.1.1", 53),
        InetSocketAddress("8.8.8.8", 53),
    )

    /**
     * One probe socket per target; overridden in tests to dial a hermetic fake
     * instead of the internet. The default follows the relay routing rule:
     * through the same SOCKS address relays dial when Tor is on — a direct
     * probe would beacon the real IP around the Tor route on every check —
     * and direct when Tor is disabled. Targets are IP literals, so nothing
     * inside the probe resolves DNS.
     */
    internal var newProbeSocket: (InetSocketAddress) -> Socket = { target ->
        val settings = SettingsStore.settings.value
        val proxy = probeProxy(target, settings.torMode, settings.proxyPort, TorManager.socksPort.value)
        if (proxy == null) Socket() else Socket(proxy)
    }

    /**
     * The SOCKS proxy for a probe target when Tor routing is on — exactly
     * [RelayHttpClients.socksPortFor], including the fail-closed port-0
     * placeholder while built-in Tor is down (the probe then fails like the
     * relays do instead of leaking to the clearnet). Null dials directly.
     */
    internal fun probeProxy(
        target: InetSocketAddress,
        mode: TorMode,
        externalPort: Int,
        builtinPort: Int,
    ): Proxy? = RelayHttpClients.socksPortFor(target.hostString, mode, externalPort, builtinPort)
        ?.let { Proxy(Proxy.Type.SOCKS, InetSocketAddress(RelayHttpClients.LOCALHOST, it)) }

    enum class Transition { OPENED, ONLINE, OFFLINE, UNCHANGED }

    /** Pure transition rule: `wasOnline == null` is the startup probe. */
    fun transition(wasOnline: Boolean?, nowOnline: Boolean): Transition = when {
        wasOnline == null -> Transition.OPENED
        !wasOnline && nowOnline -> Transition.ONLINE
        wasOnline && !nowOnline -> Transition.OFFLINE
        else -> Transition.UNCHANGED
    }

    fun start(scope: CoroutineScope) {
        scope.launch {
            var wasOnline: Boolean? = null
            while (true) {
                val nowOnline = isOnline()
                when (transition(wasOnline, nowOnline)) {
                    Transition.ONLINE -> {
                        RelayHealthTracker.reset()
                        if (!PassphraseLock.isLocked()) {
                            if (!AmberDesktop.client.isActive()) {
                                AmberDesktop.client.connect()
                            }
                            AmberDesktop.client.reconnect(true)
                        }
                    }

                    // Local relays keep working without internet: only drop
                    // everything when none are in use (remote ones then fail
                    // and back off on their own).
                    Transition.OFFLINE -> if (!usesLocalRelays()) AmberDesktop.disconnectIntentionally()

                    Transition.OPENED, Transition.UNCHANGED -> {}
                }
                wasOnline = nowOnline
                delay(CHECK_INTERVAL_MS)
            }
        }
        scope.launch {
            delay(UPDATE_FILTER_PERIOD_MS)
            while (true) {
                AmberDesktop.engine.updateFilter()
                delay(UPDATE_FILTER_PERIOD_MS)
            }
        }
    }

    private fun usesLocalRelays(): Boolean = AmberDesktop.client.availableRelaysFlow().value.any { LocalRelays.isLocal(it) }

    /** TCP reachability of any probe target; false on any failure. */
    internal fun isOnline(): Boolean = probeTargets.any { target ->
        runCatching {
            newProbeSocket(target).use { socket ->
                socket.connect(target, PROBE_TIMEOUT_MS)
                true
            }
        }.getOrDefault(false)
    }
}
