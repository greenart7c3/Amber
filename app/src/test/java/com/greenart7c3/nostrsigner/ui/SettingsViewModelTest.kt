package com.greenart7c3.nostrsigner.ui

import android.content.Context
import com.greenart7c3.nostrsigner.AccountInfo
import com.greenart7c3.nostrsigner.AccountPreferences
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.RelayClient
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.database.HistoryDao
import com.greenart7c3.nostrsigner.database.LogCleanupDao
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.models.ThemeMode
import com.greenart7c3.nostrsigner.models.TorMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val callingThreadScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val historyDao = mockk<HistoryDao>(relaxed = true)
    private val logDao = mockk<LogCleanupDao>(relaxed = true)
    private val relayClient = mockk<RelayClient>(relaxed = true)
    private val accountRepository = mockk<AccountRepository> {
        every { getHistoryDatabase(any()).dao() } returns historyDao
        every { getLogDatabase(any()).cleanupDao() } returns logDao
    }
    private val settingsStore = object : SettingsStore {
        override var settings = AmberSettings(torMode = TorMode.ORBOT)
        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    }
    private val appContext = mockk<Context>(relaxed = true) {
        every { getDatabasePath(any()) } returns File("/nonexistent/db")
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        mockkObject(LocalPreferences, SavedAccounts, AccountPreferences, AmberLog)
        LocalPreferences.install(settingsStore, accountRepository)
        mockkStatic("com.greenart7c3.nostrsigner.commonsui.StringsKt")
        every { getStringBlocking(any(), *anyVararg()) } returns ""
        every { AmberLog.d(any(), any<String>()) } returns Unit
        every { SavedAccounts.all(any()) } returns listOf(AccountInfo("npub-a", true), AccountInfo("npub-b", true))
        updateTorModeStoresTheModeAndReloadsTheSettings()
        coEvery { historyDao.deleteOldHistory(any()) } returns 1
        coEvery { logDao.deleteOldLog(any()) } returns 1
    }

    private fun updateTorModeStoresTheModeAndReloadsTheSettings() {
        every { LocalPreferences.updateTorMode(any(), any(), any()) } answers { settingsStore.settings = settingsStore.settings.copy(torMode = secondArg()) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun newViewModel() = SettingsViewModel(
        appContext = appContext,
        settingsStore = settingsStore,
        accountRepository = accountRepository,
        relayClient = relayClient,
        appScope = callingThreadScope,
        npub = "npub-a",
        themeMode = MutableStateFlow(ThemeMode.SYSTEM),
        updater = null,
    )

    @Test
    fun `clearing old entries prunes every account and then refreshes the relays`() {
        val viewModel = newViewModel()

        viewModel.clearOldEntries()

        coVerify(exactly = 1) { accountRepository.getHistoryDatabase("npub-a") }
        coVerify(exactly = 1) { accountRepository.getHistoryDatabase("npub-b") }
        coVerify(exactly = 2) { logDao.deleteOldLog(any()) }
        coVerify(exactly = 1) { relayClient.checkForNewRelaysAndUpdateAllFilters(any()) }
        assertFalse(viewModel.state.value.isLoading)
        assertEquals("0", viewModel.state.value.databaseSizeMb)
    }

    @Test
    fun `disabling Tor stores DISABLED and reconnects`() {
        val viewModel = newViewModel()

        viewModel.disableTor()

        awaitTheReconnectThatEndsTheIoWrite()
        coVerify { LocalPreferences.updateTorMode(appContext, TorMode.DISABLED, any()) }
        assertEquals(TorMode.DISABLED, viewModel.state.value.torMode)
    }

    private fun awaitTheReconnectThatEndsTheIoWrite() {
        coVerify(timeout = 5_000) { relayClient.checkForNewRelaysAndUpdateAllFilters(any()) }
    }

    @Test
    fun `refresh follows a reloaded settings object, kill-switch flow included`() {
        val viewModel = newViewModel()

        settingsStore.settings = AmberSettings(torMode = TorMode.BUILTIN, killSwitch = MutableStateFlow(true))
        viewModel.refresh()

        assertEquals(TorMode.BUILTIN, viewModel.state.value.torMode)
        assertTrue(viewModel.state.value.killSwitch)
        settingsStore.settings.killSwitch.value = false
        assertFalse(viewModel.state.value.killSwitch)
    }
}
