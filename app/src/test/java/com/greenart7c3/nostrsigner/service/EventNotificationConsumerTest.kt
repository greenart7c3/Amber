package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.AccountStorage
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.common.BUNKER_REPLY_ATTEMPTS
import com.greenart7c3.nostrsigner.common.UNVERIFIED_REPLY_ATTEMPTS
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.BunkerEventDao
import com.greenart7c3.nostrsigner.database.relaxedApplicationDaos
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUsers
import com.vitorpamplona.quartz.nip40Expiration.ExpirationTag
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class EventNotificationConsumerTest {
    private val bunkerResponder by lazy {
        spyk(
            BunkerResponder(
                amber,
                amber,
                amber.accountDatabases,
                amber.relayClient,
                object : CoroutineScope {
                    override val coroutineContext get() = amber.applicationIOScope.coroutineContext
                },
                mockk(relaxed = true),
            ) { null },
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var consumer: EventNotificationConsumer
    private lateinit var bunkerEventDao: BunkerEventDao
    private lateinit var amber: Amber
    private lateinit var account: Account
    private lateinit var dao: ApplicationDao

    @Before
    fun setUp() {
        mockkStatic("com.vitorpamplona.quartz.nip01Core.crypto.EventExtKt")
        mockkStatic("com.vitorpamplona.quartz.nip01Core.tags.people.EventExtKt")
        mockkStatic("com.vitorpamplona.quartz.nip40Expiration.EventExtKt")

        bunkerEventDao = mockk(relaxed = true)
        dao = mockk(relaxed = true)
        val database = mockk<AppDatabase>(relaxed = true)
        every { database.bunkerEventDao() } returns bunkerEventDao
        every { database.dao() } returns dao

        amber = mockk(relaxed = true)
        every { amber.applicationIOScope } returns scope
        every { amber.accountDatabases.getDatabase(any()) } returns database
        every { amber.accountDatabases.daos(any()) } returns relaxedApplicationDaos(applicationDao = dao)

        mockkObject(AmberLog)
        every { AmberLog.d(any(), any()) } returns Unit
        every { AmberLog.d(any(), any(), any()) } returns Unit

        mockkObject(LocalPreferences, AccountStorage)
        account = newTestAccount(scope)

        consumer = spyk(
            EventNotificationConsumer(
                mockk(relaxed = true),
                settingsStore = amber,
                accountRepository = amber.accountDatabases,
                scope = object : CoroutineScope {
                    override val coroutineContext get() = amber.applicationIOScope.coroutineContext
                },
                notificationCache = amber.notificationCache,
                bunkerResponder = bunkerResponder,
            ) { amber.notificationSubscription.updateFilter() },
        )
        every { consumer.saveLog(any(), any(), any()) } returns Unit
        val nm = mockk<android.app.NotificationManager>(relaxed = true)
        every { nm.areNotificationsEnabled() } returns true
        every { consumer.notificationManager() } returns nm

        mockkObject(NotificationUtils)
        every { NotificationUtils.getOrCreateBunkerChannel(any()) } returns mockk(relaxed = true)
        every { NotificationUtils.getOrCreateErrorsChannel(any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkObject(NotificationUtils)
        unmockkObject(AmberLog)
        unmockkObject(LocalPreferences, AccountStorage)
        unmockkStatic("com.vitorpamplona.quartz.nip01Core.crypto.EventExtKt")
        unmockkStatic("com.vitorpamplona.quartz.nip01Core.tags.people.EventExtKt")
        unmockkStatic("com.vitorpamplona.quartz.nip40Expiration.EventExtKt")
    }

    private fun eventForAccount(createdAt: Long, tags: Array<Array<String>> = arrayOf(arrayOf("p", account.hexKey))): Event {
        val event = mockk<Event>(relaxed = true)
        every { event.id } returns "stale"
        every { event.verify() } returns true
        every { event.kind } returns NostrConnectEvent.KIND
        every { event.createdAt } returns createdAt
        every { event.tags } returns tags
        every { event.taggedUsers() } returns listOf(PTag(account.hexKey))
        coEvery { AccountStorage.loadSync(any(), any()) } returns account
        coEvery { AccountStorage.load(any(), any()) } returns account
        return event
    }

    private fun assertRejectedBeforeProcessing() {
        coVerify(exactly = 0) { bunkerEventDao.exists(any()) }
        coVerify(exactly = 0) { bunkerEventDao.insert(any()) }
    }

    @Test
    fun `consume rejects old events`() = runBlocking {
        consumer.consume(eventForAccount(TimeUtils.now() - TimeUtils.FIVE_MINUTES - 60), NormalizedRelayUrl("wss://relay.com"))

        assertRejectedBeforeProcessing()
    }

    @Test
    fun `consume rejects future events`() = runBlocking {
        consumer.consume(eventForAccount(TimeUtils.now() + TimeUtils.FIVE_MINUTES + 60), NormalizedRelayUrl("wss://relay.com"))

        assertRejectedBeforeProcessing()
    }

    @Test
    fun `consume rejects expired events`() = runBlocking {
        val expired = arrayOf(arrayOf("p", account.hexKey), arrayOf(ExpirationTag.TAG_NAME, (TimeUtils.now() - 10).toString()))
        consumer.consume(eventForAccount(TimeUtils.now(), expired), NormalizedRelayUrl("wss://relay.com"))

        assertRejectedBeforeProcessing()
    }

    @Test
    fun `consume rejects duplicate events persistently`() = runBlocking {
        val event = mockk<Event>(relaxed = true)
        every { event.id } returns "event1"
        every { event.verify() } returns true
        every { event.kind } returns NostrConnectEvent.KIND
        every { event.createdAt } returns TimeUtils.now()
        every { event.tags } returns arrayOf(arrayOf("p", account.hexKey))
        every { event.taggedUsers() } returns listOf(PTag(account.hexKey))

        coEvery { AccountStorage.loadSync(any(), any()) } returns account
        coEvery { AccountStorage.load(any(), any()) } returns account
        coEvery { bunkerEventDao.exists("event1") } returns true

        consumer.consume(event, NormalizedRelayUrl("wss://relay.com"))

        coVerify(exactly = 1) { bunkerEventDao.exists(any()) }
        coVerify(exactly = 0) { bunkerEventDao.insert(any()) }
    }

    @Test
    fun `a legacy request while locked is neither opened nor recorded`() = runBlocking {
        val event = mockk<Event>(relaxed = true)
        every { event.id } returns "event2"
        every { event.verify() } returns true
        every { event.kind } returns NostrConnectEvent.KIND
        every { event.createdAt } returns TimeUtils.now()
        every { event.content } returns "ciphertext"
        every { event.tags } returns arrayOf(arrayOf("p", account.hexKey))
        every { event.taggedUsers() } returns listOf(PTag(account.hexKey))

        val locked = spyk(account)
        val lockedCrypto = spyk(locked.crypto)
        every { locked.crypto } returns lockedCrypto
        every { locked.requirePrivateKeyAccess() } throws SecurityException("locked")
        coEvery { AccountStorage.loadSync(any(), any()) } returns locked
        coEvery { AccountStorage.load(any(), any()) } returns locked
        coEvery { bunkerEventDao.exists("event2") } returns false

        consumer.consume(event, NormalizedRelayUrl("wss://relay.com"))

        coVerify(exactly = 0) { bunkerEventDao.insert(any()) }
        coVerify(exactly = 0) { lockedCrypto.decrypt(any(), any()) }
    }

    private suspend fun consumeFromUnknownSender(requestJson: String, onAccount: (Account) -> Unit): List<BunkerResponse> {
        val kp = KeyPair()
        val signer = mockk<NostrSignerInternal>(relaxed = true) { every { keyPair } returns kp }
        val acc = spyk(
            Account(
                signer = signer,
                name = MutableStateFlow(""),
                picture = MutableStateFlow(""),
                signPolicy = 2,
                didBackup = false,
                scope = scope,
                keyAccessCheck = {},
            ),
        )
        val crypto = spyk(acc.crypto)
        every { acc.crypto } returns crypto
        coEvery { crypto.decrypt(any(), any()) } returns requestJson
        val sender = KeyPair().pubKey.toHexKey()
        val event = mockk<Event>(relaxed = true)
        every { event.id } returns "preauth-${requestJson.hashCode()}"
        every { event.verify() } returns true
        every { event.kind } returns NostrConnectEvent.KIND
        every { event.createdAt } returns TimeUtils.now()
        every { event.content } returns "ciphertext"
        every { event.pubKey } returns sender
        every { event.tags } returns arrayOf(arrayOf("p", acc.hexKey))
        every { event.taggedUsers() } returns listOf(PTag(acc.hexKey))
        coEvery { AccountStorage.loadSync(any(), any()) } returns acc
        coEvery { AccountStorage.load(any(), any()) } returns acc
        coEvery { bunkerEventDao.exists(any()) } returns false
        coEvery { dao.getByKey(any()) } returns null

        mockkObject(BunkerRequestUtils)
        try {
            every { amber.applicationIOScope } returns CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
            every { amber.notificationCache[any()] } returns null
            val responses = mutableListOf<BunkerResponse>()
            val attempts = mutableListOf<Int>()
            coEvery {
                bunkerResponder.sendBunkerResponse(any(), any(), any(), any(), any(), any(), any())
            } answers {
                responses += arg<BunkerResponse>(2)
                attempts += arg<Int>(6)
            }
            consumer.consume(event, NormalizedRelayUrl("wss://relay.com"))
            onAccount(acc)
            assertEquals("each reply to an unverified sender is published once", List(responses.size) { UNVERIFIED_REPLY_ATTEMPTS }, attempts)
            return responses
        } finally {
            unmockkObject(BunkerRequestUtils)
        }
    }

    @Test
    fun `an unknown sender's sign_event is refused without signing`() = runBlocking {
        val unsigned = """{"id":"","pubkey":"","created_at":1,"kind":1,"tags":[],"content":"hi","sig":""}"""
        val responses = consumeFromUnknownSender(
            """{"id":"s1","method":"sign_event","params":[${com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper.mapper.writeValueAsString(unsigned)}]}""",
        ) { acc ->
            coVerify(exactly = 0) { acc.sign<Event>(any()) }
        }
        assertEquals("no permission", responses.single().error)
    }

    @Test
    fun `an unknown sender's multi-relay auth event is refused as unknown, once`() = runBlocking {
        val unsigned = """{"id":"","pubkey":"","created_at":1,"kind":22242,"tags":[["relay","wss://a.com"],["relay","wss://b.com"]],"content":"","sig":""}"""
        val responses = consumeFromUnknownSender(
            """{"id":"a1","method":"sign_event","params":[${com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper.mapper.writeValueAsString(unsigned)}]}""",
        ) { acc ->
            coVerify(exactly = 0) { acc.sign<Event>(any()) }
        }
        assertEquals("no permission", responses.single().error)
    }

    @Test
    fun `an unknown sender's decrypt is refused without decrypting its payload`() = runBlocking {
        val peer = KeyPair().pubKey.toHexKey()
        val responses = consumeFromUnknownSender(
            """{"id":"d1","method":"nip44_decrypt","params":["$peer","payload-ciphertext"]}""",
        ) { acc ->
            val crypto = acc.crypto
            coVerify(exactly = 1) { crypto.decrypt(any(), any()) }
            coVerify(exactly = 0) { crypto.nip44Decrypt(any(), any()) }
        }
        assertEquals("no permission", responses.single().error)
    }

    @Test
    fun `malformed params are answered with an error`() = runBlocking {
        for (request in listOf(
            """{"id":"z1","method":"decrypt_zap_event","params":["only-one"]}""",
            """{"id":"s2","method":"sign_event","params":["not json"]}""",
            """{"id":"s3","method":"sign_event","params":[]}""",
            """{"id":"p1","method":"sign_psbt","params":[]}""",
            """{"id":"n1","method":"nip44_decrypt","params":[]}""",
        )) {
            val responses = consumeFromUnknownSender(request) {}
            assertEquals(request, "invalid params", responses.single().error)
        }
    }

    @Test
    fun `an expired connection is answered as an unknown app`() = runBlocking {
        val kp = KeyPair()
        val signer = mockk<NostrSignerInternal>(relaxed = true) { every { keyPair } returns kp }
        val expiredAccount = Account(
            signer = signer,
            name = MutableStateFlow(""),
            picture = MutableStateFlow(""),
            signPolicy = 2,
            didBackup = false,
            scope = scope,
            keyAccessCheck = {},
        )
        val requestJson = """{"id":"req1","method":"get_public_key","params":[]}"""
        coEvery { signer.decrypt(any(), any()) } returns requestJson
        val event = requestEvent("event3", from = expiredAccount.hexKey, to = expiredAccount.hexKey)

        coEvery { AccountStorage.loadSync(any(), any()) } returns expiredAccount
        coEvery { AccountStorage.load(any(), any()) } returns expiredAccount
        coEvery { bunkerEventDao.exists("event3") } returns false

        val anHourAgo = TimeUtils.now() - 3_600
        val expiredRow = legacyConnection(expiredAccount.hexKey, expiredAccount.hexKey).copy(name = "expired-client", deleteAfter = anHourAgo)
        coEvery { dao.getByKey(expiredAccount.hexKey) } returns mockk {
            every { application } returns expiredRow
        }

        mockkObject(BunkerRequestUtils)
        try {
            every { amber.applicationIOScope } returns CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
            every { amber.notificationCache[any()] } returns null
            val responses = mutableListOf<BunkerResponse>()
            coEvery {
                bunkerResponder.sendBunkerResponse(any(), any(), any(), any(), any(), any(), any())
            } answers {
                responses += arg<BunkerResponse>(2)
            }

            consumer.consume(event, NormalizedRelayUrl("wss://relay.com"))

            coVerify(exactly = 1) { bunkerEventDao.insert(any()) }
            assertEquals("no permission", responses.single().error)
        } finally {
            unmockkObject(BunkerRequestUtils)
        }
    }

    private data class SentReply(val response: BunkerResponse, val relays: List<NormalizedRelayUrl>, val attempts: Int)

    private suspend fun consumeFromConnectedApp(requestJson: String, onAccount: (Account) -> Unit = {}): List<SentReply> {
        val acc = spyk(
            Account(
                signer = mockk<NostrSignerInternal>(relaxed = true) { every { keyPair } returns KeyPair() },
                name = MutableStateFlow(""),
                picture = MutableStateFlow(""),
                signPolicy = 2,
                didBackup = false,
                scope = scope,
                keyAccessCheck = {},
            ),
        )
        val crypto = spyk(acc.crypto)
        every { acc.crypto } returns crypto
        coEvery { crypto.decrypt(any(), any()) } returns requestJson
        val client = KeyPair().pubKey.toHexKey()
        val event = requestEvent("connected-${requestJson.hashCode()}", from = client, to = acc.hexKey)
        coEvery { AccountStorage.loadSync(any(), any()) } returns acc
        coEvery { AccountStorage.load(any(), any()) } returns acc
        coEvery { bunkerEventDao.exists(any()) } returns false
        val row = legacyConnection(client, acc.hexKey)
        coEvery { dao.getByKey(client) } returns mockk(relaxed = true) {
            every { application } returns row
        }

        mockkObject(BunkerRequestUtils)
        try {
            every { amber.applicationIOScope } returns CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
            every { amber.notificationCache[any()] } returns null
            val sent = mutableListOf<SentReply>()
            coEvery {
                bunkerResponder.sendBunkerResponse(any(), any(), any(), any(), any(), any(), any())
            } answers {
                sent += SentReply(arg(2), arg(3), arg(6))
            }
            consumer.consume(event, NormalizedRelayUrl("wss://relay.com"))
            onAccount(acc)
            return sent
        } finally {
            unmockkObject(BunkerRequestUtils)
        }
    }

    @Test
    fun `a connected app's multi-relay auth event is refused, with retries, without signing`() = runBlocking {
        val unsigned = """{"id":"","pubkey":"","created_at":1,"kind":22242,"tags":[["relay","wss://a.com"],["relay","wss://b.com"]],"content":"","sig":""}"""
        val requestJson = """{"id":"a2","method":"sign_event","params":[${com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper.mapper.writeValueAsString(unsigned)}]}"""
        val sent = consumeFromConnectedApp(requestJson) { acc ->
            coVerify(exactly = 0) { acc.sign<Event>(any()) }
        }

        assertEquals("refusing a multi-relay auth event", sent.single().response.error)
        assertEquals(BUNKER_REPLY_ATTEMPTS, sent.single().attempts)
    }

    @Test
    fun `a connected app's auth event for a relay outside the whitelist is refused without signing`() = runBlocking {
        every { amber.settings } returns AmberSettings(authWhitelist = listOf("good.example"))
        val unsigned = """{"id":"","pubkey":"","created_at":1,"kind":22242,"tags":[["relay","wss://other.example"]],"content":"","sig":""}"""
        val requestJson = """{"id":"w1","method":"sign_event","params":[${com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper.mapper.writeValueAsString(unsigned)}]}"""
        val sent = consumeFromConnectedApp(requestJson) { acc ->
            coVerify(exactly = 0) { acc.sign<Event>(any()) }
        }

        assertEquals("user rejected", sent.single().response.error)
        assertEquals(BUNKER_REPLY_ATTEMPTS, sent.single().attempts)
    }

    @Test
    fun `a repeated connect from a connection without relays is acknowledged where it arrived`() = runBlocking {
        val sent = consumeFromConnectedApp("""{"id":"c2","method":"connect","params":["signer","secret"]}""").single()

        assertEquals("ack", sent.response.result)
        assertEquals(listOf(NormalizedRelayUrl("wss://relay.com")), sent.relays)
        assertEquals(BUNKER_REPLY_ATTEMPTS, sent.attempts)
    }

    private fun requestEvent(id: String, from: String, to: String): Event {
        val event = mockk<Event>(relaxed = true)
        every { event.id } returns id
        every { event.verify() } returns true
        every { event.kind } returns NostrConnectEvent.KIND
        every { event.createdAt } returns TimeUtils.now()
        every { event.content } returns "ciphertext"
        every { event.pubKey } returns from
        every { event.tags } returns arrayOf(arrayOf("p", to))
        every { event.taggedUsers() } returns listOf(PTag(to))
        return event
    }

    private fun legacyConnection(client: String, accountHex: String) = ApplicationEntity(
        key = client,
        name = "client",
        relays = emptyList(),
        url = "",
        icon = "",
        description = "",
        pubKey = accountHex,
        isConnected = true,
        secret = "",
        useSecret = false,
        signPolicy = 2,
        closeApplication = true,
        deleteAfter = 0L,
        lastUsed = 1L,
        localKey = "",
    )
}
