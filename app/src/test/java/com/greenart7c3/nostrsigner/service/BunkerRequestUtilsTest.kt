package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.EncryptionType
import com.greenart7c3.nostrsigner.models.SignerType
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import io.mockk.every
import io.mockk.mockk
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
    ): AmberBunkerRequest = AmberBunkerRequest(
        request = mockBunkerRequest(method = method, id = id),
        localKey = "localKey",
        relays = emptyList(),
        currentAccount = "npub1test",
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
    fun `getTypeFromBunker returns SIGN_MESSAGE for sign_message`() {
        assertEquals(SignerType.SIGN_MESSAGE, BunkerRequestUtils.getTypeFromBunker(mockBunkerRequest("sign_message")))
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
    fun `getTypeFromBunker returns INVALID for unknown method`() {
        assertEquals(SignerType.INVALID, BunkerRequestUtils.getTypeFromBunker(mockBunkerRequest("unknown_method")))
    }

    @Test
    fun `getTypeFromBunker returns INVALID for empty method`() {
        assertEquals(SignerType.INVALID, BunkerRequestUtils.getTypeFromBunker(mockBunkerRequest("")))
    }

    // ===== getDataFromBunker =====

    @Test
    fun `getDataFromBunker returns first param for sign_message`() {
        val request = mockBunkerRequest("sign_message", arrayOf("hello world"))
        assertEquals("hello world", BunkerRequestUtils.getDataFromBunker(request))
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
    fun `getDataFromBunker returns second param for decrypt_zap_event`() {
        val request = mockBunkerRequest("decrypt_zap_event", arrayOf("pubkey123", "event_json_string"))
        assertEquals("event_json_string", BunkerRequestUtils.getDataFromBunker(request))
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
        BunkerRequestUtils.addRequest(createAmberBunkerRequest("id1"))
        BunkerRequestUtils.addRequest(createAmberBunkerRequest("id2"))
        BunkerRequestUtils.remove("id1")
        val requests = BunkerRequestUtils.getBunkerRequests()
        assertEquals(1, requests.size)
        assertEquals("id2", requests.first().request.id)
    }

    @Test
    fun `remove leaves state unchanged when id is not found`() {
        BunkerRequestUtils.addRequest(createAmberBunkerRequest("id1"))
        BunkerRequestUtils.remove("nonexistent")
        assertEquals(1, BunkerRequestUtils.getBunkerRequests().size)
    }

    @Test
    fun `getBunkerRequests returns empty list initially`() {
        assertTrue(BunkerRequestUtils.getBunkerRequests().isEmpty())
    }

    @Test
    fun `getBunkerRequests reflects current state after mutations`() {
        assertTrue(BunkerRequestUtils.getBunkerRequests().isEmpty())
        BunkerRequestUtils.addRequest(createAmberBunkerRequest("id1"))
        assertEquals(1, BunkerRequestUtils.getBunkerRequests().size)
        BunkerRequestUtils.remove("id1")
        assertTrue(BunkerRequestUtils.getBunkerRequests().isEmpty())
    }

    // ===== retryWithBackoff =====

    @Test
    fun `retryWithBackoff returns true when block succeeds on first attempt`() = runBlocking {
        var calls = 0
        val result = BunkerRequestUtils.retryWithBackoff(
            maxRetries = 3,
            initialDelayMs = 1L,
            maxDelayMs = 4L,
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
            initialDelayMs = 1L,
            maxDelayMs = 4L,
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
            initialDelayMs = 1L,
            maxDelayMs = 4L,
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
            initialDelayMs = 1L,
            maxDelayMs = 4L,
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
            initialDelayMs = 1L,
            maxDelayMs = 4L,
        ) { true }
        assertTrue(result)
    }

    @Test
    fun `retryWithBackoff does not exceed maxRetries attempts`() = runBlocking {
        val maxRetries = 4
        var calls = 0
        BunkerRequestUtils.retryWithBackoff(
            maxRetries = maxRetries,
            initialDelayMs = 1L,
            maxDelayMs = 4L,
        ) {
            calls++
            false
        }
        assertEquals(maxRetries, calls)
    }
}
