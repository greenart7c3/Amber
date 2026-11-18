package com.greenart7c3.nostrsigner.ui

import com.greenart7c3.nostrsigner.AccountPreferences
import com.greenart7c3.nostrsigner.AccountStorage
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.service.ApplicationBackup
import com.greenart7c3.nostrsigner.service.BackupApplication
import com.greenart7c3.nostrsigner.service.BackupPayload
import com.greenart7c3.nostrsigner.service.RestoreResult
import com.greenart7c3.nostrsigner.service.newTestAccount
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountStateViewModelRestoreTest {
    // Unconfined so maybeOfferRestore's internal launch runs to completion on the
    // calling thread; every ApplicationBackup call it makes is a mocked immediate.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val amber = mockk<Amber>(relaxed = true)

    @Before
    fun setUp() {
        // acceptRestore reports its result on Main.
        Dispatchers.setMain(Dispatchers.Unconfined)
        mockkObject(BuildFlavorChecker)
        every { BuildFlavorChecker.isOfflineFlavor() } returns false

        mockkObject(LocalPreferences, AccountStorage, SavedAccounts, AccountPreferences)
        every { SavedAccounts.current(any()) } returns null
        every { SavedAccounts.all(any()) } returns emptyList()
        coEvery { AccountStorage.all(any()) } returns emptyList()
        every { AccountStorage.loadSync(any(), any()) } returns null
        coEvery { AccountStorage.load(any(), any()) } returns null
        // Post-logout state: AccountStorage.logout wipes prefs_<npub>, so the
        // publish-consent flag reads false on relogin.
        every { AccountPreferences.getBackupApplications(any(), any()) } returns false

        val dao = mockk<ApplicationDao>(relaxed = true)
        coEvery { dao.getAll(any()) } returns emptyList()
        every { amber.applicationIOScope } returns scope
        every { amber.dao(any()) } returns dao

        mockkObject(ApplicationBackup)
        coEvery { ApplicationBackup.resolveReadRelays(any(), any(), any()) } returns setOf(mockk())
        coEvery { ApplicationBackup.fetchLatestBackupEvent(any(), any(), any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(BuildFlavorChecker)
        unmockkObject(LocalPreferences, AccountStorage, SavedAccounts, AccountPreferences)
        unmockkObject(ApplicationBackup)
    }

    @Test
    fun `maybeOfferRestore reaches relay fetch even with backup publishing disabled`() {
        val viewModel = newViewModel()
        val account = newTestAccount(scope)

        viewModel.maybeOfferRestore(account)

        coVerify {
            ApplicationBackup.resolveReadRelays(account, any(), any())
            ApplicationBackup.fetchLatestBackupEvent(account, any(), any())
        }
        assertNull(viewModel.restorePrompt.value)
    }

    @Test
    fun `acceptRestore restores only the selected applications`() {
        val viewModel = newViewModel()
        val account = newTestAccount(scope)
        val kept = backupApp("kept")
        val payload = BackupPayload(applications = listOf(kept, backupApp("unchecked")))
        viewModel.restorePrompt.value = RestorePromptState.Found(account, payload, createdAt = 0)
        val restored = slot<BackupPayload>()
        coEvery { ApplicationBackup.restoreFromPayload(any(), capture(restored), any(), any()) } returns RestoreResult.Success(1, 0)

        viewModel.acceptRestore(listOf(kept)) { }

        assertEquals(listOf(kept), restored.captured.applications)
        assertNull(viewModel.restorePrompt.value)
    }

    private fun newViewModel() = AccountStateViewModel(
        npub = null,
        appContext = amber,
        settingsStore = amber,
        accountRepository = amber,
        relayClient = amber,
        appScope = scope,
    )

    private fun backupApp(key: String) = BackupApplication(
        key = key,
        name = key,
        relays = emptyList(),
        url = "",
        icon = "",
        description = "",
        pubKey = "",
        isConnected = true,
        secret = "",
        useSecret = false,
        signPolicy = 1,
        closeApplication = true,
        deleteAfter = 0,
        lastUsed = 0,
        permissions = emptyList(),
    )
}
