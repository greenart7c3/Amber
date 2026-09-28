package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.NetworkConnectivity
import com.greenart7c3.nostrsigner.desktop.core.RelayHealthTracker
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayHealthTrackerTest {
    private val relay = NormalizedRelayUrl(url = "wss://relay.example.com")

    @Test
    fun allowsTenConsecutiveFailuresThenDies() {
        RelayHealthTracker.reset()
        repeat(10) { attempt ->
            assertTrue("attempt ${attempt + 1} should still be retried", RelayHealthTracker.recordFailure(relay))
            assertFalse(RelayHealthTracker.isDead(relay))
        }
        assertFalse(RelayHealthTracker.recordFailure(relay))
        assertTrue(RelayHealthTracker.isDead(relay))
    }

    @Test
    fun successClearsTheFailureStreak() {
        RelayHealthTracker.reset()
        repeat(8) { RelayHealthTracker.recordFailure(relay) }
        RelayHealthTracker.recordSuccess(relay)
        assertFalse(RelayHealthTracker.isDead(relay))
        assertTrue(RelayHealthTracker.recordFailure(relay))
    }

    @Test
    fun resetGivesEveryRelayAFreshChance() {
        RelayHealthTracker.reset()
        repeat(12) { RelayHealthTracker.recordFailure(relay) }
        assertTrue(RelayHealthTracker.isDead(relay))
        RelayHealthTracker.reset()
        assertFalse(RelayHealthTracker.isDead(relay))
        assertTrue(RelayHealthTracker.recordFailure(relay))
    }

    @Test
    fun relaysAreTrackedIndependently() {
        RelayHealthTracker.reset()
        val other = NormalizedRelayUrl(url = "wss://other.example.com")
        repeat(12) { RelayHealthTracker.recordFailure(relay) }
        assertTrue(RelayHealthTracker.isDead(relay))
        assertFalse(RelayHealthTracker.isDead(other))
        assertTrue(RelayHealthTracker.recordFailure(other))
    }
}

class NetworkConnectivityTransitionTest {
    @Test
    fun transitions() {
        // Startup probe only records state, mirroring Android's first
        // onAvailable (no reconnect before the boot connect path runs).
        assertEquals(NetworkConnectivity.Transition.OPENED, NetworkConnectivity.transition(null, true))
        assertEquals(NetworkConnectivity.Transition.OPENED, NetworkConnectivity.transition(null, false))
        assertEquals(NetworkConnectivity.Transition.ONLINE, NetworkConnectivity.transition(false, true))
        assertEquals(NetworkConnectivity.Transition.OFFLINE, NetworkConnectivity.transition(true, false))
        assertEquals(NetworkConnectivity.Transition.UNCHANGED, NetworkConnectivity.transition(true, true))
        assertEquals(NetworkConnectivity.Transition.UNCHANGED, NetworkConnectivity.transition(false, false))
    }
}
