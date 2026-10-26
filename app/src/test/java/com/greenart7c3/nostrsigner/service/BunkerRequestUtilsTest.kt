package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.common.EncryptionType
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.ui.RememberType
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BunkerRequestUtilsTest {

    @Before
    fun setUp() {
        BunkerRequestUtils.clearRequests()
    }

    @After
    fun tearDown() {
        BunkerRequestUtils.clearRequests()
    }

    private fun mockBunkerRequest(
        method: String,
        params: Array<String> = emptyArray(),
        id: String = "test-id",
    ): BunkerRequest = mockk(relaxed = true) {
        every { this@mockk.method } returns method
        every { this@mockk.params } returns params
        every { this@mockk.id } returns id
    }

    private fun createAmberBunkerRequest(
        id: String,
        method: String = "get_public_key",
        localKey: String = "localKey",
        account: String = "npub1test",
    ): AmberBunkerRequest = AmberBunkerRequest(
        request = mockBunkerRequest(method = method, id = id),
        localKey = localKey,
        relays = emptyList(),
        currentAccount = account,
        nostrConnectSecret = "",
        closeApplication = false,
        name = "TestApp",
        signedEvent = null,
        encryptedData = null,
        encryptionType = EncryptionType.NIP44,
        isNostrConnectUri = false,
    )

    // ===== getTypeFromBunker =====

    @Test
    fun `getTypeFromBunker returns INVALID for sign_message`() {
        assertEquals(SignerType.INVALID, BunkerRequestUtils.getTypeFromBunker(mockBunkerRequest("sign_message")))
    }

    @Test
    fun `getTypeFromBunker returns CONNECT for connect`() {
        assertEquals(SignerType.CONNECT, BunkerRequestUtils.getTypeFromBunker(mockBunkerRequest("connect")))
    }

    @Test
    fun `getTypeFromBunker returns SIGN_EVENT for sign_event`() {
        assertEquals(SignerType.SIGN_EVENT, BunkerRequestUtils.getTypeFromBunker(mockBunkerRequest("sign_event")))
    }

    @Test
    fun `getTypeFromBunker returns GET_PUBLIC_KEY for get_public_key`() {
        assertEquals(SignerType.GET_PUBLIC_KEY, BunkerRequestUtils.getTypeFromBunker(mockBunkerRequest("get_public_key")))
    }

    @Test
    fun `getTypeFromBunker returns NIP04_ENCRYPT for nip04_encrypt`() {
        assertEquals(SignerType.NIP04_ENCRYPT, BunkerRequestUtils.getTypeFromBunker(mockBunkerRequest("nip04_encrypt")))
    }

    @Test
    fun `getTypeFromBunker returns NIP04_DECRYPT for nip04_decrypt`() {
        assertEquals(SignerType.NIP04_DECRYPT, BunkerRequestUtils.getTypeFromBunker(mockBunkerRequest("nip04_decrypt")))
    }

    @Test
    fun `getTypeFromBunker returns NIP44_ENCRYPT for nip44_encrypt`() {
        assertEquals(SignerType.NIP44_ENCRYPT, BunkerRequestUtils.getTypeFromBunker(mockBunkerRequest("nip44_encrypt")))
    }

    @Test
    fun `getTypeFromBunker returns NIP44_DECRYPT for nip44_decrypt`() {
        assertEquals(SignerType.NIP44_DECRYPT, BunkerRequestUtils.getTypeFromBunker(mockBunkerRequest("nip44_decrypt")))
    }

    @Test
    fun `getTypeFromBunker returns NIP44_V3_ENCRYPT for nip44v3_encrypt`() {
        assertEquals(SignerType.NIP44_V3_ENCRYPT, BunkerRequestUtils.getTypeFromBunker(mockBunkerRequest("nip44v3_encrypt")))
    }

    @Test
    fun `getTypeFromBunker returns NIP44_V3_DECRYPT for nip44v3_decrypt`() {
        assertEquals(SignerType.NIP44_V3_DECRYPT, BunkerRequestUtils.getTypeFromBunker(mockBunkerRequest("nip44v3_decrypt")))
    }

    @Test
    fun `getTypeFromBunker returns DECRYPT_ZAP_EVENT for decrypt_zap_event`() {
        assertEquals(SignerType.DECRYPT_ZAP_EVENT, BunkerRequestUtils.getTypeFromBunker(mockBunkerRequest("decrypt_zap_event")))
    }

    @Test
    fun `getTypeFromBunker returns PING for ping`() {
        assertEquals(SignerType.PING, BunkerRequestUtils.getTypeFromBunker(mockBunkerRequest("ping")))
    }

    @Test
    fun `getTypeFromBunker returns SWITCH_RELAYS for switch_relays`() {
        assertEquals(SignerType.SWITCH_RELAYS, BunkerRequestUtils.getTypeFromBunker(mockBunkerRequest("switch_relays")))
    }

    @Test
    fun `getTypeFromBunker returns SIGN_PSBT for sign_psbt`() {
        assertEquals(SignerType.SIGN_PSBT, BunkerRequestUtils.getTypeFromBunker(mockBunkerRequest("sign_psbt")))
    }

    @Test
    fun `getTypeFromBunker returns LOGOUT for logout`() {
        assertEquals(SignerType.LOGOUT, BunkerRequestUtils.getTypeFromBunker(mockBunkerRequest("logout")))
    }

    @Test
    fun `getTypeFromBunker returns INVALID for unknown method`() {
        assertEquals(SignerType.INVALID, BunkerRequestUtils.getTypeFromBunker(mockBunkerRequest("unknown_method")))
    }

    @Test
    fun `getTypeFromBunker returns INVALID for empty method`() {
        assertEquals(SignerType.INVALID, BunkerRequestUtils.getTypeFromBunker(mockBunkerRequest("")))
    }

    // ===== getDataFromBunker =====

    @Test
    fun `getDataFromBunker returns empty string for sign_message`() {
        val request = mockBunkerRequest("sign_message", arrayOf("hello world"))
        assertEquals("", BunkerRequestUtils.getDataFromBunker(request))
    }

    @Test
    fun `getDataFromBunker returns ack for connect`() {
        assertEquals("ack", BunkerRequestUtils.getDataFromBunker(mockBunkerRequest("connect")))
    }

    @Test
    fun `getDataFromBunker returns pong for ping`() {
        assertEquals("pong", BunkerRequestUtils.getDataFromBunker(mockBunkerRequest("ping")))
    }

    @Test
    fun `getDataFromBunker returns second param for nip04_encrypt`() {
        val request = mockBunkerRequest("nip04_encrypt", arrayOf("pubkey123", "ciphertext456"))
        assertEquals("ciphertext456", BunkerRequestUtils.getDataFromBunker(request))
    }

    @Test
    fun `getDataFromBunker returns second param for nip04_decrypt`() {
        val request = mockBunkerRequest("nip04_decrypt", arrayOf("pubkey123", "ciphertext456"))
        assertEquals("ciphertext456", BunkerRequestUtils.getDataFromBunker(request))
    }

    @Test
    fun `getDataFromBunker returns second param for nip44_encrypt`() {
        val request = mockBunkerRequest("nip44_encrypt", arrayOf("pubkey123", "plaintext789"))
        assertEquals("plaintext789", BunkerRequestUtils.getDataFromBunker(request))
    }

    @Test
    fun `getDataFromBunker returns second param for nip44_decrypt`() {
        val request = mockBunkerRequest("nip44_decrypt", arrayOf("pubkey123", "ciphertext456"))
        assertEquals("ciphertext456", BunkerRequestUtils.getDataFromBunker(request))
    }

    @Test
    fun `getDataFromBunker returns fourth param for nip44v3_encrypt`() {
        val request = mockBunkerRequest("nip44v3_encrypt", arrayOf("pubkey123", "4", "dm", "cGxhaW50ZXh0Lw=="))
        assertEquals("cGxhaW50ZXh0Lw==", BunkerRequestUtils.getDataFromBunker(request))
    }

    @Test
    fun `getDataFromBunker returns fourth param for nip44v3_decrypt`() {
        val request = mockBunkerRequest("nip44v3_decrypt", arrayOf("pubkey123", "4", "dm", "A2NpcGhlcnRleHQ="))
        assertEquals("A2NpcGhlcnRleHQ=", BunkerRequestUtils.getDataFromBunker(request))
    }

    @Test
    fun `getNip44v3Kind parses second param`() {
        val request = mockBunkerRequest("nip44v3_encrypt", arrayOf("pk", "42", "scope", "data"))
        assertEquals(42, BunkerRequestUtils.getNip44v3Kind(request))
    }

    @Test
    fun `getNip44v3Kind returns null when missing or invalid`() {
        assertEquals(null, BunkerRequestUtils.getNip44v3Kind(mockBunkerRequest("nip44v3_encrypt", arrayOf("pk"))))
        assertEquals(null, BunkerRequestUtils.getNip44v3Kind(mockBunkerRequest("nip44v3_encrypt", arrayOf("pk", "not-a-number", "s", "d"))))
    }

    @Test
    fun `getNip44v3Scope returns third param or empty`() {
        assertEquals("dm", BunkerRequestUtils.getNip44v3Scope(mockBunkerRequest("nip44v3_encrypt", arrayOf("pk", "4", "dm", "data"))))
        assertEquals("", BunkerRequestUtils.getNip44v3Scope(mockBunkerRequest("nip44v3_encrypt", arrayOf("pk", "4"))))
    }

    @Test
    fun `getDataFromBunker returns second param for decrypt_zap_event`() {
        val request = mockBunkerRequest("decrypt_zap_event", arrayOf("pubkey123", "event_json_string"))
        assertEquals("event_json_string", BunkerRequestUtils.getDataFromBunker(request))
    }

    @Test
    fun `getDataFromBunker returns first param for sign_psbt`() {
        val request = mockBunkerRequest("sign_psbt", arrayOf("70736274ff01abcd"))
        assertEquals("70736274ff01abcd", BunkerRequestUtils.getDataFromBunker(request))
    }

    @Test
    fun `getDataFromBunker returns empty string when second param is missing for encrypt or decrypt`() {
        val request = mockBunkerRequest("nip04_encrypt", emptyArray())
        assertEquals("", BunkerRequestUtils.getDataFromBunker(request))
    }

    @Test
    fun `getDataFromBunker returns empty string for get_public_key`() {
        assertEquals("", BunkerRequestUtils.getDataFromBunker(mockBunkerRequest("get_public_key")))
    }

    @Test
    fun `getDataFromBunker returns empty string for switch_relays`() {
        assertEquals("", BunkerRequestUtils.getDataFromBunker(mockBunkerRequest("switch_relays")))
    }

    @Test
    fun `getDataFromBunker returns ack for logout`() {
        assertEquals("ack", BunkerRequestUtils.getDataFromBunker(mockBunkerRequest("logout")))
    }

    @Test
    fun `getDataFromBunker returns empty string for unknown method`() {
        assertEquals("", BunkerRequestUtils.getDataFromBunker(mockBunkerRequest("unknown_method")))
    }

    // ===== State management =====

    @Test
    fun `addRequest adds a new request to state`() {
        val request = createAmberBunkerRequest("id1")
        BunkerRequestUtils.addRequest(request)
        val requests = BunkerRequestUtils.getBunkerRequests()
        assertEquals(1, requests.size)
        assertEquals("id1", requests.first().request.id)
    }

    @Test
    fun `addRequest ignores duplicate requests with same id`() {
        val request = createAmberBunkerRequest("id1")
        BunkerRequestUtils.addRequest(request)
        BunkerRequestUtils.addRequest(request)
        assertEquals(1, BunkerRequestUtils.getBunkerRequests().size)
    }

    @Test
    fun `addRequest is safe under concurrent duplicate ids`() = runBlocking {
        // The same NIP-46 request id can be delivered concurrently by multiple
        // relays. A TOCTOU race in addRequest used to let duplicates through,
        // producing duplicate LazyColumn keys and crashing the UI.
        val request = createAmberBunkerRequest("17856")
        (1..200).map {
            launch(Dispatchers.Default) { BunkerRequestUtils.addRequest(request) }
        }.joinAll()
        assertEquals(1, BunkerRequestUtils.getBunkerRequests().size)
    }

    @Test
    fun `addRequest allows multiple requests with different ids`() {
        BunkerRequestUtils.addRequest(createAmberBunkerRequest("id1"))
        BunkerRequestUtils.addRequest(createAmberBunkerRequest("id2"))
        BunkerRequestUtils.addRequest(createAmberBunkerRequest("id3"))
        assertEquals(3, BunkerRequestUtils.getBunkerRequests().size)
    }

    @Test
    fun `clearRequests removes all requests from state`() {
        BunkerRequestUtils.addRequest(createAmberBunkerRequest("id1"))
        BunkerRequestUtils.addRequest(createAmberBunkerRequest("id2"))
        BunkerRequestUtils.clearRequests()
        assertTrue(BunkerRequestUtils.getBunkerRequests().isEmpty())
    }

    @Test
    fun `clearRequests on empty state does not throw`() {
        BunkerRequestUtils.clearRequests()
        assertTrue(BunkerRequestUtils.getBunkerRequests().isEmpty())
    }

    @Test
    fun `remove deletes the request with the given id`() {
        val first = createAmberBunkerRequest("id1")
        BunkerRequestUtils.addRequest(first)
        BunkerRequestUtils.addRequest(createAmberBunkerRequest("id2"))
        BunkerRequestUtils.remove(first)
        val requests = BunkerRequestUtils.getBunkerRequests()
        assertEquals(1, requests.size)
        assertEquals("id2", requests.first().request.id)
    }

    @Test
    fun `remove leaves state unchanged when id is not found`() {
        BunkerRequestUtils.addRequest(createAmberBunkerRequest("id1"))
        BunkerRequestUtils.remove(createAmberBunkerRequest("nonexistent"))
        assertEquals(1, BunkerRequestUtils.getBunkerRequests().size)
    }

    // Clients choose their own JSON-RPC ids (often sequential), so the id is
    // only unique per (account, client key).
    @Test
    fun `requests from different clients with the same id are all queued`() {
        BunkerRequestUtils.addRequest(createAmberBunkerRequest("1", localKey = "clientA"))
        BunkerRequestUtils.addRequest(createAmberBunkerRequest("1", localKey = "clientB"))
        BunkerRequestUtils.addRequest(createAmberBunkerRequest("1", localKey = "clientA", account = "npub1other"))
        assertEquals(3, BunkerRequestUtils.getBunkerRequests().size)
    }

    @Test
    fun `answering one client's request keeps another client's request with the same id`() {
        val fromA = createAmberBunkerRequest("1", localKey = "clientA")
        val fromB = createAmberBunkerRequest("1", localKey = "clientB")
        BunkerRequestUtils.addRequest(fromA)
        BunkerRequestUtils.addRequest(fromB)

        BunkerRequestUtils.remove(fromA)

        assertEquals(listOf("clientB"), BunkerRequestUtils.getBunkerRequests().map { it.localKey })
    }

    @Test
    fun `getBunkerRequests returns empty list initially`() {
        assertTrue(BunkerRequestUtils.getBunkerRequests().isEmpty())
    }

    @Test
    fun `getBunkerRequests reflects current state after mutations`() {
        assertTrue(BunkerRequestUtils.getBunkerRequests().isEmpty())
        val request = createAmberBunkerRequest("id1")
        BunkerRequestUtils.addRequest(request)
        assertEquals(1, BunkerRequestUtils.getBunkerRequests().size)
        BunkerRequestUtils.remove(request)
        assertTrue(BunkerRequestUtils.getBunkerRequests().isEmpty())
    }

    // ===== retryWithBackoff =====

    @Test
    fun `retryWithBackoff returns true when block succeeds on first attempt`() = runBlocking {
        var calls = 0
        val result = BunkerRequestUtils.retryWithBackoff(
            maxRetries = 3,
            initialDelay = 1.milliseconds,
            maxDelay = 4.milliseconds,
        ) {
            calls++
            true
        }
        assertTrue(result)
        assertEquals(1, calls)
    }

    @Test
    fun `retryWithBackoff retries and returns true on eventual success`() = runBlocking {
        var calls = 0
        val result = BunkerRequestUtils.retryWithBackoff(
            maxRetries = 5,
            initialDelay = 1.milliseconds,
            maxDelay = 4.milliseconds,
        ) {
            calls++
            calls >= 3
        }
        assertTrue(result)
        assertEquals(3, calls)
    }

    @Test
    fun `retryWithBackoff returns false when all retries fail`() = runBlocking {
        var calls = 0
        val result = BunkerRequestUtils.retryWithBackoff(
            maxRetries = 3,
            initialDelay = 1.milliseconds,
            maxDelay = 4.milliseconds,
        ) {
            calls++
            false
        }
        assertFalse(result)
        assertEquals(3, calls)
    }

    @Test
    fun `retryWithBackoff with maxRetries 1 calls block exactly once on failure`() = runBlocking {
        var calls = 0
        val result = BunkerRequestUtils.retryWithBackoff(
            maxRetries = 1,
            initialDelay = 1.milliseconds,
            maxDelay = 4.milliseconds,
        ) {
            calls++
            false
        }
        assertFalse(result)
        assertEquals(1, calls)
    }

    @Test
    fun `retryWithBackoff with maxRetries 1 returns true on success`() = runBlocking {
        val result = BunkerRequestUtils.retryWithBackoff(
            maxRetries = 1,
            initialDelay = 1.milliseconds,
            maxDelay = 4.milliseconds,
        ) { true }
        assertTrue(result)
    }

    @Test
    fun `retryWithBackoff does not exceed maxRetries attempts`() = runBlocking {
        val maxRetries = 4
        var calls = 0
        BunkerRequestUtils.retryWithBackoff(
            maxRetries = maxRetries,
            initialDelay = 1.milliseconds,
            maxDelay = 4.milliseconds,
        ) {
            calls++
            false
        }
        assertEquals(maxRetries, calls)
    }

    // ===== responseRelays =====

    private val uriRelay = NormalizedRelayUrl("wss://relay.nip46.com/")
    private val savedRelay = NormalizedRelayUrl("wss://saved.relay/")
    private val defaultRelay = NormalizedRelayUrl("wss://default.relay/")

    private fun nostrConnectRequest(relays: List<NormalizedRelayUrl>) = createAmberBunkerRequest(id = "connect-id", method = "connect")
        .copy(relays = relays, isNostrConnectUri = true)

    @Test
    fun `responseRelays uses the nostrconnect URI relays for a new connection`() {
        val relays = BunkerRequestUtils.responseRelays(nostrConnectRequest(listOf(uriRelay)), null, listOf(defaultRelay))
        assertEquals(listOf(uriRelay), relays)
    }

    @Test
    fun `responseRelays prefers the nostrconnect URI relays over a saved app with no relays`() {
        val relays = BunkerRequestUtils.responseRelays(nostrConnectRequest(listOf(uriRelay)), emptyList(), listOf(defaultRelay))
        assertEquals(listOf(uriRelay), relays)
    }

    @Test
    fun `responseRelays merges the nostrconnect URI relays with saved relays`() {
        val relays = BunkerRequestUtils.responseRelays(nostrConnectRequest(listOf(uriRelay)), listOf(savedRelay, uriRelay), listOf(defaultRelay))
        assertEquals(listOf(uriRelay, savedRelay), relays)
    }

    @Test
    fun `responseRelays falls back to defaults for a nostrconnect URI without relays`() {
        val relays = BunkerRequestUtils.responseRelays(nostrConnectRequest(emptyList()), null, listOf(defaultRelay))
        assertEquals(listOf(defaultRelay), relays)
    }

    @Test
    fun `responseRelays ignores the delivering relay of a relay-delivered request`() {
        val request = createAmberBunkerRequest(id = "sign-id", method = "sign_event").copy(relays = listOf(uriRelay))
        assertEquals(listOf(defaultRelay), BunkerRequestUtils.responseRelays(request, null, listOf(defaultRelay)))
        assertEquals(listOf(defaultRelay), BunkerRequestUtils.responseRelays(request, emptyList(), listOf(defaultRelay)))
        assertEquals(listOf(savedRelay), BunkerRequestUtils.responseRelays(request, listOf(savedRelay), listOf(defaultRelay)))
    }

    // ===== finishRequest =====

    @Test
    fun `finishRequest keeps other pending requests in the queue`() {
        val connect = createAmberBunkerRequest(id = "connect-id", method = "connect")
        val sign = createAmberBunkerRequest(id = "sign-id", method = "sign_event")
        BunkerRequestUtils.addRequest(sign)
        BunkerRequestUtils.addRequest(connect)

        BunkerRequestUtils.finishRequest(connect, closeApplication = true)

        assertEquals(listOf("sign-id"), BunkerRequestUtils.getBunkerRequests().map { it.request.id })
    }

    // ===== stale queued requests for a deleted app (F-14) =====

    private fun amberWithDao(getByKeyReturnsNull: Boolean): Pair<Amber, ApplicationDao> {
        val dao = mockk<ApplicationDao>(relaxed = true)
        if (getByKeyReturnsNull) {
            coEvery { dao.getByKey(any()) } returns null
        }
        // finishRequest() cancels notifications through the application context.
        val nm = mockk<android.app.NotificationManager>(relaxed = true)
        val appContext = mockk<android.content.Context>(relaxed = true)
        every { appContext.getSystemService(android.content.Context.NOTIFICATION_SERVICE) } returns nm
        every { appContext.getSystemService(android.app.NotificationManager::class.java) } returns nm
        val amber = mockk<Amber>(relaxed = true)
        // finishRequest() wraps Amber.instance itself in the consumer.
        every { amber.getSystemService(android.content.Context.NOTIFICATION_SERVICE) } returns nm
        every { amber.getSystemService(android.app.NotificationManager::class.java) } returns nm
        every { amber.dao(any()) } returns dao
        every { amber.applicationContext } returns appContext
        every { amber.applicationIOScope } returns CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        return amber to dao
    }

    @Test
    fun `rejecting a queued request for a deleted app does not recreate it`() = runBlocking {
        val (amber, dao) = amberWithDao(getByKeyReturnsNull = true)
        installAmberInstance(amber)

        mockkObject(AmberUtils)
        try {
            val errors = mutableListOf<AmberBunkerRequest>()
            coEvery {
                AmberUtils.sendBunkerError(any(), any(), any(), any(), any(), any())
            } answers {
                errors += arg<AmberBunkerRequest>(1)
            }

            BunkerRequestUtils.sendRejection(
                key = "localKey",
                account = newTestAccount(CoroutineScope(SupervisorJob() + Dispatchers.Default)),
                bunkerRequest = createAmberBunkerRequest(id = "stale-reject"),
                appName = "TestApp",
                rememberType = RememberType.ALWAYS,
                signerType = SignerType.GET_PUBLIC_KEY,
                kind = null,
                onLoading = {},
            )

            // sendRejection launches on Dispatchers.IO: wait for it to finish.
            coVerify(timeout = 5_000) {
                AmberUtils.sendBunkerError(any(), any(), any(), any(), any(), any())
            }
            coVerify(exactly = 0) { dao.insertApplicationWithPermissions(any()) }
            coVerify(exactly = 0) { dao.insertPermissions(any()) }
            coVerify(exactly = 0) { dao.insertPermissions2(any()) }
            coVerify(exactly = 0) {
                AmberUtils.acceptOrRejectPermission(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
            assertEquals(1, errors.size)
        } finally {
            unmockkObject(AmberUtils)
        }
    }

    @Test
    fun `approving a queued request for a deleted app sends an error and inserts nothing`() = runBlocking {
        val (amber, dao) = amberWithDao(getByKeyReturnsNull = true)
        installAmberInstance(amber)

        mockkObject(AmberUtils)
        try {
            val errors = mutableListOf<AmberBunkerRequest>()
            coEvery {
                AmberUtils.sendBunkerError(any(), any(), any(), any(), any(), any())
            } answers {
                errors += arg<AmberBunkerRequest>(1)
            }

            BunkerRequestUtils.sendResult(
                context = mockk(relaxed = true),
                account = newTestAccount(CoroutineScope(SupervisorJob() + Dispatchers.Default)),
                key = "localKey",
                response = "sig",
                bunkerRequest = createAmberBunkerRequest(id = "stale-approve"),
                kind = null,
                onLoading = {},
                rememberType = RememberType.ALWAYS,
            )

            // sendResult launches on Dispatchers.IO: wait for it to finish.
            coVerify(timeout = 5_000) {
                AmberUtils.sendBunkerError(any(), any(), any(), any(), any(), any())
            }
            coVerify(exactly = 0) { dao.insertApplicationWithPermissions(any()) }
            coVerify(exactly = 0) { dao.insertPermissions(any()) }
            assertEquals(1, errors.size)
        } finally {
            unmockkObject(AmberUtils)
        }
    }

    // The reply came after the bookkeeping, so a failure there (seen with a
    // malformed sign_event in getDataFromBunker) left the client unanswered.
    @Test
    fun `a rejection is answered even when recording it fails`() = runBlocking {
        val (amber, dao) = amberWithDao(getByKeyReturnsNull = false)
        coEvery { dao.getByKey(any()) } returns mockk<com.greenart7c3.nostrsigner.database.ApplicationWithPermissions>(relaxed = true)
        installAmberInstance(amber)

        mockkObject(AmberUtils, com.greenart7c3.nostrsigner.AmberLog)
        try {
            every { com.greenart7c3.nostrsigner.AmberLog.e(any(), any(), any()) } returns Unit
            coEvery { AmberUtils.sendBunkerError(any(), any(), any(), any(), any(), any()) } returns Unit
            coEvery {
                AmberUtils.acceptOrRejectPermission(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } throws IllegalStateException("boom")

            BunkerRequestUtils.sendRejection(
                key = "localKey",
                account = newTestAccount(CoroutineScope(SupervisorJob() + Dispatchers.Default)),
                bunkerRequest = createAmberBunkerRequest(id = "failing-reject"),
                appName = "TestApp",
                rememberType = RememberType.ALWAYS,
                signerType = SignerType.GET_PUBLIC_KEY,
                kind = null,
                onLoading = {},
            )

            coVerify(timeout = 5_000) { AmberUtils.sendBunkerError(any(), any(), any(), any(), any(), any()) }
        } finally {
            unmockkObject(AmberUtils, com.greenart7c3.nostrsigner.AmberLog)
        }
    }

    @Test
    fun `getDataFromBunker tolerates malformed sign_event and sign_psbt params`() {
        assertEquals("", BunkerRequestUtils.getDataFromBunker(mockBunkerRequest("sign_event", arrayOf("not json"))))
        assertEquals("", BunkerRequestUtils.getDataFromBunker(mockBunkerRequest("sign_event")))
        assertEquals("", BunkerRequestUtils.getDataFromBunker(mockBunkerRequest("sign_psbt")))
    }

    @Test
    fun `a queued request for a live app is still answered normally`() = runBlocking {
        val (amber, dao) = amberWithDao(getByKeyReturnsNull = false)
        val liveApp = mockk<com.greenart7c3.nostrsigner.database.ApplicationWithPermissions>(relaxed = true)
        coEvery { dao.getByKey(any()) } returns liveApp
        installAmberInstance(amber)

        mockkObject(AmberUtils)
        try {
            coEvery {
                AmberUtils.sendBunkerError(any(), any(), any(), any(), any(), any())
            } returns Unit

            BunkerRequestUtils.sendRejection(
                key = "localKey",
                account = newTestAccount(CoroutineScope(SupervisorJob() + Dispatchers.Default)),
                bunkerRequest = createAmberBunkerRequest(id = "live-reject"),
                appName = "TestApp",
                rememberType = RememberType.NEVER,
                signerType = SignerType.GET_PUBLIC_KEY,
                kind = null,
                onLoading = {},
            )

            coVerify(timeout = 5_000, exactly = 1) { dao.insertApplicationWithPermissions(any()) }
            // The client still gets its rejection reply from the tail of sendRejection.
            coVerify(timeout = 5_000, exactly = 1) { AmberUtils.sendBunkerError(any(), any(), any(), any(), any(), any()) }
        } finally {
            unmockkObject(AmberUtils)
        }
    }

    @Test
    fun `removeAllForClient drops only that client's queued requests`() {
        val a = createAmberBunkerRequest(id = "a").copy(localKey = "clientA")
        val b = createAmberBunkerRequest(id = "b").copy(localKey = "clientB")
        BunkerRequestUtils.addRequest(a)
        BunkerRequestUtils.addRequest(b)

        BunkerRequestUtils.removeAllForClient("clientA")

        assertEquals(listOf("b"), BunkerRequestUtils.getBunkerRequests().map { it.request.id })
    }
}
