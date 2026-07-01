package com.greenart7c3.nostrsigner.shared

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

private class FakePermissionStore(private val decisions: MutableMap<String, Boolean> = mutableMapOf()) : BunkerPermissionStore {
    override suspend fun isApproved(appPubKey: String, method: BunkerMethod, kind: Int?): Boolean? = decisions["$appPubKey:$method:$kind"]

    override suspend fun remember(appPubKey: String, method: BunkerMethod, kind: Int?, approved: Boolean) {
        decisions["$appPubKey:$method:$kind"] = approved
    }
}

private class FakeHistoryLogger : BunkerHistoryLogger {
    val entries = mutableListOf<BunkerHistoryEntry>()

    override suspend fun log(entry: BunkerHistoryEntry) {
        entries.add(entry)
    }
}

class BunkerSigningEngineTest {
    private suspend fun sendRequest(client: BunkerSigner, account: BunkerSigner, request: BunkerRequest): String {
        val plainText = JacksonMapper.mapper.writeValueAsString(request)
        return client.nip44Encrypt(plainText, account.pubKey)
    }

    private suspend fun decodeResponse(client: BunkerSigner, account: BunkerSigner, event: com.vitorpamplona.quartz.nip01Core.core.Event): BunkerResponse {
        val plainText = client.nip44Decrypt(event.content, account.pubKey)
        return JacksonMapper.mapper.readValue(plainText, BunkerResponse::class.java)
    }

    @Test
    fun autoApprovedGetPublicKeyReturnsAccountPubKey() = runTest {
        val account = BunkerSigner(KeyPair())
        val client = BunkerSigner(KeyPair())
        val permissionStore = FakePermissionStore(mutableMapOf("${client.pubKey}:GET_PUBLIC_KEY:null" to true))
        val engine = BunkerSigningEngine(
            account = account,
            permissionStore = permissionStore,
            approvalPort = BunkerApprovalPort { error("should not prompt when a rule already exists") },
        )

        val request = BunkerRequest("req-1", "get_public_key", arrayOf())
        val encrypted = sendRequest(client, account, request)

        val responseEvent = engine.handleIncomingEvent(client.pubKey, encrypted)
        assertTrue(responseEvent != null)

        val response = decodeResponse(client, account, responseEvent)
        assertEquals("req-1", response.id)
        assertEquals(account.pubKey, response.result)
        assertNull(response.error)
    }

    @Test
    fun rejectedRequestReturnsUserRejectedError() = runTest {
        val account = BunkerSigner(KeyPair())
        val client = BunkerSigner(KeyPair())
        val engine = BunkerSigningEngine(
            account = account,
            permissionStore = FakePermissionStore(),
            approvalPort = BunkerApprovalPort { BunkerApprovalDecision(approved = false, remember = false) },
        )

        val request = BunkerRequest("req-2", "get_public_key", arrayOf())
        val encrypted = sendRequest(client, account, request)

        val responseEvent = engine.handleIncomingEvent(client.pubKey, encrypted)!!
        val response = decodeResponse(client, account, responseEvent)

        assertEquals("user rejected", response.error)
        assertTrue(response.result.isNullOrEmpty())
    }

    @Test
    fun signEventProducesEventSignedByAccount() = runTest {
        val account = BunkerSigner(KeyPair())
        val client = BunkerSigner(KeyPair())
        val engine = BunkerSigningEngine(
            account = account,
            permissionStore = FakePermissionStore(mutableMapOf("${client.pubKey}:SIGN_EVENT:1" to true)),
            approvalPort = BunkerApprovalPort { error("should not prompt when a rule already exists") },
        )

        val unsignedEventJson = """{"kind":1,"created_at":1700000000,"tags":[],"content":"hello from bunker desktop"}"""
        val request = BunkerRequest("req-3", "sign_event", arrayOf(unsignedEventJson))
        val encrypted = sendRequest(client, account, request)

        val responseEvent = engine.handleIncomingEvent(client.pubKey, encrypted)!!
        val response = decodeResponse(client, account, responseEvent)

        assertNull(response.error)
        val signedEvent = JacksonMapper.mapper.readValue(response.result, com.vitorpamplona.quartz.nip01Core.core.Event::class.java)
        assertEquals(account.pubKey, signedEvent.pubKey)
        assertEquals("hello from bunker desktop", signedEvent.content)
        assertTrue(signedEvent.sig.isNotEmpty())
        assertTrue(signedEvent.id.isNotEmpty())
    }

    @Test
    fun connectRequestMetadataNameFlowsIntoHistoryAndApprovalPrompt() = runTest {
        val account = BunkerSigner(KeyPair())
        val client = BunkerSigner(KeyPair())
        val historyLogger = FakeHistoryLogger()
        var promptedAppName: String? = "not called"
        val engine = BunkerSigningEngine(
            account = account,
            permissionStore = FakePermissionStore(),
            approvalPort = BunkerApprovalPort {
                promptedAppName = it.appName
                BunkerApprovalDecision(approved = true, remember = false)
            },
            historyLogger = historyLogger,
        )

        val request = BunkerRequest("req-4", "connect", arrayOf(account.pubKey, "", "", """{"name":"My App"}"""))
        val encrypted = sendRequest(client, account, request)

        engine.handleIncomingEvent(client.pubKey, encrypted)

        assertEquals("My App", promptedAppName)
        assertEquals("My App", historyLogger.entries.single().appName)
    }

    @Test
    fun appNameLookupIsUsedAsFallbackForNonConnectRequests() = runTest {
        val account = BunkerSigner(KeyPair())
        val client = BunkerSigner(KeyPair())
        val historyLogger = FakeHistoryLogger()
        val engine = BunkerSigningEngine(
            account = account,
            permissionStore = FakePermissionStore(mutableMapOf("${client.pubKey}:PING:null" to true)),
            approvalPort = BunkerApprovalPort { error("should not prompt when a rule already exists") },
            historyLogger = historyLogger,
            appNameLookup = { pubKey -> if (pubKey == client.pubKey) "Known App" else null },
        )

        val request = BunkerRequest("req-5", "ping", arrayOf())
        val encrypted = sendRequest(client, account, request)

        engine.handleIncomingEvent(client.pubKey, encrypted)

        assertEquals("Known App", historyLogger.entries.single().appName)
    }
}
