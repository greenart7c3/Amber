package com.greenart7c3.nostrsigner.relays

import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.common.relays.RelayHealthTracker
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.RelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NostrClientLoggerListenerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val relayUrl = NormalizedRelayUrl("wss://relay.example.com/")
    private lateinit var relayClient: RelayClient
    private lateinit var relay: IRelayClient
    private lateinit var markedDeadCalls: AtomicInteger
    private lateinit var listener: NostrClientLoggerListener

    @Before
    fun setUp() {
        RelayHealthTracker.reset()
        mockkObject(AmberLog, SavedAccounts)
        every { AmberLog.d(any(), any()) } returns Unit
        every { AmberLog.d(any(), any(), any()) } returns Unit
        every { SavedAccounts.current(any()) } returns null
        every { SavedAccounts.all(any()) } returns emptyList()

        relayClient = mockk(relaxed = true)
        every { relayClient.intentionalDisconnectTime } returns 0L
        relay = mockk(relaxed = true)
        every { relay.url } returns relayUrl
        markedDeadCalls = AtomicInteger(0)

        listener = NostrClientLoggerListener(
            context = mockk(relaxed = true),
            stats = mockk(relaxed = true),
            scope = scope,
            relayClient = relayClient,
            accountRepository = mockk<AccountRepository>(relaxed = true),
            onRelayMarkedDead = { markedDeadCalls.incrementAndGet() },
        )
    }

    @After
    fun tearDown() {
        RelayHealthTracker.reset()
        unmockkObject(AmberLog, SavedAccounts)
        scope.cancel()
    }

    @Test
    fun `onDisconnected does not call reconnect or increment relay failure count`() {
        repeat(20) { listener.onDisconnected(relay) }

        verify(exactly = 0) { relayClient.reconnect() }
        assertFalse(RelayHealthTracker.isDead(relayUrl))
        assertEquals(0, markedDeadCalls.get())
    }

    @Test
    fun `onCannotConnect never calls reconnect and notifies when relay is marked dead`() {
        repeat(10) {
            listener.onCannotConnect(relay, "connect failed")
            assertFalse(RelayHealthTracker.isDead(relayUrl))
            assertEquals(0, markedDeadCalls.get())
        }

        listener.onCannotConnect(relay, "connect failed")

        assertTrue(RelayHealthTracker.isDead(relayUrl))
        assertEquals(1, markedDeadCalls.get())
        verify(exactly = 0) { relayClient.reconnect() }
    }

    @Test
    fun `onCannotConnect within intentional disconnect window is ignored`() {
        every { relayClient.intentionalDisconnectTime } returns System.currentTimeMillis()

        repeat(15) { listener.onCannotConnect(relay, "canceled") }

        assertFalse(RelayHealthTracker.isDead(relayUrl))
        assertEquals(0, markedDeadCalls.get())
    }

    @Test
    fun `onConnected clears previous failure streak`() {
        repeat(10) { listener.onCannotConnect(relay, "connect failed") }
        listener.onConnected(relay, 40, false)
        listener.onCannotConnect(relay, "connect failed")

        assertFalse(RelayHealthTracker.isDead(relayUrl))
        assertEquals(0, markedDeadCalls.get())
    }
}
