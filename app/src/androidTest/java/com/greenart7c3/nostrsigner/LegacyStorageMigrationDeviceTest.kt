package com.greenart7c3.nostrsigner

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [LegacyStorageMigration] against real `EncryptedSharedPreferences` files and
 * the real AndroidKeyStore: the accounts of a pre-3.4.0 install come back, the
 * private keys only reach the encrypted DataStore, and the legacy files are gone
 * once every account opens again.
 *
 * The app wide account list is emptied for the duration of the test and restored
 * afterwards, so it starts from the state of an install that just updated instead
 * of depending on the accounts of the device it runs on.
 */
@RunWith(AndroidJUnit4::class)
class LegacyStorageMigrationDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val privateKeys = listOf(RandomInstance.bytes(32), RandomInstance.bytes(32))
    private val keyPairs = privateKeys.map { KeyPair(privKey = it) }
    private val npubs = keyPairs.map { it.pubKey.toNpub() }
    private val seedWords = "twelve words of a mnemonic"
    private var savedAccounts: String? = null
    private var currentAccount: String? = null
    private var pin: String? = null

    @Before
    fun setUp() = runBlocking {
        val settings = LocalPreferences.appPreferences(context)
        savedAccounts = settings.getString(SAVED_ACCOUNTS, null)
        currentAccount = settings.getString(CURRENT_ACCOUNT, null)
        settings.edit {
            remove(SAVED_ACCOUNTS)
            remove(CURRENT_ACCOUNT)
        }
        LocalPreferences.invalidateAccountCaches()
        pin = DataStoreAccess.loadPin(context)
        DataStoreAccess.savePin(context, null)
    }

    @After
    fun tearDown() = runBlocking {
        npubs.forEach { npub ->
            LocalPreferences.accountPreferences(context, npub).edit(commit = true) { clear() }
            File(prefsDirectory(), "prefs_$npub.xml").delete()
            DataStoreAccess.clearCacheForNpub(context, npub)
        }
        LocalPreferences.appPreferences(context).edit {
            remove("use_proxy")
            remove("proxy_port")
            putString(SAVED_ACCOUNTS, savedAccounts)
            putString(CURRENT_ACCOUNT, currentAccount)
        }
        legacyFiles().forEach { it.delete() }
        DataStoreAccess.savePin(context, pin)
        LocalPreferences.invalidateAccountCaches()
    }

    @Test
    fun migratesAccountsKeysAndSettings() = runBlocking {
        writeLegacySettings(
            SAVED_ACCOUNTS to npubs.joinToString(","),
            CURRENT_ACCOUNT to npubs[0],
            "pin" to "synthetic-pin-1234",
        )
        npubs.forEachIndexed { index, npub -> writeLegacyAccount(npub, keyPairs[index], privateKeys[index]) }
        assertTrue(LegacyStorageMigration.needed(context))

        LegacyStorageMigration.run(context)

        LocalPreferences.invalidateAccountCaches()
        assertEquals(npubs.toSet(), LocalPreferences.allSavedAccounts(context).map { it.npub }.toSet())
        assertEquals(npubs[0], LocalPreferences.currentAccount(context))

        val account = LocalPreferences.loadFromEncryptedStorage(context, npubs[0])
        assertNotNull(account)
        assertEquals(keyPairs[0].pubKey.toHexKey(), account?.hexKey)
        assertEquals(privateKeys[0].toHexKey(), DataStoreAccess.getEncryptedKey(context, npubs[0], DataStoreAccess.NOSTR_PRIVKEY))
        assertEquals(seedWords, DataStoreAccess.getEncryptedKey(context, npubs[0], DataStoreAccess.SEED_WORDS))

        val userPrefs = LocalPreferences.accountPreferences(context, npubs[0])
        assertEquals(keyPairs[0].pubKey.toHexKey(), userPrefs.getString("nostr_pubkey", null))
        assertEquals("Synthetic", userPrefs.getString("account_name", null))
        assertFalse(userPrefs.contains("nostr_privkey"))
        assertFalse(userPrefs.contains("seed_words"))

        assertEquals("synthetic-pin-1234", DataStoreAccess.loadPin(context))
        assertFalse("the PIN never reaches the plain preference file", LocalPreferences.appPreferences(context).contains("pin"))
        assertFalse("the legacy files are gone once the accounts open", LegacyStorageMigration.needed(context))
    }

    @Test
    fun anAccountWithoutAKeyKeepsEveryLegacyFile() = runBlocking {
        writeLegacySettings(SAVED_ACCOUNTS to npubs.joinToString(","))
        writeLegacyAccount(npubs[0], keyPairs[0], privateKeys[0])
        // The second account file exists but holds no key, so it cannot be restored.
        writeLegacyAccount(npubs[1], keyPairs[1], privateKeys[1], withKey = false)

        LegacyStorageMigration.run(context)

        assertTrue("the failed account keeps its file", legacyFile(npubs[1]).exists())
        assertTrue("the settings file is kept while one account is missing", settingsFile().exists())
        assertFalse("the restored account's file is gone", legacyFile(npubs[0]).exists())
    }

    private fun legacyPreferences(name: String): SharedPreferences {
        @Suppress("DEPRECATION")
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        @Suppress("DEPRECATION")
        return EncryptedSharedPreferences.create(
            context,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun writeLegacySettings(vararg values: Pair<String, String>) {
        legacyPreferences("secret_keeper").edit().apply {
            values.forEach { (key, value) -> putString(key, value) }
        }.commit()
    }

    private fun writeLegacyAccount(npub: String, keyPair: KeyPair, privateKey: ByteArray, withKey: Boolean = true) {
        legacyPreferences("secret_keeper_$npub").edit().apply {
            putString("nostr_pubkey", keyPair.pubKey.toHexKey())
            putString("account_name", "Synthetic")
            if (withKey) {
                putString("nostr_privkey", privateKey.toHexKey())
                putString("seed_words", seedWords)
            }
        }.commit()
    }

    private fun prefsDirectory(): File = File("${context.filesDir.parent}/shared_prefs/")

    private fun legacyFile(npub: String) = File(prefsDirectory(), "secret_keeper_$npub.xml")

    private fun settingsFile() = File(prefsDirectory(), "secret_keeper.xml")

    private fun legacyFiles(): List<File> = prefsDirectory().listFiles()?.filter { it.name.startsWith("secret_keeper") }.orEmpty()

    private companion object {
        const val SAVED_ACCOUNTS = "all_saved_accounts"
        const val CURRENT_ACCOUNT = "currently_logged_in_account"
    }
}
