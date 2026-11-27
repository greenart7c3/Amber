package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AccountRecord
import com.greenart7c3.nostrsigner.desktop.core.AccountStore
import com.greenart7c3.nostrsigner.desktop.core.AccountsStore
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.AppDirs
import com.greenart7c3.nostrsigner.desktop.core.AppRecord
import com.greenart7c3.nostrsigner.desktop.core.AppWithPermissions
import com.greenart7c3.nostrsigner.desktop.core.CrashReporter
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.DesktopKeyStore
import com.greenart7c3.nostrsigner.desktop.core.DesktopMasterKey
import com.greenart7c3.nostrsigner.desktop.core.PassphraseLock
import com.greenart7c3.nostrsigner.desktop.core.PasswordStore
import com.greenart7c3.nostrsigner.desktop.core.StoreGenerations
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

private class MemorySecretStore(var value: String? = null) : PasswordStore {
    var available = true

    var successfulLoadsBeforeFailing: Int? = null

    var pauseNextLoadUntilReleased: Pair<java.util.concurrent.CountDownLatch, java.util.concurrent.CountDownLatch>? = null
    override val description: String = "test keyring"

    override fun load(): String? {
        pauseNextLoadUntilReleased?.let { (loading, release) ->
            pauseNextLoadUntilReleased = null
            loading.countDown()
            release.await(10, java.util.concurrent.TimeUnit.SECONDS)
        }
        if (!available) return null
        successfulLoadsBeforeFailing?.let { remaining ->
            if (remaining <= 0) return null
            successfulLoadsBeforeFailing = remaining - 1
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
        private const val SECRET = "super-secret-private-key"
        private const val MARKER = "SECRET_APP_NAME_9f3a"
        private const val PASS = "new passphrase 42"
        private const val FLAKY_KEYRING_PASS = "flaky keyring 77"
        private val fastKdf = PassphraseLock.KdfParams(memoryKb = 1024, iterations = 1, parallelism = 1)
        private val keyring = MemorySecretStore()
        private val generations = HashMap<String, MemorySecretStore>()

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
            StoreGenerations.storeFor = { context -> generations.getOrPut(context) { MemorySecretStore() } }
            PassphraseLock.targetKdfParams = fastKdf
        }

        private fun encryptInPreV2FormatWithoutContextBinding(key: SecretKey, plainText: String): String {
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
        val cipher1 = DesktopKeyStore.encrypt(SECRET, "test/secret")
        val account = plainModeKeepsDataReadable(cipher1)
        val appsFile = File(AppDirs.accountDir(account.npub), "applications.json")
        setupWrapsTheSameMasterKeySoOldCiphertextsStillDecrypt(cipher1)
        setupRemovesTheUnprotectedMasterKeyCopies()
        setupEncryptsTheAccountDatabaseAtRest(account.npub, appsFile)
        lockEvictsAndWipesTheKeys(cipher1, account.npub)
        verifyChecksThePassphraseWithoutUnlocking(cipher1)
        unlockRejectsAWrongPassphraseAndTheRightOneRestoresAccess(cipher1, account.npub, appsFile)
        changingThePassphraseNeedsTheOldOneAndKeepsTheData(cipher1)
        unlockWithoutTheKeyringSecretReportsItMissingNotAWrongPassphrase()
        droppingTheSecondFactorFlagFromTheAuthenticatedHeaderBreaksTheBlob()
        val other = addSecondAccountToSwapDataWith()
        storesRefuseADatabaseSwappedInFromAnotherAccountAndQuarantineIt(appsFile, other)
        storesRefuseAPlantedPlaintextDatabase(other)
        storesRefuseRolledBackData(other)
        accountKeysAreBoundToTheirRecords(account, other, appsFile)
        weakKdfIsRehashedAtUnlock(cipher1)
        flakyKeyringKeepsTheSecondFactor(cipher1)
        aLockArrivingMidUnlockWaitsForItInsteadOfInterleaving()
        crashReportsStayUnreadableOnDisk()
    }

    private suspend fun plainModeKeepsDataReadable(cipher1: String): DesktopAccount {
        assertEquals(SECRET, DesktopKeyStore.decrypt(cipher1, "test/secret"))
        assertEquals(PassphraseLock.Status.DISABLED, PassphraseLock.state.value)
        assertFalse(PassphraseLock.isEnabled())

        val account = AccountManager.addAccount(KeyPair(), name = "db")
        val store = AmberDesktop.store(account.npub)
        store.upsert(AppWithPermissions(app = AppRecord(key = "app-key-1", name = MARKER, pubKey = account.hexKey)))
        store.addLog("wss://relay.example.com", "bunker", "sensitive-log-line")
        val appsFile = File(AppDirs.accountDir(account.npub), "applications.json")
        assertTrue("app db should be plaintext without a passphrase", appsFile.readText().contains(MARKER))
        return account
    }

    private suspend fun setupWrapsTheSameMasterKeySoOldCiphertextsStillDecrypt(cipher1: String) {
        PassphraseLock.enable("correct horse battery staple".toCharArray(), fastKdf)
        assertTrue(PassphraseLock.isEnabled())
        assertEquals(PassphraseLock.Status.UNLOCKED, PassphraseLock.state.value)
        assertEquals("a ciphertext from before setup still decrypts", SECRET, DesktopKeyStore.decrypt(cipher1, "test/secret"))
    }

    private fun setupRemovesTheUnprotectedMasterKeyCopies() {
        val dataDir = AppDirs.dataDir
        assertFalse(File(dataDir, "amber.keystore").exists())
        assertFalse(File(dataDir, "keystore.pass").exists())
        assertTrue(File(dataDir, "master.key.enc").exists())
        assertTrue("the keyring secret is a second factor", blobJson().contains("\"osSecret\":true"))
        assertTrue("the keyring holds the secret", keyring.value != null)
    }

    private fun setupEncryptsTheAccountDatabaseAtRest(npub: String, appsFile: File) {
        val encrypted = appsFile.readText()
        assertTrue("encrypted file should carry the marker header", encrypted.startsWith("AMBERENC3:"))
        assertFalse("plaintext app name must not survive in the encrypted file", encrypted.contains(MARKER))

        val readerAfterRestart = AccountStore(npub)
        assertEquals(MARKER, readerAfterRestart.apps.value.first().app.name)
    }

    private suspend fun lockEvictsAndWipesTheKeys(cipher1: String, npub: String) {
        val loaded = AmberDesktop.account(npub)!!
        val liveMasterKey = DesktopMasterKey.masterKeyForWrapping()
        PassphraseLock.lock()
        assertTrue("account key must be zeroed", loaded.signer.keyPair.privKey!!.all { it == 0.toByte() })
        assertTrue("master key must be destroyed", liveMasterKey.isDestroyed)
        assertEquals(PassphraseLock.Status.LOCKED, PassphraseLock.state.value)
        assertFalse(DesktopMasterKey.isMasterKeyAvailable())
        assertThrows(DesktopKeyStore.LockedException::class.java) {
            runBlocking { DesktopKeyStore.decrypt(cipher1, "test/secret") }
        }
    }

    private suspend fun verifyChecksThePassphraseWithoutUnlocking(cipher1: String) {
        assertTrue(PassphraseLock.verify("correct horse battery staple".toCharArray()))
        assertFalse(PassphraseLock.verify("wrong passphrase".toCharArray()))
        assertEquals(PassphraseLock.Status.LOCKED, PassphraseLock.state.value)
        assertThrows(DesktopKeyStore.LockedException::class.java) {
            runBlocking { DesktopKeyStore.decrypt(cipher1, "test/secret") }
        }
    }

    private suspend fun unlockRejectsAWrongPassphraseAndTheRightOneRestoresAccess(cipher1: String, npub: String, appsFile: File) {
        assertFalse(PassphraseLock.unlock("wrong passphrase".toCharArray()))
        assertEquals(PassphraseLock.Status.LOCKED, PassphraseLock.state.value)
        assertTrue(PassphraseLock.unlock("correct horse battery staple".toCharArray()))
        assertEquals(SECRET, DesktopKeyStore.decrypt(cipher1, "test/secret"))
        assertEquals(MARKER, AmberDesktop.store(npub).apps.value.first().app.name)
        assertTrue("db must stay encrypted while a passphrase is set", appsFile.readText().startsWith("AMBERENC3:"))
    }

    private suspend fun changingThePassphraseNeedsTheOldOneAndKeepsTheData(cipher1: String) {
        assertFalse(PassphraseLock.changePassphrase("nope".toCharArray(), PASS.toCharArray(), fastKdf))
        assertTrue(PassphraseLock.changePassphrase("correct horse battery staple".toCharArray(), PASS.toCharArray(), fastKdf))
        PassphraseLock.lock()
        assertFalse(PassphraseLock.unlock("correct horse battery staple".toCharArray()))
        assertTrue(PassphraseLock.unlock(PASS.toCharArray()))
        assertEquals(SECRET, DesktopKeyStore.decrypt(cipher1, "test/secret"))
    }

    private fun unlockWithoutTheKeyringSecretReportsItMissingNotAWrongPassphrase() {
        PassphraseLock.lock()
        keyring.available = false
        assertThrows(PassphraseLock.OsSecretUnavailableException::class.java) {
            runBlocking { PassphraseLock.unlock(PASS.toCharArray()) }
        }
        assertEquals("the data dir and passphrase alone do not unlock", PassphraseLock.Status.LOCKED, PassphraseLock.state.value)
        keyring.available = true
    }

    private suspend fun droppingTheSecondFactorFlagFromTheAuthenticatedHeaderBreaksTheBlob() {
        val blobFile = File(AppDirs.dataDir, "master.key.enc")
        val goodBlob = blobFile.readText()
        blobFile.writeText(goodBlob.replace("\"osSecret\":true", "\"osSecret\":false"))
        assertFalse(PassphraseLock.unlock(PASS.toCharArray()))
        blobFile.writeText(goodBlob)
        assertTrue(PassphraseLock.unlock(PASS.toCharArray()))
    }

    private suspend fun addSecondAccountToSwapDataWith(): DesktopAccount {
        val other = AccountManager.addAccount(KeyPair(), name = "other")
        AmberDesktop.store(other.npub).upsert(AppWithPermissions(app = AppRecord(key = "app-key-2", name = "other-app", pubKey = other.hexKey)))
        return other
    }

    private fun storesRefuseADatabaseSwappedInFromAnotherAccountAndQuarantineIt(appsFile: File, other: DesktopAccount) {
        val otherAppsFile = File(AppDirs.accountDir(other.npub), "applications.json")
        otherAppsFile.writeText(appsFile.readText())
        assertTrue(AccountStore(other.npub).apps.value.isEmpty())
        assertFalse(otherAppsFile.exists())
        assertEquals("the rejected file is kept aside, not overwritten", 1, quarantined(otherAppsFile).size)
    }

    private fun storesRefuseAPlantedPlaintextDatabase(other: DesktopAccount) {
        val otherAppsFile = File(AppDirs.accountDir(other.npub), "applications.json")
        otherAppsFile.writeText("""[{"app":{"key":"evil","name":"planted","pubKey":"${other.hexKey}"},"permissions":[]}]""")
        assertTrue(AccountStore(other.npub).apps.value.isEmpty())
    }

    private fun storesRefuseRolledBackData(other: DesktopAccount) {
        val otherAppsFile = File(AppDirs.accountDir(other.npub), "applications.json")
        val appsContext = "${other.npub}/applications.json"
        val (olderCopy, current) = copiesBeforeAndAfterRemovingAGrant(other, otherAppsFile)
        anOlderValidCopyIsRefusedSoARemovedConnectionDoesNotComeBack(other, otherAppsFile, olderCopy)
        raisingTheGenerationInTheHeaderBreaksTheBoundCiphertext(other, otherAppsFile, olderCopy)
        aCopyWithoutAGenerationHeaderIsRefused(other, otherAppsFile, appsContext)
        aFileOneGenerationAheadOfItsRecordIsAcceptedAndTheRecordCatchesUp(other, otherAppsFile, appsContext, current)
        withNoGenerationRecordedACopyWithoutAGenerationHeaderIsStillRefused(other, otherAppsFile, appsContext, current)
    }

    private fun copiesBeforeAndAfterRemovingAGrant(other: DesktopAccount, otherAppsFile: File): Pair<String, String> {
        val otherStore = AmberDesktop.store(other.npub)
        otherStore.upsert(AppWithPermissions(app = AppRecord(key = "revoked-later", name = "granted", pubKey = other.hexKey)))
        val olderCopy = otherAppsFile.readText()
        otherStore.delete("revoked-later")
        return olderCopy to otherAppsFile.readText()
    }

    private fun anOlderValidCopyIsRefusedSoARemovedConnectionDoesNotComeBack(other: DesktopAccount, otherAppsFile: File, olderCopy: String) {
        val rolledBackBefore = quarantined(otherAppsFile).size
        otherAppsFile.writeText(olderCopy)
        assertTrue("a rolled-back copy must not restore a removed connection", AccountStore(other.npub).apps.value.none { it.app.key == "revoked-later" })
        assertFalse(otherAppsFile.exists())
        assertEquals(rolledBackBefore + 1, quarantined(otherAppsFile).size)
    }

    private fun raisingTheGenerationInTheHeaderBreaksTheBoundCiphertext(other: DesktopAccount, otherAppsFile: File, olderCopy: String) {
        val olderGeneration = olderCopy.removePrefix("AMBERENC3:").substringBefore(':').toLong()
        otherAppsFile.writeText(olderCopy.replaceFirst("AMBERENC3:$olderGeneration:", "AMBERENC3:${olderGeneration + 100}:"))
        assertTrue(AccountStore(other.npub).apps.value.none { it.app.key == "revoked-later" })
        assertFalse(otherAppsFile.exists())
    }

    private fun aCopyWithoutAGenerationHeaderIsRefused(other: DesktopAccount, otherAppsFile: File, appsContext: String) {
        otherAppsFile.writeText(
            "AMBERENC2:" + DesktopKeyStore.encryptString("""[{"app":{"key":"revoked-later","name":"granted","pubKey":"${other.hexKey}"},"permissions":[]}]""", appsContext),
        )
        assertTrue(AccountStore(other.npub).apps.value.isEmpty())
        assertFalse(otherAppsFile.exists())
    }

    private fun aFileOneGenerationAheadOfItsRecordIsAcceptedAndTheRecordCatchesUp(other: DesktopAccount, otherAppsFile: File, appsContext: String, current: String) {
        otherAppsFile.writeText(current)
        val currentGeneration = current.removePrefix("AMBERENC3:").substringBefore(':').toLong()
        val recordLeftBehindByACrashBeforeItWasWritten = (currentGeneration - 1).toString()
        generations.getValue(appsContext).value = recordLeftBehindByACrashBeforeItWasWritten
        assertEquals(listOf("app-key-2"), AccountStore(other.npub).apps.value.map { it.app.key })
        assertEquals(currentGeneration.toString(), generations.getValue(appsContext).value)
    }

    private fun withNoGenerationRecordedACopyWithoutAGenerationHeaderIsStillRefused(other: DesktopAccount, otherAppsFile: File, appsContext: String, current: String) {
        val currentGeneration = current.removePrefix("AMBERENC3:").substringBefore(':').toLong()
        generations.getValue(appsContext).value = null
        otherAppsFile.writeText(
            "AMBERENC2:" + DesktopKeyStore.encryptString("""[{"app":{"key":"app-key-2","name":"other-app","pubKey":"${other.hexKey}"},"permissions":[]}]""", appsContext),
        )
        assertTrue("there is no older format to accept", AccountStore(other.npub).apps.value.isEmpty())
        assertFalse(otherAppsFile.exists())
        otherAppsFile.writeText(current)
        generations.getValue(appsContext).value = currentGeneration.toString()
    }

    private suspend fun accountKeysAreBoundToTheirRecords(account: DesktopAccount, other: DesktopAccount, appsFile: File) {
        swappingAccountKeysBetweenRecordsIsRejected(account, other)
        val masterKey: SecretKey = SecretKeySpec(DesktopMasterKey.masterKeyForWrapping().encoded, "AES")
        aKeyCopyWithoutContextBindingIsRefused(account, masterKey)
        aLegacyAmberenc1DatabaseIsRefusedAndQuarantined(account, masterKey, appsFile)
    }

    private suspend fun swappingAccountKeysBetweenRecordsIsRejected(account: DesktopAccount, other: DesktopAccount) {
        val records = AccountsStore.accounts.value
        val mine = records.first { it.npub == account.npub }
        val theirs = records.first { it.npub == other.npub }
        AccountsStore.upsert(theirs.copy(encryptedPrivKey = mine.encryptedPrivKey))
        assertNull(AccountManager.loadAccount(other.npub))
        AccountsStore.upsert(theirs)
        assertEquals(other.npub, AccountManager.loadAccount(other.npub)?.npub)
    }

    private suspend fun aKeyCopyWithoutContextBindingIsRefused(account: DesktopAccount, masterKey: SecretKey) {
        val privHex = AccountManager.loadAccount(account.npub)!!.signer.keyPair.privKey!!.toHexKey()
        AccountsStore.upsert(AccountsStore.get(account.npub)!!.copy(encryptedPrivKey = encryptInPreV2FormatWithoutContextBinding(masterKey, privHex)))
        assertNull(AccountManager.loadAccount(account.npub))
        AccountsStore.upsert(AccountsStore.get(account.npub)!!.copy(encryptedPrivKey = DesktopKeyStore.encrypt(privHex, "${account.npub}/privkey")))
        assertEquals(account.npub, AccountManager.loadAccount(account.npub)?.npub)
    }

    private fun aLegacyAmberenc1DatabaseIsRefusedAndQuarantined(account: DesktopAccount, masterKey: SecretKey, appsFile: File) {
        appsFile.writeText(
            "AMBERENC1:" + encryptInPreV2FormatWithoutContextBinding(
                masterKey,
                """[{"app":{"key":"app-key-1","name":"$MARKER","pubKey":"${account.hexKey}"},"permissions":[]}]""",
            ),
        )
        assertTrue(AccountStore(account.npub).apps.value.isEmpty())
        assertFalse(appsFile.exists())
        assertEquals(1, quarantined(appsFile).size)
    }

    private suspend fun weakKdfIsRehashedAtUnlock(cipher1: String) {
        try {
            PassphraseLock.targetKdfParams = PassphraseLock.KdfParams(memoryKb = 2048, iterations = 2, parallelism = 1)
            PassphraseLock.lock()
            assertTrue(PassphraseLock.unlock(PASS.toCharArray()))
            assertTrue("rehashed to the target cost", blobJson().contains("\"memoryKb\":2048"))
            PassphraseLock.lock()
            assertTrue(PassphraseLock.unlock(PASS.toCharArray()))
        } finally {
            PassphraseLock.targetKdfParams = fastKdf
        }
        assertEquals(SECRET, DesktopKeyStore.decrypt(cipher1, "test/secret"))
    }

    private suspend fun flakyKeyringKeepsTheSecondFactor(cipher1: String) {
        val keyringSecret = keyring.value
        aKeyringFailingAfterOneGoodReadDuringAPassphraseChangeKeepsTheSecondFactor(keyringSecret)
        aKeyringFailingAfterOneGoodReadDuringTheUnlockRehashKeepsTheSecondFactor(keyringSecret)
        assertTrue(PassphraseLock.changePassphrase(FLAKY_KEYRING_PASS.toCharArray(), PASS.toCharArray(), fastKdf))
        assertEquals(SECRET, DesktopKeyStore.decrypt(cipher1, "test/secret"))
    }

    private suspend fun aKeyringFailingAfterOneGoodReadDuringAPassphraseChangeKeepsTheSecondFactor(keyringSecret: String?) {
        keyring.successfulLoadsBeforeFailing = 1
        try {
            assertTrue(PassphraseLock.changePassphrase(PASS.toCharArray(), FLAKY_KEYRING_PASS.toCharArray(), fastKdf))
        } finally {
            keyring.successfulLoadsBeforeFailing = null
        }
        assertTrue("the second factor is kept", blobJson().contains("\"osSecret\":true"))
        assertEquals("the stored secret is not replaced: a rewrap reuses it", keyringSecret, keyring.value)
        PassphraseLock.lock()
        assertTrue(PassphraseLock.unlock(FLAKY_KEYRING_PASS.toCharArray()))
    }

    private suspend fun aKeyringFailingAfterOneGoodReadDuringTheUnlockRehashKeepsTheSecondFactor(keyringSecret: String?) {
        try {
            PassphraseLock.targetKdfParams = PassphraseLock.KdfParams(memoryKb = 4096, iterations = 2, parallelism = 1)
            PassphraseLock.lock()
            keyring.successfulLoadsBeforeFailing = 1
            assertTrue(PassphraseLock.unlock(FLAKY_KEYRING_PASS.toCharArray()))
        } finally {
            keyring.successfulLoadsBeforeFailing = null
            PassphraseLock.targetKdfParams = fastKdf
        }
        assertTrue("rehashed to the target cost", blobJson().contains("\"memoryKb\":4096"))
        assertTrue("the second factor is kept", blobJson().contains("\"osSecret\":true"))
        assertEquals("the stored secret is not replaced: a rewrap reuses it", keyringSecret, keyring.value)
        PassphraseLock.lock()
        assertTrue(PassphraseLock.unlock(FLAKY_KEYRING_PASS.toCharArray()))
    }

    private suspend fun aLockArrivingMidUnlockWaitsForItInsteadOfInterleaving() {
        PassphraseLock.lock()
        val loading = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        keyring.pauseNextLoadUntilReleased = loading to release
        val unlocking = kotlin.concurrent.thread { runBlocking { PassphraseLock.unlock(PASS.toCharArray()) } }
        assertTrue(loading.await(10, java.util.concurrent.TimeUnit.SECONDS))
        val locking = kotlin.concurrent.thread { PassphraseLock.lock() }
        Thread.sleep(200)
        release.countDown()
        unlocking.join(10_000)
        locking.join(10_000)
        assertEquals("a lock arriving mid-unlock must not be undone by that unlock", PassphraseLock.Status.LOCKED, PassphraseLock.state.value)
        assertFalse("the lock that came last wins: no key left behind", DesktopMasterKey.isMasterKeyAvailable())
        assertTrue(PassphraseLock.unlock(PASS.toCharArray()))
    }

    private suspend fun crashReportsStayUnreadableOnDisk() {
        val crashFile = File(AppDirs.dataDir, "crash.trace")
        try {
            anUnlockedCrashReportIsEncryptedAndReadableOnlyAfterUnlock(crashFile)
            aLockedCrashReportKeepsOnlyExceptionTypesAndFrames(crashFile)
        } finally {
            CrashReporter.dismiss()
            if (PassphraseLock.isLocked()) assertTrue(PassphraseLock.unlock(PASS.toCharArray()))
        }
    }

    private suspend fun anUnlockedCrashReportIsEncryptedAndReadableOnlyAfterUnlock(crashFile: File) {
        CrashReporter.save(IllegalStateException("crash-secret-1"))
        assertTrue(crashFile.readText().startsWith(CrashReporter.ENCRYPTED_HEADER))
        assertFalse("an exception message can quote request data", crashFile.readText().contains("crash-secret-1"))
        CrashReporter.pending.value = null
        PassphraseLock.lock()
        CrashReporter.loadAfterUnlock()
        assertNull(CrashReporter.pending.value)
        assertTrue("kept for the next unlock", crashFile.exists())
        assertTrue(PassphraseLock.unlock(PASS.toCharArray()))
        CrashReporter.loadAfterUnlock()
        assertTrue(CrashReporter.pending.value!!.contains("crash-secret-1"))
        CrashReporter.dismiss()
    }

    private fun aLockedCrashReportKeepsOnlyExceptionTypesAndFrames(crashFile: File) {
        PassphraseLock.lock()
        CrashReporter.save(IllegalStateException("crash-secret-2"))
        assertFalse("an exception message can quote request data", crashFile.readText().contains("crash-secret-2"))
        assertTrue(crashFile.readText().contains("java.lang.IllegalStateException"))
    }

    @Test
    fun `a rolled back v1 blob is refused instead of blessing planted data`() = runBlocking {
        val record = AccountsStore.accounts.value.first()
        assertTrue("expects the v2 blob and passphrase fullLifecycle leaves behind", blobJson().contains("\"version\":2"))

        PassphraseLock.lock()
        val supportedBlob = File(AppDirs.dataDir, "master.key.enc").readText()
        AmberDesktop.evictAccount(record.npub)
        val appsFile = rollBackToAPreReleaseV1BlobWithAPlantedPlaintextDatabase(record.npub)

        assertFalse("the unsupported blob is refused before anything is decrypted", PassphraseLock.unlock(PASS.toCharArray()))
        assertEquals(PassphraseLock.Status.LOCKED, PassphraseLock.state.value)

        val storeUnderTheRolledBackBlob = AccountStore(record.npub)
        assertTrue("the planted file is never blessed", storeUnderTheRolledBackBlob.apps.value.isEmpty())
        assertFalse(appsFile.exists())
        assertTrue(quarantined(appsFile).isNotEmpty())

        restoreTheSupportedBlobForTheOtherTestsSharingThisDataDir(supportedBlob)
    }

    private fun rollBackToAPreReleaseV1BlobWithAPlantedPlaintextDatabase(npub: String): File {
        File(AppDirs.dataDir, "master.key.enc").writeText(
            """{"version":1,"salt":"AAAA","memoryKb":1024,"iterations":1,"parallelism":1,"iv":"AAAA","cipherText":"AAAA","osSecret":false}""",
        )
        val appsFile = File(AppDirs.accountDir(npub), "applications.json")
        appsFile.writeText(
            """[{"app":{"key":"evil","name":"planted-f17","pubKey":"$npub"},"permissions":[]}]""",
        )
        return appsFile
    }

    private suspend fun restoreTheSupportedBlobForTheOtherTestsSharingThisDataDir(supportedBlob: String) {
        File(AppDirs.dataDir, "master.key.enc").writeText(supportedBlob)
        assertTrue(PassphraseLock.unlock(PASS.toCharArray()))
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
            AccountsStore.reload()
            assertTrue("the backup is restored on restart", AccountsStore.get("npub1backuptest") != null)
            assertEquals("the corrupt file is kept", 1, quarantined(file).size)
            assertTrue(file.readText().contains("npub1backuptest"))
        } finally {
            AccountsStore.delete("npub1backuptest")
            assertEquals(before.map { it.npub }, AccountsStore.accounts.value.map { it.npub })
        }
    }

    @Test
    fun `startup cleanup after an enable interrupted between writing the blob and removing the unprotected copies deletes the copies and keeps the blob`() {
        val dataDir = AppDirs.dataDir
        val blob = File(dataDir, "master.key.enc")
        val blobExisted = blob.exists()
        if (!blobExisted) blob.writeText("{}")
        val keystore = File(dataDir, "amber.keystore")
        val passFile = File(dataDir, "keystore.pass")
        try {
            keystore.writeText("leftover-keystore")
            passFile.writeText("leftover-password")
            DesktopMasterKey.cleanupUnprotectedCopiesIfWrapped()
            assertFalse("leftover keystore must be deleted", keystore.exists())
            assertFalse("leftover password file must be deleted", passFile.exists())
            assertTrue("the wrapped blob must survive the cleanup", blob.exists())
        } finally {
            if (!blobExisted) {
                blob.delete()
                undoTheLockedStateTheFabricatedBlobMayHaveInitialized()
            }
        }
    }

    private fun undoTheLockedStateTheFabricatedBlobMayHaveInitialized() {
        PassphraseLock.state.compareAndSet(PassphraseLock.Status.LOCKED, PassphraseLock.Status.DISABLED)
    }
}
