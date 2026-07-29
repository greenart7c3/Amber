package com.greenart7c3.nostrsigner.service

import androidx.collection.LruCache
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test

class NotificationSubscriptionTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sentSubIds = Collections.synchronizedList(mutableListOf<String>())
    private lateinit var client: NostrClient
    private lateinit var dao: ApplicationDao
    private lateinit var account: Account
    private lateinit var subscription: NotificationSubscription

    @Before
    fun setUp() {
        client = mockk(relaxed = true)
        every { client.subscribe(capture(sentSubIds), any()) } returns Unit

        dao = mockk()
        val database = mockk<AppDatabase>()
        every { database.dao() } returns dao

        val amber = mockk<Amber>(relaxed = true)
        every { amber.getDatabase(any()) } returns database
        every { amber.notificationCache } returns LruCache(512)
        installAmberInstance(amber)

        mockkObject(LocalPreferences)
        account = newTestAccount(scope)
        coEvery { LocalPreferences.allAccounts(any()) } returns listOf(account)

        subscription = NotificationSubscription(client, mockk(relaxed = true))
    }

    @After
    fun tearDown() {
        unmockkObject(LocalPreferences)
        scope.cancel()
    }

    private fun connection() = ApplicationEntity.empty().copy(
        key = "conn1",
        relays = listOf(NormalizedRelayUrl("wss://relay1.example.com")),
        localKey = "ab".repeat(32),
    )

    @Test
    fun `updateFilter subscribes once per connection with a local key`() = runBlocking {
        // updateFilter is a no-op on the offline flavor by design
        assumeFalse(BuildFlavorChecker.isOfflineFlavor())
        coEvery { dao.getAll(account.hexKey) } returns listOf(connection())
        every { dao.getAllRelayLists() } returns emptyList()

        subscription.updateFilter()

        assertEquals(1, sentSubIds.size)
    }

    @Test
    fun `updateFilter reuses the same subscription id on refresh`() = runBlocking {
        assumeFalse(BuildFlavorChecker.isOfflineFlavor())
        coEvery { dao.getAll(account.hexKey) } returns listOf(connection())
        every { dao.getAllRelayLists() } returns emptyList()

        subscription.updateFilter()
        subscription.updateFilter()

        assertEquals(2, sentSubIds.size)
        assertEquals(sentSubIds[0], sentSubIds[1])
    }

    @Test
    fun `updateFilter unsubscribes connections that disappeared`() = runBlocking {
        assumeFalse(BuildFlavorChecker.isOfflineFlavor())
        coEvery { dao.getAll(account.hexKey) } returns listOf(connection())
        every { dao.getAllRelayLists() } returns emptyList()
        subscription.updateFilter()
        val subId = sentSubIds.single()

        coEvery { dao.getAll(account.hexKey) } returns emptyList()
        subscription.updateFilter()

        verify(exactly = 1) { client.unsubscribe(subId) }
    }

    /**
     * Regression test for the NegativeArraySizeException class of bugs: onIncomingMessage
     * iterates subIds (containsValue) on relay I/O threads while updateFilter/closeAllSubs
     * mutate it from coroutines.
     */
    @Test
    fun `updateFilter closeAllSubs and onIncomingMessage do not throw when run concurrently`() {
        coEvery { dao.getAll(account.hexKey) } returns listOf(connection())
        every { dao.getAllRelayLists() } returns emptyList()

        val relay = mockk<IRelayClient>()
        val event = mockk<Event>(relaxed = true)

        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val threads = mutableListOf<Thread>()

        repeat(2) {
            threads += Thread {
                repeat(100) {
                    try {
                        runBlocking { subscription.updateFilter() }
                    } catch (e: Throwable) {
                        errors.add(e)
                    }
                }
            }
        }
        threads += Thread {
            repeat(100) {
                try {
                    subscription.closeAllSubs()
                } catch (e: Throwable) {
                    errors.add(e)
                }
            }
        }
        repeat(2) {
            threads += Thread {
                repeat(300) { i ->
                    try {
                        subscription.onIncomingMessage(relay, "", EventMessage("unknown-sub-$i", event))
                    } catch (e: Throwable) {
                        errors.add(e)
                    }
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertTrue(
            "Expected no exceptions, got: ${errors.firstOrNull()?.stackTraceToString()}",
            errors.isEmpty(),
        )
    }
}
