package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AccountRecord
import com.greenart7c3.nostrsigner.desktop.core.AccountStore
import com.greenart7c3.nostrsigner.desktop.core.AccountsStore
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.AppDirs
import com.greenart7c3.nostrsigner.desktop.core.AppRecord
import com.greenart7c3.nostrsigner.desktop.core.AppWithPermissions
import com.greenart7c3.nostrsigner.desktop.core.DesktopKeyStore
import com.greenart7c3.nostrsigner.desktop.core.PassphraseLock
import com.greenart7c3.nostrsigner.desktop.core.PasswordStore
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import java.io.File
import java.nio.ByteBuffer
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/** In-memory stand-in for the OS credential store holding the second unlock factor. */
private class MemorySecretStore(var value: String? = null) : PasswordStore {
    var available = true

    /** When set, only this many more reads succeed; later ones fail like a stumbling keyring. */
    var failLoadsAfter: Int? = null

    /** When set, the next read signals [loading] and waits for [release]: holds an unlock mid-way. */
    var pause: Pair<java.util.concurrent.CountDownLatch, java.util.concurrent.CountDownLatch>? = null
    override val description: String = "test keyring"

    override fun load(): String? {
        pause?.let { (loading, release) ->
            pause = null
            loading.countDown()
            release.await(10, java.util.concurrent.TimeUnit.SECONDS)
        }
        if (!available) return null
        failLoadsAfter?.let { remaining ->
            if (remaining <= 0) return null
            failLoadsAfter = remaining - 1
        }
        return value
    }

    override fun store(secret: String): Boolean {
        if (!available) return false
        value = secret
        return true
    }

    override fun delete() {
        value = null
    }
}

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
        private val keyring = MemorySecretStore()

        @JvmStatic
        @BeforeClass
        fun isolateDataDir() {
            val tmp = File.createTempFile("amber-lock-test", "").apply {
                delete()
                mkdirs()
                deleteOnExit()
            }
            System.setProperty("user.home", tmp.absolutePath)
            PassphraseLock.osSecretStore = keyring
            PassphraseLock.targetKdfParams = fastKdf
        }

        /** The pre-v2 ciphertext format (no context binding), to fabricate old installs. */
        private fun encryptUnbound(key: SecretKey, plainText: String): String {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteBuffer.allocate(cipher.iv.size + cipherText.size).put(cipher.iv).put(cipherText)
            return Base64.getEncoder().withoutPadding().encodeToString(combined.array())
        }

        private fun blobJson(): String = File(AppDirs.dataDir, "master.key.enc").readText()

        private fun quarantined(file: File): List<File> = file.parentFile.listFiles { f -> f.name.startsWith("${file.name}.corrupt-") }?.toList().orEmpty()
    }

    @Test
    fun fullLifecycle() = runBlocking {
        // Plain mode (pre-setup): keys and the account database are plaintext.
        val secret = "super-secret-private-key"
        val cipher1 = DesktopKeyStore.encrypt(secret, "test/secret")
        assertEquals(secret, DesktopKeyStore.decrypt(cipher1, "test/secret"))
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
        assertEquals(secret, DesktopKeyStore.decrypt(cipher1, "test/secret"))

        // The unprotected copies are gone.
        val dataDir = com.greenart7c3.nostrsigner.desktop.core.AppDirs.dataDir
        assertFalse(File(dataDir, "amber.keystore").exists())
        assertFalse(File(dataDir, "keystore.pass").exists())
        assertTrue(File(dataDir, "master.key.enc").exists())
        assertTrue("the keyring secret is a second factor", blobJson().contains("\"osSecret\":true"))
        assertTrue("the keyring holds the secret", keyring.value != null)

        val encrypted = appsFile.readText()
        assertTrue("encrypted file should carry the marker header", encrypted.startsWith("AMBERENC2:"))
        assertFalse("plaintext app name must not survive in the encrypted file", encrypted.contains(marker))

        // A fresh reader (simulating a restart) decrypts it back.
        val reloaded = AccountStore(account.npub)
        assertEquals(marker, reloaded.apps.value.first().app.name)

        // Locking evicts the key: crypto operations fail, and the key bytes
        // themselves are wiped, not just dereferenced.
        val loaded = AmberDesktop.account(account.npub)!!
        val liveMasterKey = DesktopKeyStore.masterKeyForWrapping()
        PassphraseLock.lock()
        assertTrue("account key must be zeroed", loaded.signer.keyPair.privKey!!.all { it == 0.toByte() })
        assertTrue("master key must be destroyed", liveMasterKey.isDestroyed)
        assertEquals(PassphraseLock.Status.LOCKED, PassphraseLock.state.value)
        assertFalse(DesktopKeyStore.isMasterKeyAvailable())
        assertThrows(DesktopKeyStore.LockedException::class.java) {
            runBlocking { DesktopKeyStore.decrypt(cipher1, "test/secret") }
        }

        // verify() checks the passphrase without unlocking anything.
        assertTrue(PassphraseLock.verify("correct horse battery staple".toCharArray()))
        assertFalse(PassphraseLock.verify("wrong passphrase".toCharArray()))
        assertEquals(PassphraseLock.Status.LOCKED, PassphraseLock.state.value)
        assertThrows(DesktopKeyStore.LockedException::class.java) {
            runBlocking { DesktopKeyStore.decrypt(cipher1, "test/secret") }
        }

        // Wrong passphrase is rejected, right one restores access.
        assertFalse(PassphraseLock.unlock("wrong passphrase".toCharArray()))
        assertEquals(PassphraseLock.Status.LOCKED, PassphraseLock.state.value)
        assertTrue(PassphraseLock.unlock("correct horse battery staple".toCharArray()))
        assertEquals(secret, DesktopKeyStore.decrypt(cipher1, "test/secret"))
        assertEquals(marker, AmberDesktop.store(account.npub).apps.value.first().app.name)
        assertTrue("db must stay encrypted while a passphrase is set", appsFile.readText().startsWith("AMBERENC2:"))

        // Changing the passphrase requires the old one and keeps the data.
        assertFalse(PassphraseLock.changePassphrase("nope".toCharArray(), "new passphrase 42".toCharArray(), fastKdf))
        assertTrue(PassphraseLock.changePassphrase("correct horse battery staple".toCharArray(), "new passphrase 42".toCharArray(), fastKdf))
        PassphraseLock.lock()
        assertFalse(PassphraseLock.unlock("correct horse battery staple".toCharArray()))
        assertTrue(PassphraseLock.unlock("new passphrase 42".toCharArray()))
        assertEquals(secret, DesktopKeyStore.decrypt(cipher1, "test/secret"))
        val pass = "new passphrase 42"

        // Without the keyring secret the data dir and passphrase are not
        // enough, and the failure is reported as such (not "wrong passphrase").
        PassphraseLock.lock()
        keyring.available = false
        assertThrows(PassphraseLock.OsSecretUnavailableException::class.java) {
            runBlocking { PassphraseLock.unlock(pass.toCharArray()) }
        }
        assertEquals(PassphraseLock.Status.LOCKED, PassphraseLock.state.value)
        keyring.available = true

        // The header is authenticated: dropping the second-factor flag breaks the blob.
        val blobFile = File(dataDir, "master.key.enc")
        val goodBlob = blobFile.readText()
        blobFile.writeText(goodBlob.replace("\"osSecret\":true", "\"osSecret\":false"))
        assertFalse(PassphraseLock.unlock(pass.toCharArray()))
        blobFile.writeText(goodBlob)
        assertTrue(PassphraseLock.unlock(pass.toCharArray()))

        // A second account to swap data with.
        val other = AccountManager.addAccount(KeyPair(), name = "other")
        val otherAppsFile = File(AppDirs.accountDir(other.npub), "applications.json")
        AmberDesktop.store(other.npub).upsert(AppWithPermissions(app = AppRecord(key = "app-key-2", name = "other-app", pubKey = other.hexKey)))

        // Swapping one account's encrypted database into another is rejected,
        // and the rejected file is kept aside rather than overwritten.
        otherAppsFile.writeText(appsFile.readText())
        assertTrue(AccountStore(other.npub).apps.value.isEmpty())
        assertFalse(otherAppsFile.exists())
        assertEquals(1, quarantined(otherAppsFile).size)

        // A planted plaintext database is rejected too.
        otherAppsFile.writeText("""[{"app":{"key":"evil","name":"planted","pubKey":"${other.hexKey}"},"permissions":[]}]""")
        assertTrue(AccountStore(other.npub).apps.value.isEmpty())

        // Swapping account keys between records is rejected.
        val records = AccountsStore.accounts.value
        val mine = records.first { it.npub == account.npub }
        val theirs = records.first { it.npub == other.npub }
        AccountsStore.upsert(theirs.copy(encryptedPrivKey = mine.encryptedPrivKey))
        assertNull(AccountManager.loadAccount(other.npub))
        AccountsStore.upsert(theirs)
        assertEquals(other.npub, AccountManager.loadAccount(other.npub)?.npub)

        // An old-format (unbound) key copy is refused outright.
        val masterKey: SecretKey = SecretKeySpec(DesktopKeyStore.masterKeyForWrapping().encoded, "AES")
        val privHex = AccountManager.loadAccount(account.npub)!!.signer.keyPair.privKey!!.toHexKey()
        AccountsStore.upsert(AccountsStore.get(account.npub)!!.copy(encryptedPrivKey = encryptUnbound(masterKey, privHex)))
        assertNull(AccountManager.loadAccount(account.npub))
        AccountsStore.upsert(AccountsStore.get(account.npub)!!.copy(encryptedPrivKey = DesktopKeyStore.encrypt(privHex, "${account.npub}/privkey")))
        assertEquals(account.npub, AccountManager.loadAccount(account.npub)?.npub)

        // A legacy AMBERENC1 database is refused and quarantined.
        appsFile.writeText(
            "AMBERENC1:" + encryptUnbound(
                masterKey,
                """[{"app":{"key":"app-key-1","name":"$marker","pubKey":"${account.hexKey}"},"permissions":[]}]""",
            ),
        )
        assertTrue(AccountStore(account.npub).apps.value.isEmpty())
        assertFalse(appsFile.exists())
        assertEquals(1, quarantined(appsFile).size)

        // A blob cheaper than the current Argon2id target is rehashed at unlock.
        try {
            PassphraseLock.targetKdfParams = PassphraseLock.KdfParams(memoryKb = 2048, iterations = 2, parallelism = 1)
            PassphraseLock.lock()
            assertTrue(PassphraseLock.unlock(pass.toCharArray()))
            assertTrue("rehashed to the target cost", blobJson().contains("\"memoryKb\":2048"))
            PassphraseLock.lock()
            assertTrue(PassphraseLock.unlock(pass.toCharArray()))
        } finally {
            PassphraseLock.targetKdfParams = fastKdf
        }
        assertEquals(secret, DesktopKeyStore.decrypt(cipher1, "test/secret"))

        // The keyring stumbling right after it unwrapped the blob (one good
        // read, then failures) must neither drop the second factor nor replace
        // the secret the current blob depends on: a rewrap reuses that secret.
        val keyringSecret = keyring.value
        keyring.failLoadsAfter = 1
        try {
            assertTrue(PassphraseLock.changePassphrase(pass.toCharArray(), "flaky keyring 77".toCharArray(), fastKdf))
        } finally {
            keyring.failLoadsAfter = null
        }
        assertTrue("the second factor is kept", blobJson().contains("\"osSecret\":true"))
        assertEquals("the stored secret is not replaced", keyringSecret, keyring.value)
        PassphraseLock.lock()
        assertTrue(PassphraseLock.unlock("flaky keyring 77".toCharArray()))

        // Same for the Argon2id rehash that runs during unlock.
        try {
            PassphraseLock.targetKdfParams = PassphraseLock.KdfParams(memoryKb = 4096, iterations = 2, parallelism = 1)
            PassphraseLock.lock()
            keyring.failLoadsAfter = 1
            assertTrue(PassphraseLock.unlock("flaky keyring 77".toCharArray()))
        } finally {
            keyring.failLoadsAfter = null
            PassphraseLock.targetKdfParams = fastKdf
        }
        assertTrue("rehashed to the target cost", blobJson().contains("\"memoryKb\":4096"))
        assertTrue("the second factor is kept", blobJson().contains("\"osSecret\":true"))
        assertEquals("the stored secret is not replaced", keyringSecret, keyring.value)
        PassphraseLock.lock()
        assertTrue(PassphraseLock.unlock("flaky keyring 77".toCharArray()))

        // Back to the passphrase the other tests in this class use.
        assertTrue(PassphraseLock.changePassphrase("flaky keyring 77".toCharArray(), pass.toCharArray(), fastKdf))
        assertEquals(secret, DesktopKeyStore.decrypt(cipher1, "test/secret"))

        // A lock (e.g. the auto-lock timer) arriving while an unlock is mid-way
        // waits for it instead of interleaving: it used to run straight through,
        // and the paused unlock then reinstalled the key and set UNLOCKED.
        PassphraseLock.lock()
        val loading = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        keyring.pause = loading to release
        val unlocking = kotlin.concurrent.thread { runBlocking { PassphraseLock.unlock(pass.toCharArray()) } }
        assertTrue(loading.await(10, java.util.concurrent.TimeUnit.SECONDS))
        val locking = kotlin.concurrent.thread { PassphraseLock.lock() }
        Thread.sleep(200)
        release.countDown()
        unlocking.join(10_000)
        locking.join(10_000)
        assertEquals(PassphraseLock.Status.LOCKED, PassphraseLock.state.value)
        assertFalse("the lock that came last wins: no key left behind", DesktopKeyStore.isMasterKeyAvailable())
        assertTrue(PassphraseLock.unlock(pass.toCharArray()))
    }

    @Test
    fun `a rolled back v1 blob is refused instead of blessing planted data`() = runBlocking {
        // Continues the state from fullLifecycle: v2 blob, bound stores,
        // passphrase "new passphrase 42".
        val record = AccountsStore.accounts.value.first()
        assertTrue(blobJson().contains("\"version\":2"))

        PassphraseLock.lock()
        // Roll the blob back to the pre-release v1 format and plant a
        // plaintext application database next to it.
        val masterKey: SecretKey = SecretKeySpec(DesktopKeyStore.masterKeyForWrapping().encoded, "AES")
        val goodBlob = File(AppDirs.dataDir, "master.key.enc").readText()
        AmberDesktop.evictAccount(record.npub)
        File(AppDirs.dataDir, "master.key.enc").writeText(
            """{"version":1,"salt":"AAAA","memoryKb":1024,"iterations":1,"parallelism":1,"iv":"AAAA","cipherText":"AAAA","osSecret":false}""",
        )
        val appsFile = File(AppDirs.accountDir(record.npub), "applications.json")
        appsFile.writeText(
            """[{"app":{"key":"evil","name":"planted-f17","pubKey":"${record.npub}"},"permissions":[]}]""",
        )

        // The unsupported blob is refused before anything is decrypted, so
        // the planted file is never read, let alone blessed. The only way
        // back is restoring a supported blob.
        assertFalse(PassphraseLock.unlock("new passphrase 42".toCharArray()))
        assertEquals(PassphraseLock.Status.LOCKED, PassphraseLock.state.value)

        // A store read under the rolled-back blob refuses and quarantines the
        // planted file too.
        val reloaded = AccountStore(record.npub)
        assertTrue(reloaded.apps.value.isEmpty())
        assertFalse(appsFile.exists())
        assertTrue(quarantined(appsFile).isNotEmpty())

        // Restore the supported blob: later test classes share this data dir.
        File(AppDirs.dataDir, "master.key.enc").writeText(goodBlob)
        assertTrue(PassphraseLock.unlock("new passphrase 42".toCharArray()))
    }

    @Test
    fun kdfDefaultsScaleWithTheHeapButNeverBelowTheOldCost() {
        val gb = 1024L * 1024 * 1024
        assertEquals(262144, PassphraseLock.KdfParams.recommended(8 * gb).memoryKb)
        assertEquals(131072, PassphraseLock.KdfParams.recommended(gb * 3 / 4).memoryKb)
        assertEquals(65536, PassphraseLock.KdfParams.recommended(gb / 4).memoryKb)
        val old = PassphraseLock.KdfParams(memoryKb = 65536, iterations = 3, parallelism = 2)
        assertTrue(old.isWeakerThan(PassphraseLock.KdfParams()))
        assertFalse(PassphraseLock.KdfParams().isWeakerThan(old))
    }

    @Test
    fun unreadableAccountsFileIsKeptAndRestoredFromBackup() {
        val file = File(AppDirs.dataDir, "accounts.json")
        val backup = File(AppDirs.dataDir, "accounts.json.bak")
        val before = AccountsStore.accounts.value
        AccountsStore.upsert(AccountRecord(npub = "npub1backuptest", name = "a"))
        AccountsStore.upsert(AccountRecord(npub = "npub1backuptest", name = "b"))
        assertTrue(backup.exists())
        try {
            file.writeText("{ torn write")
            // A restart re-reads the file; the corrupt one is kept, the backup restored.
            AccountsStore.reload()
            assertTrue(AccountsStore.get("npub1backuptest") != null)
            assertEquals(1, quarantined(file).size)
            assertTrue(file.readText().contains("npub1backuptest"))
        } finally {
            AccountsStore.delete("npub1backuptest")
            assertEquals(before.map { it.npub }, AccountsStore.accounts.value.map { it.npub })
        }
    }

    @Test
    fun interruptedEnableCleanupRemovesLeftoverUnprotectedCopies() {
        // Simulate a crash between writeBlob() and removeUnprotectedCopies():
        // the wrapped blob exists AND an unprotected master-key copy is still
        // on disk. The startup cleanup must delete the unprotected copies and
        // keep the blob. Safe in any method order: it only fabricates state
        // that was not there and restores it afterwards.
        val dataDir = AppDirs.dataDir
        val blob = File(dataDir, "master.key.enc")
        val blobExisted = blob.exists()
        if (!blobExisted) blob.writeText("{}")
        val keystore = File(dataDir, "amber.keystore")
        val passFile = File(dataDir, "keystore.pass")
        try {
            keystore.writeText("leftover-keystore")
            passFile.writeText("leftover-password")
            DesktopKeyStore.cleanupUnprotectedCopiesIfWrapped()
            assertFalse("leftover keystore must be deleted", keystore.exists())
            assertFalse("leftover password file must be deleted", passFile.exists())
            assertTrue("the wrapped blob must survive the cleanup", blob.exists())
        } finally {
            if (!blobExisted) {
                blob.delete()
                // The singleton may have initialized as LOCKED while the
                // fabricated blob existed; restore the fresh-install state.
                PassphraseLock.state.compareAndSet(PassphraseLock.Status.LOCKED, PassphraseLock.Status.DISABLED)
            }
        }
    }
}
