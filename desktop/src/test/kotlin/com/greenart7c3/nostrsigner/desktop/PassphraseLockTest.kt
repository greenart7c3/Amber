package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.DesktopKeyStore
import com.greenart7c3.nostrsigner.desktop.core.PassphraseLock
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Exercises the full passphrase-lock lifecycle against a scratch data dir.
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
        // Plain mode: encrypt something so the master key exists.
        val secret = "super-secret-private-key"
        val cipher1 = DesktopKeyStore.encrypt(secret)
        assertEquals(secret, DesktopKeyStore.decrypt(cipher1))
        assertEquals(PassphraseLock.Status.DISABLED, PassphraseLock.state.value)

        // Enable the lock: same master key, so old ciphertexts still decrypt.
        PassphraseLock.enable("correct horse battery staple".toCharArray(), fastKdf)
        assertTrue(PassphraseLock.isEnabled())
        assertEquals(PassphraseLock.Status.UNLOCKED, PassphraseLock.state.value)
        assertEquals(secret, DesktopKeyStore.decrypt(cipher1))

        // The unprotected copies are gone.
        val dataDir = com.greenart7c3.nostrsigner.desktop.core.AppDirs.dataDir
        assertFalse(File(dataDir, "amber.keystore").exists())
        assertFalse(File(dataDir, "keystore.pass").exists())
        assertTrue(File(dataDir, "master.key.enc").exists())

        // Locking evicts the key: crypto operations fail.
        PassphraseLock.lock()
        assertEquals(PassphraseLock.Status.LOCKED, PassphraseLock.state.value)
        assertThrows(DesktopKeyStore.LockedException::class.java) {
            runBlocking { DesktopKeyStore.decrypt(cipher1) }
        }

        // Wrong passphrase is rejected, right one restores access.
        assertFalse(PassphraseLock.unlock("wrong passphrase".toCharArray()))
        assertEquals(PassphraseLock.Status.LOCKED, PassphraseLock.state.value)
        assertTrue(PassphraseLock.unlock("correct horse battery staple".toCharArray()))
        assertEquals(secret, DesktopKeyStore.decrypt(cipher1))

        // Changing the passphrase requires the old one and keeps the data.
        assertFalse(PassphraseLock.changePassphrase("nope".toCharArray(), "new passphrase 42".toCharArray(), fastKdf))
        assertTrue(PassphraseLock.changePassphrase("correct horse battery staple".toCharArray(), "new passphrase 42".toCharArray(), fastKdf))
        PassphraseLock.lock()
        assertFalse(PassphraseLock.unlock("correct horse battery staple".toCharArray()))
        assertTrue(PassphraseLock.unlock("new passphrase 42".toCharArray()))
        assertEquals(secret, DesktopKeyStore.decrypt(cipher1))

        // Disabling restores the keystore + password-store path.
        PassphraseLock.disable()
        assertEquals(PassphraseLock.Status.DISABLED, PassphraseLock.state.value)
        assertFalse(File(dataDir, "master.key.enc").exists())
        assertTrue(File(dataDir, "amber.keystore").exists())
        assertEquals(secret, DesktopKeyStore.decrypt(cipher1))
    }
}
