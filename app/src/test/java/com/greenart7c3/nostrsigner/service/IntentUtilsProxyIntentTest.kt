package com.greenart7c3.nostrsigner.service

import android.content.Context
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryDao
import com.greenart7c3.nostrsigner.database.HistoryDatabase
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.ReturnType
import com.greenart7c3.nostrsigner.models.SignerType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression tests for the bunker-proxy NIP-55 intent path. The approval UI never
 * renders for proxy accounts (SignerActivity stays invisible), so a `get_public_key`
 * connect must be answered AND the caller registered in the database — otherwise
 * the calling app hangs forever and never shows up on Amber's main screen.
 */
class IntentUtilsProxyIntentTest {
    private val context = mockk<Context>(relaxed = true)
    private lateinit var amber: Amber
    private lateinit var dao: ApplicationDao
    private lateinit var proxyAccount: Account
    private lateinit var normalAccount: Account

    companion object {
        private val HEX = "ab".repeat(32)
        private const val PROXY_NPUB = "npub1proxy"
        private const val CALLER = "com.amethyst"
    }

    @Before
    fun setUp() {
        mockkObject(AmberLog)
        every { AmberLog.d(any(), any<String>()) } returns Unit
        every { AmberLog.d(any(), any(), any()) } returns Unit
        every { AmberLog.e(any(), any<String>()) } returns Unit
        every { AmberLog.e(any(), any(), any()) } returns Unit

        amber = mockk()
        every { amber.applicationIOScope } returns CoroutineScope(Dispatchers.Unconfined)
        every { amber.getMainActivity() } returns null
        every { amber.getLogDatabase(any()) } returns mockk(relaxed = true)
        val historyDao = mockk<HistoryDao>(relaxed = true)
        val historyDatabase = mockk<HistoryDatabase>()
        every { historyDatabase.dao() } returns historyDao
        every { amber.getHistoryDatabase(any()) } returns historyDatabase

        dao = mockk()
        every { amber.dao(any()) } returns dao
        installAmberInstance(amber)

        proxyAccount = mockk(relaxed = true)
        every { proxyAccount.isProxy } returns true
        every { proxyAccount.npub } returns PROXY_NPUB
        every { proxyAccount.hexKey } returns HEX
        every { proxyAccount.signPolicy } returns 1

        normalAccount = mockk(relaxed = true)
        every { normalAccount.isProxy } returns false

        // App not saved yet, so registerProxyApp takes the create path.
        coEvery { dao.getByKey(any()) } returns null
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun intentData(
        type: SignerType = SignerType.GET_PUBLIC_KEY,
        currentAccount: String = PROXY_NPUB,
    ) = IntentData(
        data = "",
        name = "Amethyst",
        type = type,
        pubKey = HEX,
        id = "id-1",
        callBackUrl = null,
        compression = CompressionType.NONE,
        returnType = ReturnType.SIGNATURE,
        permissions = null,
        currentAccount = currentAccount,
        route = null,
        event = null,
        encryptedData = null,
    )

    @Test
    fun `proxy get_public_key intent is answered and saves the app`() = runBlocking {
        val captured = slot<ApplicationWithPermissions>()
        coEvery { dao.insertApplicationWithPermissions(capture(captured)) } returns Unit

        val handled = IntentUtils.handleProxyIntent(context, proxyAccount, intentData(), CALLER)

        assertTrue(handled)
        val saved = captured.captured
        assertEquals(CALLER, saved.application.key)
        assertEquals(HEX, saved.application.pubKey)
        assertTrue(saved.application.isConnected)
        assertTrue(saved.permissions.any { it.type == "GET_PUBLIC_KEY" && it.acceptable })
        coVerify(exactly = 1) { dao.insertApplicationWithPermissions(any()) }
    }

    @Test
    fun `proxy get_public_key targeting another account is not handled`() = runBlocking {
        val handled = IntentUtils.handleProxyIntent(
            context,
            proxyAccount,
            intentData(currentAccount = "npub1other"),
            CALLER,
        )

        assertFalse(handled)
        coVerify(exactly = 0) { dao.insertApplicationWithPermissions(any()) }
    }

    @Test
    fun `non-proxy account falls through to the approval flow`() = runBlocking {
        val handled = IntentUtils.handleProxyIntent(context, normalAccount, intentData(), CALLER)

        assertFalse(handled)
        coVerify(exactly = 0) { dao.insertApplicationWithPermissions(any()) }
    }

    @Test
    fun `get_public_key targeting a proxy account is handled even when another account is active`() = runBlocking {
        mockkObject(LocalPreferences)
        every { LocalPreferences.loadFromEncryptedStorageSync(any(), eq(PROXY_NPUB)) } returns proxyAccount
        val captured = slot<ApplicationWithPermissions>()
        coEvery { dao.insertApplicationWithPermissions(capture(captured)) } returns Unit

        // Active account is normal, but the intent targets the proxy account.
        val handled = IntentUtils.handleProxyIntent(context, normalAccount, intentData(), CALLER)

        assertTrue(handled)
        assertEquals(CALLER, captured.captured.application.key)
        assertTrue(captured.captured.permissions.any { it.type == "GET_PUBLIC_KEY" && it.acceptable })
        coVerify(exactly = 1) { dao.insertApplicationWithPermissions(any()) }
    }
}
