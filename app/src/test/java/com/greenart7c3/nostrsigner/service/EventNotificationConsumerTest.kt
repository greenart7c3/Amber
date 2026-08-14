package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.BunkerEventDao
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUsers
import com.vitorpamplona.quartz.nip40Expiration.ExpirationTag
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class EventNotificationConsumerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var consumer: EventNotificationConsumer
    private lateinit var bunkerEventDao: BunkerEventDao
    private lateinit var amber: Amber
    private lateinit var account: Account
    private lateinit var dao: ApplicationDao

    @Before
    fun setUp() {
        mockkStatic("com.vitorpamplona.quartz.nip01Core.crypto.EventKt")
        mockkStatic("com.vitorpamplona.quartz.nip01Core.tags.people.EventExtKt")
        mockkStatic("com.vitorpamplona.quartz.nip40Expiration.EventExtKt")

        bunkerEventDao = mockk(relaxed = true)
        dao = mockk(relaxed = true)
        val database = mockk<AppDatabase>(relaxed = true)
        every { database.bunkerEventDao() } returns bunkerEventDao
        every { database.dao() } returns dao

        amber = mockk(relaxed = true)
        every { amber.getDatabase(any()) } returns database
        every { amber.dao(any()) } returns dao
        installAmberInstance(amber)

        mockkObject(LocalPreferences)
        account = newTestAccount(scope)

        consumer = spyk(EventNotificationConsumer(mockk(relaxed = true)))
        every { consumer["notificationManager"]() } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkObject(LocalPreferences)
        unmockkStatic("com.vitorpamplona.quartz.nip01Core.crypto.EventKt")
        unmockkStatic("com.vitorpamplona.quartz.nip01Core.tags.people.EventExtKt")
        unmockkStatic("com.vitorpamplona.quartz.nip40Expiration.EventExtKt")
    }

    @Test
    fun `consume rejects old events`() = runBlocking {
        val event = mockk<Event>()
        every { event.verify() } returns true
        every { event.kind } returns NostrConnectEvent.KIND
        every { event.createdAt } returns (TimeUtils.now() - TimeUtils.FIVE_MINUTES - 1)

        consumer.consume(event, NormalizedRelayUrl("wss://relay.com"))

        verify(exactly = 0) { event.tags }
    }

    @Test
    fun `consume rejects future events`() = runBlocking {
        val event = mockk<Event>()
        every { event.verify() } returns true
        every { event.kind } returns NostrConnectEvent.KIND
        every { event.createdAt } returns (TimeUtils.now() + TimeUtils.FIVE_MINUTES + 1)

        consumer.consume(event, NormalizedRelayUrl("wss://relay.com"))

        verify(exactly = 0) { event.tags }
    }

    @Test
    fun `consume rejects expired events`() = runBlocking {
        val event = mockk<Event>()
        every { event.verify() } returns true
        every { event.kind } returns NostrConnectEvent.KIND
        every { event.createdAt } returns TimeUtils.now()
        every { event.tags } returns arrayOf(arrayOf(ExpirationTag.TAG_NAME, (TimeUtils.now() - 10).toString()))

        consumer.consume(event, NormalizedRelayUrl("wss://relay.com"))

        // If it passed expiration, it would call taggedUsers()
        verify(exactly = 0) { event.taggedUsers() }
    }

    @Test
    fun `consume rejects duplicate events persistently`() = runBlocking {
        val event = mockk<Event>()
        every { event.id } returns "event1"
        every { event.verify() } returns true
        every { event.kind } returns NostrConnectEvent.KIND
        every { event.createdAt } returns TimeUtils.now()
        every { event.tags } returns arrayOf(arrayOf("p", account.hexKey))

        coEvery { LocalPreferences.loadFromEncryptedStorageSync(any(), any()) } returns account
        coEvery { bunkerEventDao.exists("event1") } returns true

        consumer.consume(event, NormalizedRelayUrl("wss://relay.com"))

        coVerify(exactly = 1) { bunkerEventDao.exists("event1") }
        coVerify(exactly = 0) { bunkerEventDao.insert(any()) }
    }
}
