package com.greenart7c3.nostrsigner.service

import android.net.NetworkCapabilities.TRANSPORT_VPN

/**
 * What the relay connections care about in the device's default network.
 *
 * @param networkId the network's handle; a different value means a different network
 * (switching Wi-Fi <-> mobile, or a VPN taking over as the default network).
 * @param transports the transports the network reports (Wi-Fi, cellular, ethernet, VPN, ...).
 * A VPN network also reports its underlying transport, so a VPN moving from Wi-Fi to
 * mobile shows up here even though the default network itself stays the same.
 * @param validated whether Android verified the network actually reaches the internet.
 */
data class NetworkSnapshot(
    val networkId: Long,
    val transports: Set<Int>,
    val validated: Boolean,
)

/**
 * Decides when a default-network callback means the relay sockets must be rebuilt.
 *
 * Sockets opened on one network do not survive a move to another: the old routes are
 * gone (or, with a VPN, the traffic now takes a different path) but the sockets keep
 * looking connected until a ping times out. Callbacks arrive in bursts and repeat the
 * same state, so this keeps the last snapshot and only reports real changes.
 */
class NetworkChangeDetector {
    private var last: NetworkSnapshot? = null
    private var lost = false

    /**
     * Records the current default network. Returns why the relays should be reset,
     * or null when nothing that matters changed.
     */
    @Synchronized
    fun onNetwork(current: NetworkSnapshot): String? {
        val previous = last
        last = current

        if (previous == null) {
            // The first report after registering the callback is the network the
            // relays are already connecting on; only a return after a loss is a change.
            val wasLost = lost
            lost = false
            return if (wasLost) "network available again" else null
        }

        return when {
            previous.networkId != current.networkId -> describeSwitch(previous, current)
            previous.transports != current.transports -> describeSwitch(previous, current)
            !previous.validated && current.validated -> "network regained internet access"
            else -> null
        }
    }

    /**
     * Records that [networkId] went away. Returns true when it was the current default
     * network; the loss of a network that was already replaced changes nothing.
     */
    @Synchronized
    fun onLost(networkId: Long): Boolean {
        if (last?.networkId != networkId) return false
        last = null
        lost = true
        return true
    }

    private fun describeSwitch(previous: NetworkSnapshot, current: NetworkSnapshot): String {
        val wasVpn = TRANSPORT_VPN in previous.transports
        val isVpn = TRANSPORT_VPN in current.transports
        return when {
            !wasVpn && isVpn -> "VPN connected"
            wasVpn && !isVpn -> "VPN disconnected"
            else -> "network changed"
        }
    }
}
