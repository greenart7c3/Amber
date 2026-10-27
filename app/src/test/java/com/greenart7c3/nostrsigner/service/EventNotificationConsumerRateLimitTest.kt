package com.greenart7c3.nostrsigner.service

import android.app.NotificationManager
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.BunkerEventDao
import com.greenart7c3.nostrsigner.database.CachingApplicationDao
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Every verified kind-24133 event from a sender that is not a stored
 * connection used to cost a Room write, a decrypt and usually a signed reply.
 * Such senders must be dropped once over budget, before any of that work,
 * while stored connections are never limited.
 */
class EventNotificationConsumerRateLimitTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })
    private val relay = NormalizedRelayUrl("wss://relay.example.com/")
    private val appDao = mockk<ApplicationDao>(relaxed = true)
    private val bunkerEventDao = mockk<BunkerEventDao>(relaxed = true)
    private lateinit var consumer: EventNotificationConsumer
    private lateinit var accountHex: String

    @Before
    fun setUp() {
        EventNotificationConsumer.senderRateLimiter.resetForTest()
        val db = mockk<AppDatabase>(relaxed = true)
        every { db.bunkerEventDao() } returns bunkerEventDao
        coEvery { bunkerEventDao.exists(any()) } returns false
        coEvery { appDao.getByKeyRaw(any()) } returns null

        val amber = mockk<Amber>(relaxed = true)
        every { amber.applicationIOScope } returns scope
        every { amber.getDatabase(any()) } returns db
        every { amber.dao(any()) } returns CachingApplicationDao(appDao)
        installAmberInstance(amber)

        val account = newTestAccount(scope)
        accountHex = account.hexKey
        mockkObject(LocalPreferences, NotificationUtils, AmberLog)
        every { AmberLog.d(any(), any<String>()) } returns Unit
        every { AmberLog.d(any(), any(), any()) } returns Unit
        every { LocalPreferences.loadFromEncryptedStorageSync(any(), any()) } returns account
        coEvery { LocalPreferences.loadAccount(any(), any()) } returns account
        every { LocalPreferences.allSavedAccounts(any()) } returns emptyList()
        every { NotificationUtils.getOrCreateBunkerChannel(any()) } returns mockk(relaxed = true)
        every { NotificationUtils.getOrCreateErrorsChannel(any()) } returns mockk(relaxed = true)

        val notificationManager = mockk<NotificationManager>()
        every { notificationManager.areNotificationsEnabled() } returns true
        consumer = spyk(EventNotificationConsumer(mockk(relaxed = true)))
        every { consumer.notificationManager() } returns notificationManager
    }

    @After
    fun tearDown() {
        unmockkObject(LocalPreferences, NotificationUtils, AmberLog)
        EventNotificationConsumer.senderRateLimiter.resetForTest()
        scope.cancel()
    }

    // Empty content: the event is fully admitted (recorded in the persistent
    // cache) but notify() stops before decrypting, which keeps the test to
    // the gate itself. A distinct created_at gives each event its own id.
    private fun eventFrom(sender: NostrSignerInternal, i: Int): Event = sender.signerSync.sign(TimeUtils.now() - i, NostrConnectEvent.KIND, arrayOf(arrayOf("p", accountHex)), "")

    /**
     * Feeds pre-signed [events] and checks that exactly the [capacity] budget
     * got through, plus whatever the bucket legitimately refilled while the
     * loop ran (signature checks are slow on a loaded test machine).
     */
    private suspend fun assertAdmitted(events: List<Event>, capacity: Int) {
        val started = System.currentTimeMillis()
        events.forEach { consumer.consume(it, relay) }
        val elapsedMs = System.currentTimeMillis() - started
        val refill = (elapsedMs * capacity / (SenderRateLimiter.DEFAULT_WINDOW_SECONDS * 1000)).toInt() + 1
        coVerify(atLeast = capacity, atMost = capacity + refill) { bunkerEventDao.insert(any()) }
    }

    @Test
    fun unknownSenderFloodIsDroppedBeforeAnyPerEventWork() = runBlocking {
        val attacker = NostrSignerInternal(KeyPair())
        assertAdmitted(List(50) { eventFrom(attacker, it) }, SenderRateLimiter.DEFAULT_CAPACITY)
    }

    @Test
    fun freshKeyFloodIsCappedInTotal() = runBlocking {
        assertAdmitted(List(200) { eventFrom(NostrSignerInternal(KeyPair()), 0) }, SenderRateLimiter.DEFAULT_AGGREGATE_CAPACITY)
    }

    @Test
    fun staleEventFloodOnlyLogsWithinTheBudget() = runBlocking {
        // Signed but outside the freshness window: each rejection logs the
        // event id, so they must be budgeted like any other event.
        val attacker = NostrSignerInternal(KeyPair())
        val stale = List(50) { attacker.signerSync.sign<Event>(TimeUtils.now() - 3600 - it, NostrConnectEvent.KIND, arrayOf(arrayOf("p", accountHex)), "") }
        val started = System.currentTimeMillis()
        stale.forEach { consumer.consume(it, relay) }
        val refill = ((System.currentTimeMillis() - started) * SenderRateLimiter.DEFAULT_CAPACITY / (SenderRateLimiter.DEFAULT_WINDOW_SECONDS * 1000)).toInt() + 1

        verify(atLeast = SenderRateLimiter.DEFAULT_CAPACITY, atMost = SenderRateLimiter.DEFAULT_CAPACITY + refill) {
            consumer.saveLog(match { it.contains("too old") }, any(), any())
        }
        coVerify(exactly = 0) { bunkerEventDao.insert(any()) }
    }

    @Test
    fun storedConnectionIsNeverLimited() = runBlocking {
        val app = NostrSignerInternal(KeyPair())
        coEvery { appDao.getByKeyRaw(app.keyPair.pubKey.toHexKey()) } returns mockk(relaxed = true)
        repeat(100) { consumer.consume(eventFrom(app, it), relay) }

        coVerify(exactly = 100) { bunkerEventDao.insert(any()) }
    }
}
