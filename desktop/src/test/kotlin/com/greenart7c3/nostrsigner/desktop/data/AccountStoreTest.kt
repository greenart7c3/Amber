package com.greenart7c3.nostrsigner.desktop.data

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Exercises [AccountStore]'s filesystem bookkeeping (listing, active-pointer, logout, and the
 * legacy-layout migration's early-exit/failure paths) without touching [SecureCryptoHelper] —
 * that needs a running OS keychain / Secret Service provider that CI/sandboxes don't have.
 * `[AccountStore.generate]`/`[AccountStore.load]`, which do need it, are exercised manually
 * (see the plan's verification section) rather than here.
 */
class AccountStoreTest {
    companion object {
        init {
            // AppDataDir.directory is a process-wide `by lazy` resolved from user.home on first
            // access — override it before any test in this class can trigger that resolution,
            // so these tests never touch the real ~/.amber-bunker.
            val tempHome = Files.createTempDirectory("amber-bunker-account-store-test").toFile()
            System.setProperty("user.home", tempHome.absolutePath)
        }
    }

    @AfterTest
    fun tearDown() {
        AppDataDir.accountsDir.listFiles()?.forEach { it.deleteRecursively() }
        AppDataDir.activeAccountFile.delete()
        AppDataDir.file("account.key").delete()
        AppDataDir.file("bunker.db").delete()
    }

    @Test
    fun migrateLegacyLayoutIfNeededIsNotNeededWhenNoLegacyKeyExists() = runTest {
        assertEquals(MigrationResult.NotNeeded, AccountStore.migrateLegacyLayoutIfNeeded())
    }

    @Test
    fun migrateLegacyLayoutIfNeededFailsAndLeavesUndecryptableKeyUntouched() = runTest {
        val legacyKey = AppDataDir.file("account.key")
        legacyKey.writeText("not a real encrypted key")

        val result = AccountStore.migrateLegacyLayoutIfNeeded()

        assertEquals(MigrationResult.Failed, result)
        assertTrue(legacyKey.exists())
        assertEquals("not a real encrypted key", legacyKey.readText())
    }

    @Test
    fun listActiveSetAndLogoutTrackStoredAccountsByKeyFilePresence() = runTest {
        val pubKeyA = "aaaa0000111122223333444455556666777788889999aaaabbbbccccddddee"
        val pubKeyB = "bbbb0000111122223333444455556666777788889999aaaabbbbccccddddff"
        File(AppDataDir.accountDir(pubKeyA), "account.key").writeText("dummy-a")
        File(AppDataDir.accountDir(pubKeyB), "account.key").writeText("dummy-b")

        assertEquals(listOf(pubKeyA, pubKeyB).sorted(), AccountStore.listAccounts().sorted())
        assertTrue(AccountStore.hasAnyAccount())
        assertNull(AccountStore.activeAccount())

        AccountStore.setActive(pubKeyA)
        assertEquals(pubKeyA, AccountStore.activeAccount())

        AccountStore.logout(pubKeyA)
        assertEquals(listOf(pubKeyB), AccountStore.listAccounts())
        // The active pointer still names pubKeyA, but its key file is gone, so it no longer resolves.
        assertNull(AccountStore.activeAccount())

        AccountStore.logout(pubKeyB)
        assertTrue(AccountStore.listAccounts().isEmpty())
        assertTrue(!AccountStore.hasAnyAccount())
    }
}
