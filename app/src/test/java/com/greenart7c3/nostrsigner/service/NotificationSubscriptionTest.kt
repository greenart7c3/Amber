package com.greenart7c3.nostrsigner.service

import androidx.collection.LruCache
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.CachingApplicationDao
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        // updateFilter reads through the caching wrapper; a fresh wrapper per call
        // mirrors per-test dao state without cross-test cache leakage.
        every { amber.dao(any()) } answers { CachingApplicationDao(dao) }
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
        LocalKeyAccountIndex.replaceAll(emptyMap())
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
                        runBlocking { subscription.onIncomingMessage(relay, "", EventMessage("unknown-sub-$i", event)) }
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

    @Test
    fun `all endpoint routes exist before the first subscription becomes live`() = runBlocking {
        assumeFalse(BuildFlavorChecker.isOfflineFlavor())
        val first = connection()
        val second = connection().copy(key = "conn2", localKey = "cd".repeat(32))
        coEvery { dao.getAll(account.hexKey) } returns listOf(first, second)
        every { client.subscribe(any(), any()) } answers {
            assertEquals(account.npub, LocalKeyAccountIndex.lookup(first.localPubKey)?.npub)
            assertEquals(account.npub, LocalKeyAccountIndex.lookup(second.localPubKey)?.npub)
        }
        subscription.updateFilter()
        verify(exactly = 2) { client.subscribe(any(), any()) }
    }

    @Test
    fun `overlapping refreshes cannot restore an older routing snapshot`() = runBlocking {
        assumeFalse(BuildFlavorChecker.isOfflineFlavor())
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val old = connection()
        val added = connection().copy(key = "conn2", localKey = "cd".repeat(32))
        var reads = 0
        coEvery { dao.getAll(account.hexKey) } coAnswers {
            reads++
            if (reads == 1) {
                started.complete(Unit)
                release.await()
                listOf(old)
            } else {
                listOf(added)
            }
        }
        val first = async(start = CoroutineStart.UNDISPATCHED) { subscription.updateFilter() }
        started.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) { subscription.updateFilter() }
        assertEquals(1, reads)
        release.complete(Unit)
        first.await()
        second.await()
        assertNull(LocalKeyAccountIndex.lookup(old.localPubKey))
        assertEquals(account.npub, LocalKeyAccountIndex.lookup(added.localPubKey)?.npub)
    }

    @Test
    fun `close during a suspended refresh prevents that refresh from resubscribing`() = runBlocking {
        assumeFalse(BuildFlavorChecker.isOfflineFlavor())
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { dao.getAll(account.hexKey) } coAnswers {
            started.complete(Unit)
            release.await()
            listOf(connection())
        }
        val refreshing = async(start = CoroutineStart.UNDISPATCHED) { subscription.updateFilter() }
        started.await()
        subscription.closeAllSubs()
        release.complete(Unit)
        refreshing.await()
        verify(exactly = 0) { client.subscribe(any(), any()) }
        assertNull(LocalKeyAccountIndex.lookup(connection().localPubKey))
    }

    @Test
    fun `close cancels refreshes already queued behind another refresh`() = runBlocking {
        assumeFalse(BuildFlavorChecker.isOfflineFlavor())
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var reads = 0
        coEvery { dao.getAll(account.hexKey) } coAnswers {
            reads++
            if (reads == 1) {
                started.complete(Unit)
                release.await()
            }
            listOf(connection())
        }
        val first = async(start = CoroutineStart.UNDISPATCHED) { subscription.updateFilter() }
        started.await()
        val queued = async(start = CoroutineStart.UNDISPATCHED) { subscription.updateFilter() }
        subscription.closeAllSubs()
        release.complete(Unit)
        first.await()
        queued.await()
        verify(exactly = 0) { client.subscribe(any(), any()) }
        assertEquals(1, reads)
        // A deliberate fresh refresh after close still restores the connection.
        subscription.updateFilter()
        verify(exactly = 1) { client.subscribe(any(), any()) }
    }
}
