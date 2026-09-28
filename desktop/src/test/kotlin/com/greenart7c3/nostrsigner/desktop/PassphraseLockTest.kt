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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Exercises the full mandatory-passphrase lifecycle against a scratch data
 * dir: plain pre-setup mode, setup, database encryption at rest, lock,
 * verify, unlock, and passphrase change. This is the only test class that
 * enables the lock — [PassphraseLock] and [DesktopKeyStore] are process-wide
 * singletons, so a second class toggling them would race this one.
 * Small Argon2 parameters keep the tests fast; production defaults are only
 * a cost change, not a code path change.
 */
class PassphraseLockTest {
    companion object {
        private val fastKdf = PassphraseLock.KdfParams(memoryKb = 1024, iterations = 1, parallelism = 1)

        @JvmStatic
        @BeforeClass
        fun isolateDataDir() {
            val tmp = File.createTempFile("amber-lock-test", "").apply {
                delete()
                mkdirs()
                deleteOnExit()
            }
            System.setProperty("user.home", tmp.absolutePath)
        }
    }

    @Test
    fun fullLifecycle() = runBlocking {
        // Plain mode (pre-setup): keys and the account database are plaintext.
        val secret = "super-secret-private-key"
        val cipher1 = DesktopKeyStore.encrypt(secret)
        assertEquals(secret, DesktopKeyStore.decrypt(cipher1))
        assertEquals(PassphraseLock.Status.DISABLED, PassphraseLock.state.value)
        assertFalse(PassphraseLock.isEnabled())

        val marker = "SECRET_APP_NAME_9f3a"
        val account = AccountManager.addAccount(KeyPair(), name = "db")
        val store = AmberDesktop.store(account.npub)
        store.upsert(AppWithPermissions(app = AppRecord(key = "app-key-1", name = marker, pubKey = account.hexKey)))
        store.addLog("wss://relay.example.com", "bunker", "sensitive-log-line")
        val appsFile = File(AppDirs.accountDir(account.npub), "applications.json")
        assertTrue("app db should be plaintext without a passphrase", appsFile.readText().contains(marker))

        // Setup wraps the same master key under the passphrase: old
        // ciphertexts still decrypt, and the account database is re-encrypted
        // at rest.
        PassphraseLock.enable("correct horse battery staple".toCharArray(), fastKdf)
        assertTrue(PassphraseLock.isEnabled())
        assertEquals(PassphraseLock.Status.UNLOCKED, PassphraseLock.state.value)
        assertEquals(secret, DesktopKeyStore.decrypt(cipher1))

        // The unprotected copies are gone.
        val dataDir = com.greenart7c3.nostrsigner.desktop.core.AppDirs.dataDir
        assertFalse(File(dataDir, "amber.keystore").exists())
        assertFalse(File(dataDir, "keystore.pass").exists())
        assertTrue(File(dataDir, "master.key.enc").exists())

        val encrypted = appsFile.readText()
        assertTrue("encrypted file should carry the marker header", encrypted.startsWith("AMBERENC1:"))
        assertFalse("plaintext app name must not survive in the encrypted file", encrypted.contains(marker))

        // A fresh reader (simulating a restart) decrypts it back.
        val reloaded = AccountStore(account.npub)
        assertEquals(marker, reloaded.apps.value.first().app.name)

        // Locking evicts the key: crypto operations fail.
        PassphraseLock.lock()
        assertEquals(PassphraseLock.Status.LOCKED, PassphraseLock.state.value)
        assertFalse(DesktopKeyStore.isMasterKeyAvailable())
        assertThrows(DesktopKeyStore.LockedException::class.java) {
            runBlocking { DesktopKeyStore.decrypt(cipher1) }
        }

        // verify() checks the passphrase without unlocking anything.
        assertTrue(PassphraseLock.verify("correct horse battery staple".toCharArray()))
        assertFalse(PassphraseLock.verify("wrong passphrase".toCharArray()))
        assertEquals(PassphraseLock.Status.LOCKED, PassphraseLock.state.value)
        assertThrows(DesktopKeyStore.LockedException::class.java) {
            runBlocking { DesktopKeyStore.decrypt(cipher1) }
        }

        // Wrong passphrase is rejected, right one restores access.
        assertFalse(PassphraseLock.unlock("wrong passphrase".toCharArray()))
        assertEquals(PassphraseLock.Status.LOCKED, PassphraseLock.state.value)
        assertTrue(PassphraseLock.unlock("correct horse battery staple".toCharArray()))
        assertEquals(secret, DesktopKeyStore.decrypt(cipher1))
        assertEquals(marker, AmberDesktop.store(account.npub).apps.value.first().app.name)
        assertTrue("db must stay encrypted while a passphrase is set", appsFile.readText().startsWith("AMBERENC1:"))

        // Changing the passphrase requires the old one and keeps the data.
        assertFalse(PassphraseLock.changePassphrase("nope".toCharArray(), "new passphrase 42".toCharArray(), fastKdf))
        assertTrue(PassphraseLock.changePassphrase("correct horse battery staple".toCharArray(), "new passphrase 42".toCharArray(), fastKdf))
        PassphraseLock.lock()
        assertFalse(PassphraseLock.unlock("correct horse battery staple".toCharArray()))
        assertTrue(PassphraseLock.unlock("new passphrase 42".toCharArray()))
        assertEquals(secret, DesktopKeyStore.decrypt(cipher1))
    }
}
