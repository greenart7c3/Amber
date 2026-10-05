package com.greenart7c3.nostrsigner.relays

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RelayHealthTrackerTest {
    private val relay = NormalizedRelayUrl("wss://relay.example.com/")
    private var now = 1_000_000L

    @Before
    fun setUp() {
        RelayHealthTracker.reset()
        RelayHealthTracker.clock = { now }
    }

    @After
    fun tearDown() {
        RelayHealthTracker.reset()
        RelayHealthTracker.clock = System::currentTimeMillis
    }

    private fun failUntilDead() {
        repeat(10) { assertTrue(RelayHealthTracker.recordFailure(relay)) }
        assertFalse(RelayHealthTracker.recordFailure(relay))
    }

    @Test
    fun relayIsDeadAfterTooManyFailures() {
        failUntilDead()
        assertTrue(RelayHealthTracker.isDead(relay))
    }

    @Test
    fun deadRelayGetsAnotherChanceAfterTheCooldown() {
        failUntilDead()
        now += RelayHealthTracker.DEAD_COOLDOWN_MS
        assertFalse(RelayHealthTracker.isDead(relay))
    }

    @Test
    fun failingAgainAfterTheCooldownRestartsIt() {
        failUntilDead()
        now += RelayHealthTracker.DEAD_COOLDOWN_MS
        assertFalse(RelayHealthTracker.recordFailure(relay))
        assertTrue(RelayHealthTracker.isDead(relay))
    }

    @Test
    fun successAndResetClearTheDeadState() {
        failUntilDead()
        RelayHealthTracker.recordSuccess(relay)
        assertFalse(RelayHealthTracker.isDead(relay))

        failUntilDead()
        RelayHealthTracker.reset()
        assertFalse(RelayHealthTracker.isDead(relay))
    }
}
