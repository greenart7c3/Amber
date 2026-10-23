package com.greenart7c3.nostrsigner

import android.content.Context
import com.greenart7c3.nostrsigner.service.installAmberInstance
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Logout promises to delete all local information. The per-account databases
 * (connections and their grants, history, logs) used to be closed but left on
 * disk, so re-importing the key silently restored every old grant.
 */
class LogoutDeletesDatabasesTest {
    private val npub = "npub1logouttest"
    private val context = mockk<Context>(relaxed = true)
    private val amber = mockk<Amber>(relaxed = true)

    @Before
    fun setUp() {
        installAmberInstance(amber)
        mockkObject(LocalPreferences, AmberLog)
        every { AmberLog.d(any(), any<String>()) } returns Unit
        every { AmberLog.d(any(), any(), any()) } returns Unit
        every { AmberLog.e(any(), any<String>()) } returns Unit
        every { AmberLog.e(any(), any(), any()) } returns Unit
        // File and DataStore cleanup is out of scope here.
        every { LocalPreferences["removeAccount"](any<Context>(), any<String>()) } returns Unit
        every { LocalPreferences["deleteUserPreferenceFile"](any<Context>(), any<String>()) } returns Unit
        coEvery { LocalPreferences["deleteUserDataStoreFile"](any<Context>(), any<String>()) } returns Unit
        every { LocalPreferences["savedAccounts"](any<Context>()) } returns listOf("npub1other")
        every { LocalPreferences.currentAccount(any()) } returns "npub1other"
        every { context.deleteDatabase(any()) } returns true
        every { amber.closeDatabasesFor(any()) } just runs
    }

    @After
    fun tearDown() {
        unmockkObject(LocalPreferences, AmberLog)
    }

    @Test
    fun logoutDeletesTheAccountDatabasesAfterClosingThem() {
        LocalPreferences.updatePrefsForLogout(npub, context)

        verifyOrder {
            amber.closeDatabasesFor(npub)
            context.deleteDatabase("amber_db_$npub")
        }
        verify { context.deleteDatabase("log_db_$npub") }
        verify { context.deleteDatabase("history_db_$npub") }
        verify(exactly = 0) { context.deleteDatabase(match { !it.endsWith(npub) }) }
    }
}
