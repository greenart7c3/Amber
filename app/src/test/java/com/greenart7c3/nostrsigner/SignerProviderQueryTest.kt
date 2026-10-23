package com.greenart7c3.nostrsigner

import android.content.Context
import android.net.Uri
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.HistoryDao
import com.greenart7c3.nostrsigner.database.HistoryDatabase
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.installAmberInstance
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.TimeUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Regression tests for GHSA-vx4h-56qj-wcp7: the NIP-42 relay-auth whitelist
 * must act only as a relay constraint (non-whitelisted relays auto-reject),
 * never as an authorization grant. Signing a kind-22242 event always requires
 * a requester-scoped permission (or a user prompt).
 *
 * MatrixCursor is an android.jar stub in local unit tests, so cursor contents
 * cannot be inspected here; instead these tests verify whether signing was
 * reached at all ([Account.signSync]) and which permission lookups ran.
 */
class SignerProviderQueryTest {
    private val context = mockk<Context>(relaxed = true)
    private lateinit var amber: Amber
    private lateinit var dao: ApplicationDao
    private lateinit var account: Account

    companion object {
        private const val REQUESTER = "com.attacker.app"
        private const val NPUB = "npub1test000000000000000000000000000000000000000000000000000000"
        private const val WHITELISTED_HOST = "relay.example.com"
        private const val WHITELISTED_URL = "wss://relay.example.com"
        private const val OTHER_URL = "wss://evil.example.com"
        private val HEX = "ab".repeat(32)

        private fun relayAuthJson(relayUrl: String) = """{"kind":22242,"tags":[["relay","$relayUrl"]],"content":""}"""
    }

    @Before
    fun setUp() {
        mockkObject(AmberLog)
        every { AmberLog.d(any(), any<String>()) } returns Unit
        every { AmberLog.d(any(), any(), any()) } returns Unit

        val historyDao = mockk<HistoryDao>(relaxed = true)
        val historyDatabase = mockk<HistoryDatabase>()
        every { historyDatabase.dao() } returns historyDao

        dao = mockk()
        amber = mockk()
        every { amber.applicationIOScope } returns CoroutineScope(Dispatchers.Unconfined)
        every { amber.getHistoryDatabase(any()) } returns historyDatabase
        every { amber.dao(any()) } returns dao
        installAmberInstance(amber)

        account = mockk()
        every { account.npub } returns NPUB
        every { account.hexKey } returns HEX

        mockkObject(LocalPreferences)
        every { LocalPreferences.loadFromEncryptedStorageSync(any(), any()) } returns account
        every { LocalPreferences.allSavedAccounts(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        unmockkObject(LocalPreferences)
        unmockkObject(AmberLog)
    }

    private fun signEventUri(): Uri {
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://${BuildConfig.APPLICATION_ID}.SIGN_EVENT"
        return uri
    }

    private fun queryRelayAuth(relayUrl: String) = SignerProviderQuery.query(
        context = context,
        requesterId = REQUESTER,
        callerPackageName = null,
        operationUri = signEventUri(),
        arguments = arrayOf(relayAuthJson(relayUrl), "", NPUB),
    )

    private fun acceptedPermission(relay: String) = ApplicationPermissionsEntity(
        id = 1,
        pkKey = REQUESTER,
        type = "SIGN_EVENT",
        kind = 22242,
        acceptable = true,
        rememberType = 0,
        acceptUntil = TimeUtils.now() + 3600,
        rejectUntil = 0,
        relay = relay,
    )

    private fun stubNoGrants() {
        every { dao.getPermissionForRelay(REQUESTER, "SIGN_EVENT", 22242, WHITELISTED_HOST) } returns null
        every { dao.getWildcardRelayPermission(REQUESTER, "SIGN_EVENT", 22242) } returns null
        every { dao.getSignPolicy(REQUESTER) } returns null
    }

    private fun stubSigning() {
        val signed = Event("cd".repeat(32), HEX, TimeUtils.now(), 22242, arrayOf(arrayOf("relay", WHITELISTED_URL)), "", "ef".repeat(64))
        every { account.signSync<Event>(any(), 22242, any(), any()) } returns signed
    }

    @Test
    fun `whitelisted relay does not auto-accept a requester with no permission`() {
        every { amber.settings } returns AmberSettings(authWhitelist = listOf(WHITELISTED_HOST))
        stubNoGrants()

        val cursor = queryRelayAuth(WHITELISTED_URL)

        // null = no silent signing; the caller must surface a user prompt.
        assertNull(cursor)
        verify { dao.getPermissionForRelay(REQUESTER, "SIGN_EVENT", 22242, WHITELISTED_HOST) }
        verify(exactly = 0) { account.signSync<Event>(any(), any(), any(), any()) }
    }

    @Test
    fun `whitelisted relay signs silently when the requester holds an accepted relay grant`() {
        every { amber.settings } returns AmberSettings(authWhitelist = listOf(WHITELISTED_HOST))
        every { dao.getPermissionForRelay(REQUESTER, "SIGN_EVENT", 22242, WHITELISTED_HOST) } returns acceptedPermission(WHITELISTED_HOST)
        every { dao.getSignPolicy(REQUESTER) } returns null
        stubSigning()

        queryRelayAuth(WHITELISTED_URL)

        verify { account.signSync<Event>(any(), 22242, any(), any()) }
    }

    @Test
    fun `whitelisted relay signs silently when the requester holds a wildcard relay grant`() {
        every { amber.settings } returns AmberSettings(authWhitelist = listOf(WHITELISTED_HOST))
        every { dao.getPermissionForRelay(REQUESTER, "SIGN_EVENT", 22242, WHITELISTED_HOST) } returns null
        every { dao.getWildcardRelayPermission(REQUESTER, "SIGN_EVENT", 22242) } returns acceptedPermission("*")
        every { dao.getSignPolicy(REQUESTER) } returns null
        stubSigning()

        queryRelayAuth(WHITELISTED_URL)

        verify { account.signSync<Event>(any(), 22242, any(), any()) }
    }

    @Test
    fun `non-whitelisted relay is auto-rejected before any permission lookup`() {
        every { amber.settings } returns AmberSettings(authWhitelist = listOf(WHITELISTED_HOST))

        queryRelayAuth(OTHER_URL)

        verify(exactly = 0) { dao.getPermissionForRelay(any(), any(), any(), any()) }
        verify(exactly = 0) { dao.getWildcardRelayPermission(any(), any(), any()) }
        verify(exactly = 0) { account.signSync<Event>(any(), any(), any(), any()) }
    }

    @Test
    fun `empty whitelist keeps the normal permission flow`() {
        every { amber.settings } returns AmberSettings(authWhitelist = emptyList())
        stubNoGrants()

        val cursor = queryRelayAuth(WHITELISTED_URL)

        assertNull(cursor)
        verify { dao.getPermissionForRelay(REQUESTER, "SIGN_EVENT", 22242, WHITELISTED_HOST) }
        verify(exactly = 0) { account.signSync<Event>(any(), any(), any(), any()) }
    }

    private fun decryptUri(): Uri {
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://${BuildConfig.APPLICATION_ID}.NIP44_DECRYPT"
        return uri
    }

    private fun queryDecrypt() = SignerProviderQuery.query(
        context = context,
        requesterId = REQUESTER,
        callerPackageName = null,
        operationUri = decryptUri(),
        arguments = arrayOf("ciphertext", "cd".repeat(32), NPUB),
    )

    private fun decryptRow(type: String, accept: Boolean) = ApplicationPermissionsEntity(
        id = 2,
        pkKey = REQUESTER,
        type = type,
        kind = null,
        acceptable = accept,
        rememberType = 0,
        acceptUntil = if (accept) TimeUtils.now() + 3600 else 0,
        rejectUntil = if (accept) 0 else TimeUtils.now() + 3600,
    )

    /** Only [rows] exist for the requester; every other decrypt lookup is empty. */
    private fun stubDecryptGrants(vararg rows: ApplicationPermissionsEntity) {
        every { amber.getLogDatabase(any()) } returns mockk(relaxed = true)
        every { dao.getSignPolicy(REQUESTER) } returns null
        every { dao.getPermission(REQUESTER, any<String>()) } answers { rows.firstOrNull { it.type == secondArg<String>() } }
        every { dao.getPermission(REQUESTER, any<String>(), any<Int>()) } returns null
        mockkObject(AmberUtils)
        coEvery { AmberUtils.encryptOrDecryptData(any(), any(), any(), any(), any(), any()) } returns "plaintext"
    }

    // A reject-only caller used to pass the pre-check: Amber decrypted, and the
    // response (rejected for an event, null for anything else) told the caller
    // how the plaintext classified.
    @Test
    fun `a caller holding only rejections is answered without decrypting`() {
        stubDecryptGrants(decryptRow("DECRYPT_EVENT", accept = false))
        try {
            assertNull(queryDecrypt())
            coVerify(exactly = 0) { AmberUtils.encryptOrDecryptData(any(), any(), any(), any(), any(), any()) }
        } finally {
            unmockkObject(AmberUtils)
        }
    }

    @Test
    fun `a caller with an accepting grant still gets its request decrypted`() {
        stubDecryptGrants(decryptRow("DECRYPT_CLEAR_TEXT", accept = true))
        try {
            queryDecrypt()
            coVerify(exactly = 1) { AmberUtils.encryptOrDecryptData("ciphertext", SignerType.NIP44_DECRYPT, any(), any(), any(), any()) }
        } finally {
            unmockkObject(AmberUtils)
        }
    }
}
