package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AccountStore
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.AppDirs
import com.greenart7c3.nostrsigner.desktop.core.AppRecord
import com.greenart7c3.nostrsigner.desktop.core.AppWithPermissions
import com.greenart7c3.nostrsigner.desktop.core.DesktopKeyStore
import com.greenart7c3.nostrsigner.desktop.core.PassphraseLock
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Verifies that the per-account database (applications/permissions/history/
 * logs) is encrypted at rest once a passphrase lock is set, and migrates
 * transparently in both directions.
 */
class DatabaseEncryptionTest {
    companion object {
        private val fastKdf = PassphraseLock.KdfParams(memoryKb = 1024, iterations = 1, parallelism = 1)

        @JvmStatic
        @BeforeClass
        fun isolateDataDir() {
            val tmp = File.createTempFile("amber-db-enc", "").apply {
                delete()
                mkdirs()
                deleteOnExit()
            }
            System.setProperty("user.home", tmp.absolutePath)
        }
    }

    @Test
    fun databaseIsEncryptedUnderPassphrase() = runBlocking {
        val marker = "SECRET_APP_NAME_9f3a"
        val account = AccountManager.addAccount(KeyPair(), name = "db")
        val store = AmberDesktop.store(account.npub)
        store.upsert(AppWithPermissions(app = AppRecord(key = "app-key-1", name = marker, pubKey = account.hexKey)))
        store.addLog("wss://relay.example.com", "bunker", "sensitive-log-line")

        val appsFile = File(AppDirs.accountDir(account.npub), "applications.json")

        // Before any passphrase: plaintext JSON on disk.
        assertTrue("app db should be plaintext without a passphrase", appsFile.readText().contains(marker))

        // Enabling a passphrase re-encrypts the database at rest.
        PassphraseLock.enable("correct horse battery".toCharArray(), fastKdf)
        val encrypted = appsFile.readText()
        assertTrue("encrypted file should carry the marker header", encrypted.startsWith("AMBERENC1:"))
        assertFalse("plaintext app name must not survive in the encrypted file", encrypted.contains(marker))

        // A fresh reader (simulating a restart) decrypts it back.
        val reloaded = AccountStore(account.npub)
        assertEquals(marker, reloaded.apps.value.first().app.name)

        // Locking evicts the key: nothing can be written to the database.
        PassphraseLock.lock()
        assertFalse(DesktopKeyStore.isMasterKeyAvailable())

        // Unlock restores access; disabling the lock rewrites plaintext.
        assertTrue(PassphraseLock.unlock("correct horse battery".toCharArray()))
        assertEquals(marker, AmberDesktop.store(account.npub).apps.value.first().app.name)

        PassphraseLock.disable()
        assertFalse("db should be plaintext after removing the passphrase", appsFile.readText().startsWith("AMBERENC1:"))
        assertTrue(appsFile.readText().contains(marker))
    }
}
