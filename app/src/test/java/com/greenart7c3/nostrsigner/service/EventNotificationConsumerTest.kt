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
    // A real responder over the mocked Application, spied so tests can stub its replies.
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
        every { amber.accountDatabases.dao(any()) } returns dao

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
                // Read on use: tests swap the stubbed scope after setUp.
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

    /** A verified bunker event addressed to [account], so it reaches the freshness checks. */
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

    // A rejected event is never looked up in, or written to, the persistent cache.
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
        every { locked.requirePrivateKeyAccess() } throws SecurityException("locked")
        coEvery { AccountStorage.loadSync(any(), any()) } returns locked
        coEvery { AccountStorage.load(any(), any()) } returns locked
        coEvery { bunkerEventDao.exists("event2") } returns false

        consumer.consume(event, NormalizedRelayUrl("wss://relay.com"))

        // Unrecorded, so a re-delivery after unlock is processed.
        coVerify(exactly = 0) { bunkerEventDao.insert(any()) }
        coVerify(exactly = 0) { locked.decrypt(any(), any()) }
    }

    /**
     * Consumes [requestJson] from a sender with no stored connection and returns
     * the replies; the account is a spy so key use can be verified. Each reply to
     * this unverified sender must be published once ([UNVERIFIED_REPLY_ATTEMPTS]).
     */
    private suspend fun consumeFromUnknownSender(requestJson: String, onAccount: (Account) -> Unit): List<BunkerResponse> {
        val signer = mockk<NostrSignerInternal>(relaxed = true)
        val kp = KeyPair()
        val acc = spyk(
            Account(
                signer = signer,
                hexKey = kp.pubKey.toHexKey(),
                npub = "npub-preauth-test",
                name = MutableStateFlow(""),
                picture = MutableStateFlow(""),
                signPolicy = 2,
                didBackup = false,
                scope = scope,
                keyAccessCheck = {},
            ),
        )
        coEvery { acc.decrypt(any(), any()) } returns requestJson
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
            assertEquals(List(responses.size) { UNVERIFIED_REPLY_ATTEMPTS }, attempts)
            return responses
        } finally {
            unmockkObject(BunkerRequestUtils)
        }
    }

    // An unknown sender's request used to be signed (sign_event) or decrypted,
    // including the nested seal, before the permission lookup refused it.
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

    // The multi-relay check used to run before admission and answer any sender
    // with the retried refusal; now an unknown one gets the single "no permission".
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
            // Only the request itself was opened; the payload never was.
            coVerify(exactly = 1) { acc.decrypt(any(), any()) }
            coVerify(exactly = 0) { acc.nip44Decrypt(any(), any()) }
        }
        assertEquals("no permission", responses.single().error)
    }

    // Missing params or an unparsable event used to throw inside the
    // coroutine, leaving the client waiting for an answer that never came.
    @Test
    fun `malformed params are answered with an error`() = runBlocking {
        for (request in listOf(
            """{"id":"z1","method":"decrypt_zap_event","params":["only-one"]}""",
            """{"id":"s2","method":"sign_event","params":["not json"]}""",
            """{"id":"s3","method":"sign_event","params":[]}""",
            // The shared check (hasRequiredParams) also refuses these up front.
            """{"id":"p1","method":"sign_psbt","params":[]}""",
            """{"id":"n1","method":"nip44_decrypt","params":[]}""",
        )) {
            val responses = consumeFromUnknownSender(request) {}
            assertEquals(request, "invalid params", responses.single().error)
        }
    }

    @Test
    fun `an expired connection is answered as an unknown app`() = runBlocking {
        val signer = mockk<NostrSignerInternal>(relaxed = true)
        val kp = KeyPair()
        val expiredAccount = Account(
            signer = signer,
            hexKey = kp.pubKey.toHexKey(),
            npub = "npub-expired-test",
            name = MutableStateFlow(""),
            picture = MutableStateFlow(""),
            signPolicy = 2,
            didBackup = false,
            scope = scope,
            keyAccessCheck = {},
        )
        val requestJson = """{"id":"req1","method":"get_public_key","params":[]}"""
        coEvery { signer.decrypt(any(), any()) } answers {
            println("DECRYPT-ENTERED")
            requestJson
        }
        val event = mockk<Event>(relaxed = true)
        every { event.id } returns "event3"
        every { event.verify() } returns true
        every { event.kind } returns NostrConnectEvent.KIND
        every { event.createdAt } returns TimeUtils.now()
        every { event.content } returns "ciphertext"
        every { event.pubKey } returns expiredAccount.hexKey
        every { event.tags } returns arrayOf(arrayOf("p", expiredAccount.hexKey))
        every { event.taggedUsers() } returns listOf(PTag(expiredAccount.hexKey))

        coEvery { AccountStorage.loadSync(any(), any()) } returns expiredAccount
        coEvery { AccountStorage.load(any(), any()) } returns expiredAccount
        coEvery { bunkerEventDao.exists("event3") } returns false

        val expiredRow = ApplicationEntity(
            key = expiredAccount.hexKey,
            name = "expired-client",
            relays = emptyList(),
            url = "",
            icon = "",
            description = "",
            pubKey = expiredAccount.hexKey,
            isConnected = true,
            secret = "",
            useSecret = false,
            signPolicy = 2,
            closeApplication = true,
            deleteAfter = TimeUtils.now() - 3_600,
            lastUsed = 1L,
            localKey = "",
        )
        coEvery { dao.getByKey(expiredAccount.hexKey) } returns mockk {
            every { application } returns expiredRow
        }

        mockkObject(BunkerRequestUtils)
        try {
            // notify() decrypts inside applicationIOScope.launch; run that inline.
            every { amber.applicationIOScope } returns CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
            // The relaxed mock returns 0L (non-null) from the dedup cache, which
            // makes notify() treat the request as already processed.
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

    // A reply the consumer sent: the response, the relays it went to and its attempts.
    private data class SentReply(val response: BunkerResponse, val relays: List<NormalizedRelayUrl>, val attempts: Int)

    /**
     * Consumes [requestJson] from a client with a stored connection without relays (one
     * from before per-connection keys) and returns the replies; [onAccount] can verify
     * how the account key was used.
     */
    private suspend fun consumeFromConnectedApp(requestJson: String, onAccount: (Account) -> Unit = {}): List<SentReply> {
        val acc = spyk(
            Account(
                signer = mockk<NostrSignerInternal>(relaxed = true),
                hexKey = KeyPair().pubKey.toHexKey(),
                npub = "npub-connected-test",
                name = MutableStateFlow(""),
                picture = MutableStateFlow(""),
                signPolicy = 2,
                didBackup = false,
                scope = scope,
                keyAccessCheck = {},
            ),
        )
        coEvery { acc.decrypt(any(), any()) } returns requestJson
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

    // Refused by the common nip46Step, where SignerProviderQuery used to refuse it.
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

    // The ack used to go to the connection's saved relays even when there were none, so
    // nothing was sent; it now goes where the connect arrived, as on the desktop.
    @Test
    fun `a repeated connect from a connection without relays is acknowledged where it arrived`() = runBlocking {
        val sent = consumeFromConnectedApp("""{"id":"c2","method":"connect","params":["signer","secret"]}""").single()

        assertEquals("ack", sent.response.result)
        assertEquals(listOf(NormalizedRelayUrl("wss://relay.com")), sent.relays)
        assertEquals(BUNKER_REPLY_ATTEMPTS, sent.attempts)
    }

    // A verified, fresh bunker event [from] a client, tagged [to] a key.
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

    // A connection from before per-connection keys: its requests reach the account key.
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
