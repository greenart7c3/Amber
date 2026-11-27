package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.ReturnType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentPayloadDeliveryTest {
    private fun sampleIntentData(
        type: SignerType,
        acceptResultUri: Boolean = false,
        explicitSignatureOnly: Boolean = false,
    ) = IntentData(
        data = "data",
        name = "App",
        type = type,
        pubKey = "",
        id = "req-1",
        callBackUrl = null,
        compression = CompressionType.NONE,
        returnType = ReturnType.SIGNATURE,
        permissions = null,
        currentAccount = "",
        route = null,
        event = null,
        encryptedData = null,
        acceptResultUri = acceptResultUri,
        explicitSignatureOnly = explicitSignatureOnly,
    )

    @Test
    fun `buildNativeResultExtras keeps event by default and omits only when signature requested`() {
        val legacy = buildNativeResultExtras(
            intentData = sampleIntentData(SignerType.SIGN_EVENT, explicitSignatureOnly = false),
            event = """{"id":"1"}""",
            value = "sig-hex",
        )
        assertEquals("sig-hex", legacy["signature"])
        assertEquals("sig-hex", legacy["result"])
        assertEquals("""{"id":"1"}""", legacy["event"])

        val signatureOnly = buildNativeResultExtras(
            intentData = sampleIntentData(SignerType.SIGN_EVENT, explicitSignatureOnly = true),
            event = """{"id":"1"}""",
            value = "sig-hex",
        )
        assertEquals("sig-hex", signatureOnly["signature"])
        assertEquals("sig-hex", signatureOnly["result"])
        assertFalse(signatureOnly.containsKey("event"))

        val anonZap = buildNativeResultExtras(
            intentData = sampleIntentData(SignerType.SIGN_EVENT, explicitSignatureOnly = true),
            event = """{"kind":9734}""",
            value = """{"kind":9734}""",
        )
        assertEquals("""{"kind":9734}""", anonZap["event"])
    }

    @Test
    fun `buildNativeResultExtras spills oversized payloads only when acceptResultUri is true`() {
        val bigEvent = "e".repeat(250)
        val signEventExtras = buildNativeResultExtras(
            intentData = sampleIntentData(SignerType.SIGN_EVENT, acceptResultUri = true),
            event = bigEvent,
            value = "sig-hex",
            inlineThresholdBytes = 100,
            spillResponse = { "content://pkg.PAYLOADS/ev-tok" },
        )
        assertEquals("sig-hex", signEventExtras["signature"])
        assertEquals("sig-hex", signEventExtras["result"])
        assertEquals("content://pkg.PAYLOADS/ev-tok", signEventExtras[EphemeralPayloadStore.EXTRA_EVENT_URI])
        assertFalse(signEventExtras.containsKey("event"))

        val bigCipher = "c".repeat(250)
        val encryptExtras = buildNativeResultExtras(
            intentData = sampleIntentData(SignerType.NIP44_V3_ENCRYPT, acceptResultUri = true),
            event = bigCipher,
            value = bigCipher,
            inlineThresholdBytes = 100,
            spillResponse = { "content://pkg.PAYLOADS/res-tok" },
        )
        assertEquals("content://pkg.PAYLOADS/res-tok", encryptExtras[EphemeralPayloadStore.EXTRA_RESULT_URI])
        assertFalse(encryptExtras.containsKey("result"))
        assertFalse(encryptExtras.containsKey("signature"))
        assertFalse(encryptExtras.containsKey("event"))

        val pubKeyExtras = buildNativeResultExtras(
            intentData = sampleIntentData(SignerType.GET_PUBLIC_KEY),
            event = "hexkey",
            value = "hexkey",
        )
        assertEquals(BuildConfig.APPLICATION_ID, pubKeyExtras["package"])
    }

    @Test
    fun `buildBatchResultExtras spills to results_uri when oversized and opted in`() {
        val small = buildBatchResultExtras("[]", acceptResultUri = true, inlineThresholdBytes = 100) { "unused" }
        assertEquals("[]", small["results"])

        val bigJson = "[${"a".repeat(200)}]"
        val spilled = buildBatchResultExtras(bigJson, acceptResultUri = true, inlineThresholdBytes = 100) { "content://pkg.PAYLOADS/batch" }
        assertEquals("content://pkg.PAYLOADS/batch", spilled[EphemeralPayloadStore.EXTRA_RESULTS_URI])
        assertFalse(spilled.containsKey("results"))

        val legacy = buildBatchResultExtras(bigJson, acceptResultUri = false, inlineThresholdBytes = 100) { "unused" }
        assertEquals(bigJson, legacy["results"])
    }

    @Test
    fun `NostrSignerRequestParser tracks payloadOverride, acceptResultUri, and explicitSignatureOnly`() {
        val req1 = NostrSignerRequestParser.fromExtras(
            uri = "nostrsigner:",
            packageName = "com.app",
            payloadOverride = "staged-data",
            acceptResultUri = true,
        ) { key ->
            mapOf("type" to "sign_event", "returnType" to "signature")[key]
        }
        assertEquals("staged-data", req1.data)
        assertTrue(req1.acceptResultUri)
        assertTrue(req1.explicitSignatureOnly)

        val req2 = NostrSignerRequestParser.fromExtras(
            uri = "nostrsigner:inline",
            packageName = "com.app",
        ) { key ->
            mapOf("type" to "sign_event")[key]
        }
        assertFalse(req2.acceptResultUri)
        assertFalse(req2.explicitSignatureOnly)
    }

    @Test
    fun `IntentRateLimitInspector skips event parsing when payload_uri is present`() {
        val extras = mockk<Bundle>()
        every { extras.getString("type") } returns "sign_event"
        every { extras.getString(EphemeralPayloadStore.EXTRA_PAYLOAD_URI) } returns "content://pkg.PAYLOADS/0123456789abcdef0123456789abcdef"
        val intent = mockk<Intent>()
        every { intent.extras } returns extras

        val key = IntentRateLimitInspector.inspect(intent, "com.example.app")

        assertEquals(IntentRateLimiter.BucketKey("com.example.app", "SIGN_EVENT", kind = null), key)
    }

    @Test
    fun `getIntentData consumes staged payload even when bunker requests are waiting`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = EphemeralPayloadStore(scope)
        val repo = mockk<AccountRepository>(relaxed = true)
        val account = newTestAccount(scope)
        val token = store.allocateUpload(1001, "com.example.client")
        val largeContent = "m".repeat(200_000)
        val eventJson = """{"kind":1,"created_at":1,"tags":[],"content":"$largeContent"}"""
        store.completeUpload(token, 1001, "com.example.client", eventJson.toByteArray(Charsets.UTF_8))
        val payloadUri = EphemeralPayloadStore.buildPayloadUri(BuildConfig.APPLICATION_ID, token)

        val packageManager = mockk<PackageManager>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            every { packageManager.getPackageUid("com.example.client", any<PackageManager.PackageInfoFlags>()) } returns 1001
        } else {
            @Suppress("DEPRECATION")
            every { packageManager.getPackageUid("com.example.client", 0) } returns 1001
        }
        val context = mockk<Context>(relaxed = true)
        every { context.packageManager } returns packageManager

        val dataUri = mockk<Uri>()
        every { dataUri.toString() } returns "nostrsigner:"
        val extras = mockk<Bundle>()
        every { extras.getString(any()) } returns null
        every { extras.getString("type") } returns "sign_event"
        every { extras.getString("returnType") } returns "signature"
        every { extras.getString(EphemeralPayloadStore.EXTRA_PAYLOAD_URI) } returns payloadUri

        val intent = mockk<Intent>(relaxed = true)
        every { intent.data } returns dataUri
        every { intent.dataString } returns "nostrsigner:"
        every { intent.extras } returns extras
        every { intent.getStringExtra("current_user") } returns null
        every { intent.getBooleanExtra(EphemeralPayloadStore.EXTRA_ACCEPT_RESULT_URI, false) } returns true

        mockkObject(BunkerRequestUtils, AmberLog)
        every { BunkerRequestUtils.getBunkerRequests() } returns listOf(mockk<AmberBunkerRequest>(relaxed = true))
        every { AmberLog.e(any(), any(), any()) } returns Unit
        IntentUtils.clear()
        IntentUtils.install(repo, scope, store) { null }
        try {
            val result = IntentUtils.getIntentData(context, intent, "com.example.client", null, account)
            assertNotNull(result)
            assertEquals(eventJson, result!!.data)
            assertEquals(largeContent, result.event!!.content)
            assertTrue(result.acceptResultUri)
            assertTrue(result.explicitSignatureOnly)
        } finally {
            IntentUtils.clear()
            unmockkObject(BunkerRequestUtils, AmberLog)
            scope.cancel()
        }
    }

    @Test
    fun `getIntentData rejects missing or unauthorized staged payload cleanly`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = EphemeralPayloadStore(scope)
        val repo = mockk<AccountRepository>(relaxed = true)
        val account = newTestAccount(scope)
        val token = store.allocateUpload(1001, "com.owner.app")
        store.completeUpload(token, 1001, "com.owner.app", """{"kind":1,"tags":[],"content":"hi"}""".toByteArray())
        val payloadUri = EphemeralPayloadStore.buildPayloadUri(BuildConfig.APPLICATION_ID, token)

        val packageManager = mockk<PackageManager>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            every { packageManager.getPackageUid("com.attacker.app", any<PackageManager.PackageInfoFlags>()) } returns 2002
        } else {
            @Suppress("DEPRECATION")
            every { packageManager.getPackageUid("com.attacker.app", 0) } returns 2002
        }
        val context = mockk<Context>(relaxed = true)
        every { context.packageManager } returns packageManager

        val dataUri = mockk<Uri>()
        every { dataUri.toString() } returns "nostrsigner:"
        val extras = mockk<Bundle>()
        every { extras.getString(any()) } returns null
        every { extras.getString("type") } returns "sign_event"
        every { extras.getString(EphemeralPayloadStore.EXTRA_PAYLOAD_URI) } returns payloadUri
        val intent = mockk<Intent>(relaxed = true)
        every { intent.data } returns dataUri
        every { intent.dataString } returns "nostrsigner:"
        every { intent.extras } returns extras
        every { intent.getStringExtra("current_user") } returns null

        mockkObject(AmberLog)
        every { AmberLog.e(any(), any(), any()) } returns Unit
        IntentUtils.clear()
        IntentUtils.clearInvalidIntents()
        IntentUtils.install(repo, scope, store) { null }
        try {
            val result = IntentUtils.getIntentData(context, intent, "com.attacker.app", null, account)
            assertNull(result)
            assertEquals(1, IntentUtils.invalidIntents.replayCache.size)
        } finally {
            IntentUtils.clearInvalidIntents()
            IntentUtils.clear()
            unmockkObject(AmberLog)
            scope.cancel()
        }
    }

    @Test
    fun `two parses of one staged intent consume the payload once and report nothing invalid`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = EphemeralPayloadStore(scope)
        val repo = mockk<AccountRepository>(relaxed = true)
        val account = newTestAccount(scope)
        val token = store.allocateUpload(1001, "com.example.client")
        val eventJson = """{"kind":1,"created_at":1,"tags":[],"content":"${"m".repeat(200_000)}"}"""
        store.completeUpload(token, 1001, "com.example.client", eventJson.toByteArray(Charsets.UTF_8))

        val packageManager = mockk<PackageManager>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            every { packageManager.getPackageUid("com.example.client", any<PackageManager.PackageInfoFlags>()) } returns 1001
        } else {
            @Suppress("DEPRECATION")
            every { packageManager.getPackageUid("com.example.client", 0) } returns 1001
        }
        val context = mockk<Context>(relaxed = true)
        every { context.packageManager } returns packageManager

        val dataUri = mockk<Uri>()
        every { dataUri.toString() } returns "nostrsigner:"
        val extras = mockk<Bundle>()
        every { extras.getString(any()) } returns null
        every { extras.getString("id") } returns "staged-once"
        every { extras.getString("type") } returns "sign_event"
        every { extras.getString(EphemeralPayloadStore.EXTRA_PAYLOAD_URI) } returns
            EphemeralPayloadStore.buildPayloadUri(BuildConfig.APPLICATION_ID, token)
        val intent = mockk<Intent>(relaxed = true)
        every { intent.data } returns dataUri
        every { intent.dataString } returns "nostrsigner:"
        every { intent.extras } returns extras
        every { intent.getStringExtra("current_user") } returns null

        mockkObject(AmberLog)
        every { AmberLog.e(any(), any(), any()) } returns Unit
        IntentUtils.clear()
        IntentUtils.clearInvalidIntents()
        IntentUtils.install(repo, scope, store) { null }
        try {
            val results = List(2) {
                async(Dispatchers.IO) { IntentUtils.getIntentData(context, intent, "com.example.client", null, account) }
            }.awaitAll()

            assertNotNull(results[0])
            assertTrue(results[0] === results[1])
            assertTrue(IntentUtils.invalidIntents.replayCache.isEmpty())
        } finally {
            IntentUtils.clearInvalidIntents()
            IntentUtils.clear()
            unmockkObject(AmberLog)
            scope.cancel()
        }
    }

    @Test
    fun `a later intent reusing a request id is queued once the earlier request is answered`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = mockk<AccountRepository>(relaxed = true)
        val account = newTestAccount(scope)
        val context = mockk<Context>(relaxed = true)
        val dataUri = mockk<Uri>()
        every { dataUri.toString() } returns "nostrsigner:"
        val extras = mockk<Bundle>()
        every { extras.getString(any()) } returns null
        every { extras.getString("id") } returns "reused-id"
        every { extras.getString("type") } returns "get_public_key"
        fun newIntent() = mockk<Intent>(relaxed = true).also {
            every { it.data } returns dataUri
            every { it.dataString } returns "nostrsigner:"
            every { it.extras } returns extras
            every { it.getStringExtra("current_user") } returns null
        }

        IntentUtils.clear()
        IntentUtils.install(repo, scope, EphemeralPayloadStore(scope)) { null }
        try {
            val first = IntentUtils.getIntentData(context, newIntent(), "com.example.client", null, account)
            assertNotNull(first)
            IntentUtils.addAll(listOf(first!!))
            assertNull(IntentUtils.getIntentData(context, newIntent(), "com.example.client", null, account))

            IntentUtils.removeAll(listOf(first))

            assertNotNull(IntentUtils.getIntentData(context, newIntent(), "com.example.client", null, account))
        } finally {
            IntentUtils.clear()
            scope.cancel()
        }
    }
}
