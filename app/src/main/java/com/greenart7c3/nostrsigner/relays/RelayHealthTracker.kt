package com.greenart7c3.nostrsigner.relays

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks consecutive connection failures per relay so the app can stop retrying
 * relays that are permanently unreachable.
 *
 * Quartz's relay pool is driven by the relays referenced in active subscriptions:
 * [NotificationSubscription.updateFilter] re-subscribes every 30s, and any relay
 * present in that map is (re)connected by the pool. Without this tracker an
 * offline relay stays in the map forever, so a socket is opened to it on every
 * refresh, needlessly waking the radio and draining the battery.
 *
 * A relay is considered dead after [MAX_RECONNECT_ATTEMPTS] consecutive failures.
 * Dead relays are excluded from the subscription relay sets so the pool drops
 * them entirely. The streak is cleared when a relay connects successfully
 * ([recordSuccess]), and [reset] is called on every OS network change and on a
 * manual reconnect so previously-dead relays get a fresh chance.
 */
object RelayHealthTracker {
    private const val MAX_RECONNECT_ATTEMPTS = 10

    private val failureCounts = ConcurrentHashMap<NormalizedRelayUrl, Int>()

    /** Records a failed connection attempt. Returns true while the relay is still worth retrying. */
    fun recordFailure(relay: NormalizedRelayUrl): Boolean {
        val failures = failureCounts.merge(relay, 1, Int::plus) ?: 1
        return failures <= MAX_RECONNECT_ATTEMPTS
    }

    /** Clears the failure streak after a successful connection. */
    fun recordSuccess(relay: NormalizedRelayUrl) {
        failureCounts.remove(relay)
    }

    /** True once a relay has failed [MAX_RECONNECT_ATTEMPTS] times in a row without recovering. */
    fun isDead(relay: NormalizedRelayUrl): Boolean = (failureCounts[relay] ?: 0) > MAX_RECONNECT_ATTEMPTS

    /** Forgets all failure history so every relay is eligible to be retried again. */
    fun reset() {
        failureCounts.clear()
    }
}
