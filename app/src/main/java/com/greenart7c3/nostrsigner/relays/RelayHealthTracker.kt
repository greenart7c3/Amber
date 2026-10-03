package com.greenart7c3.nostrsigner.relays

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks consecutive connection failures per relay so the app can stop retrying
 * relays that are unreachable.
 *
 * Quartz's relay pool is driven by the relays referenced in active subscriptions:
 * [NotificationSubscription.updateFilter] re-subscribes on every refresh, and any relay
 * present in that map is (re)connected by the pool. Without this tracker an
 * offline relay stays in the map forever, so a socket is opened to it on every
 * refresh, needlessly waking the radio and draining the battery.
 *
 * A relay is considered dead after [MAX_RECONNECT_ATTEMPTS] consecutive failures.
 * Dead relays are excluded from the subscription relay sets so the pool drops
 * them entirely. The streak is cleared when a relay connects successfully
 * ([recordSuccess]), and [reset] is called on every OS network change and on a
 * manual reconnect so previously-dead relays get a fresh chance.
 *
 * Being dead is not permanent: [DEAD_COOLDOWN_MS] after its last failure a relay is
 * eligible again, so the periodic subscription refresh re-adds it. Without the
 * cooldown a stretch of bad connectivity that never surfaced as an OS network change
 * (a Wi-Fi that lost internet and got it back, a weak cell signal, a relay outage)
 * marked every relay dead and the app stayed disconnected until it was force closed.
 */
object RelayHealthTracker {
    private const val MAX_RECONNECT_ATTEMPTS = 10
    const val DEAD_COOLDOWN_MS = 15 * 60 * 1000L

    private class Health(val failures: Int, val lastFailureAt: Long)

    private val health = ConcurrentHashMap<NormalizedRelayUrl, Health>()

    /** Injectable for tests. */
    internal var clock: () -> Long = System::currentTimeMillis

    /** Records a failed connection attempt. Returns true while the relay is still worth retrying. */
    fun recordFailure(relay: NormalizedRelayUrl): Boolean {
        val now = clock()
        val updated = health.merge(relay, Health(1, now)) { old, _ -> Health(old.failures + 1, now) }
        return updated == null || updated.failures <= MAX_RECONNECT_ATTEMPTS
    }

    /** Clears the failure streak after a successful connection. */
    fun recordSuccess(relay: NormalizedRelayUrl) {
        health.remove(relay)
    }

    /**
     * True once a relay has failed more than [MAX_RECONNECT_ATTEMPTS] times in a row without
     * recovering, until [DEAD_COOLDOWN_MS] has passed since its last failure.
     */
    fun isDead(relay: NormalizedRelayUrl): Boolean {
        val entry = health[relay] ?: return false
        return entry.failures > MAX_RECONNECT_ATTEMPTS && clock() - entry.lastFailureAt < DEAD_COOLDOWN_MS
    }

    /** Forgets all failure history so every relay is eligible to be retried again. */
    fun reset() {
        health.clear()
    }
}
