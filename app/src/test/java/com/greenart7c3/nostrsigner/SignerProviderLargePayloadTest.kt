package com.greenart7c3.nostrsigner

import android.content.Context
import android.net.Uri
import com.greenart7c3.nostrsigner.database.ApplicationDaos
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.HistoryDao
import com.greenart7c3.nostrsigner.database.HistoryDatabase
import com.greenart7c3.nostrsigner.database.PermissionLookupDao
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.service.EphemeralPayloadStore
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.TimeUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SignerProviderLargePayloadTest {
    private val context = mockk<Context>(relaxed = true)
    private lateinit var amber: Amber
    private lateinit var dao: PermissionLookupDao
    private lateinit var account: Account

    @Before
    fun setUp() {
        mockkObject(AmberLog)
        every { AmberLog.d(any(), any<String>()) } returns Unit
        every { AmberLog.d(any(), any(), any()) } returns Unit

        val historyDao = mockk<HistoryDao>(relaxed = true)
        val historyDatabase = mockk<HistoryDatabase>()
        every { historyDatabase.dao() } returns historyDao

        dao = mockk(relaxed = true)
        amber = mockk()
        every { amber.settings } returns AmberSettings()
        every { amber.applicationIOScope } returns CoroutineScope(Dispatchers.Unconfined)
        every { amber.accountDatabases.getHistoryDatabase(any()) } returns historyDatabase
        every { amber.accountDatabases.daos(any()) } returns ApplicationDaos(mockk(), mockk(), mockk(), dao, mockk(), mockk())

        account = mockk()
        every { account.npub } returns NPUB
        every { account.hexKey } returns HEX

        mockkObject(LocalPreferences, AccountStorage, SavedAccounts, AccountPreferences)
        every { AccountStorage.loadSync(any(), any()) } returns account
        every { SavedAccounts.all(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        unmockkObject(LocalPreferences, AccountStorage, SavedAccounts, AccountPreferences)
        unmockkObject(AmberLog)
    }

    @Test
    fun `parseSignerQueryOptions extracts query flags and base uri`() {
        val opts = parseSignerQueryOptions(
            "content://com.greenart7c3.nostrsigner.SIGN_EVENT?payload_in_uri=1&accept_result_uri=true&return_type=signature",
        )

        assertEquals("content://com.greenart7c3.nostrsigner.SIGN_EVENT", opts.baseUri)
        assertTrue(opts.payloadInUri)
        assertTrue(opts.acceptResultUri)
        assertEquals("signature", opts.returnType)

        val plain = parseSignerQueryOptions("content://com.greenart7c3.nostrsigner.SIGN_EVENT")
        assertEquals("content://com.greenart7c3.nostrsigner.SIGN_EVENT", plain.baseUri)
        assertFalse(plain.payloadInUri)
        assertFalse(plain.acceptResultUri)
        assertNull(plain.returnType)
    }

    @Test
    fun `buildSignEventCursorPayload omits event when return_type is signature on normal event`() {
        val payload = buildSignEventCursorPayload(
            result = "sig123",
            signedEventJson = """{"id":"1","content":"${"x".repeat(500)}"}""",
            isAnonymousZap = false,
            acceptResultUri = true,
            returnType = "signature",
            inlineThresholdBytes = 100,
            spillResponse = { "content://unused" },
        )

        assertArrayEquals(arrayOf("signature", "result"), payload.columns)
        assertArrayEquals(arrayOf<Any>("sig123", "sig123"), payload.values)
    }

    @Test
    fun `buildSignEventCursorPayload spills oversized event_uri while keeping signature inline`() {
        val bigJson = """{"id":"1","content":"${"a".repeat(250)}"}"""
        val payload = buildSignEventCursorPayload(
            result = "sig123",
            signedEventJson = bigJson,
            isAnonymousZap = false,
            acceptResultUri = true,
            returnType = "event",
            inlineThresholdBytes = 100,
            spillResponse = { bytes -> "content://pkg.PAYLOADS/tok-${bytes.size}" },
        )

        assertArrayEquals(arrayOf("signature", "result", EphemeralPayloadStore.EXTRA_EVENT_URI), payload.columns)
        assertArrayEquals(
            arrayOf<Any>("sig123", "sig123", "content://pkg.PAYLOADS/tok-${bigJson.toByteArray().size}"),
            payload.values,
        )
    }

    @Test
    fun `buildSignEventCursorPayload uses distinct tokens for oversized anonymous zap`() {
        val bigZap = """{"kind":9734,"tags":[["anon",""]],"content":"${"z".repeat(250)}"}"""
        var counter = 0
        val payload = buildSignEventCursorPayload(
            result = bigZap,
            signedEventJson = bigZap,
            isAnonymousZap = true,
            acceptResultUri = true,
            returnType = "signature",
            inlineThresholdBytes = 100,
            spillResponse = { "content://pkg.PAYLOADS/tok-${++counter}" },
        )

        assertArrayEquals(
            arrayOf(EphemeralPayloadStore.EXTRA_RESULT_URI, EphemeralPayloadStore.EXTRA_EVENT_URI),
            payload.columns,
        )
        assertArrayEquals(
            arrayOf<Any>("content://pkg.PAYLOADS/tok-1", "content://pkg.PAYLOADS/tok-2"),
            payload.values,
        )
    }

    @Test
    fun `buildSingleValueCursorPayload drops redundant event column and spills large payloads`() {
        val small = buildSingleValueCursorPayload(
            result = "hello",
            acceptResultUri = true,
            inlineThresholdBytes = 100,
            spillResponse = { "content://unused" },
        )
        assertArrayEquals(arrayOf("signature", "result"), small.columns)
        assertArrayEquals(arrayOf<Any>("hello", "hello"), small.values)

        val large = buildSingleValueCursorPayload(
            result = "b".repeat(200),
            acceptResultUri = true,
            inlineThresholdBytes = 100,
            spillResponse = { "content://pkg.PAYLOADS/res-1" },
        )
        assertArrayEquals(arrayOf(EphemeralPayloadStore.EXTRA_RESULT_URI), large.columns)
        assertArrayEquals(arrayOf<Any>("content://pkg.PAYLOADS/res-1"), large.values)

        val legacyLarge = buildSingleValueCursorPayload(
            result = "b".repeat(200),
            acceptResultUri = false,
            inlineThresholdBytes = 100,
            spillResponse = { "content://unused" },
        )
        assertArrayEquals(arrayOf("signature", "result"), legacyLarge.columns)
        assertArrayEquals(arrayOf<Any>("b".repeat(200), "b".repeat(200)), legacyLarge.values)
    }

    @Test
    fun `SignerProviderQuery routes operation uris carrying query parameters`() {
        val grant = ApplicationPermissionsEntity(
            id = 1,
            pkKey = "com.example.app",
            type = "SIGN_EVENT",
            kind = 1,
            acceptable = true,
            rememberType = 0,
            acceptUntil = TimeUtils.now() + 3600,
            rejectUntil = 0,
        )
        every { dao.getPermission("com.example.app", "SIGN_EVENT", 1) } returns grant
        every { dao.getSignPolicy("com.example.app") } returns null
        val signed = Event("cd".repeat(32), HEX, TimeUtils.now(), 1, emptyArray(), "hi", "ef".repeat(64))
        every { account.signSync<Event>(any(), 1, any(), any()) } returns signed

        val uri = mockk<Uri>()
        every {
            uri.toString()
        } returns "content://${BuildConfig.APPLICATION_ID}.SIGN_EVENT?accept_result_uri=1&return_type=signature"

        SignerProviderQuery(amber, amber.accountDatabases, amber.applicationIOScope).query(
            context = context,
            requesterId = "com.example.app",
            callerPackageName = null,
            operationUri = uri,
            arguments = arrayOf("""{"kind":1,"tags":[],"content":"hi"}""", "", NPUB),
        )

        verify(exactly = 1) { account.signSync<Event>(any(), 1, any(), "hi") }
    }

    private companion object {
        const val NPUB = "npub1test000000000000000000000000000000000000000000000000000000"
        val HEX = "ab".repeat(32)
    }
}
