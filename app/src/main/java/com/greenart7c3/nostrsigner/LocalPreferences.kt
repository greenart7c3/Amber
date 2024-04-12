package com.greenart7c3.nostrsigner

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.LruCache
import androidx.compose.runtime.Immutable
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import fr.acinq.secp256k1.jni.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

// Release mode (!BuildConfig.DEBUG) always uses encrypted preferences
// To use plaintext SharedPreferences for debugging, set this to true
// It will only apply in Debug builds
private const val DEBUG_PLAINTEXT_PREFERENCES = false
private const val DEBUG_PREFERENCES_NAME = "debug_prefs"

private object PrefKeys {
    const val CURRENT_ACCOUNT = "currently_logged_in_account"
    const val SAVED_ACCOUNTS = "all_saved_accounts"
    const val NOSTR_PRIVKEY = "nostr_privkey"
    const val NOSTR_PUBKEY = "nostr_pubkey"
    const val REMEMBER_APPS = "remember_apps"
    const val ACCOUNT_NAME = "account_name"
    const val RATIONALE = "rationale"
}

@Immutable
data class AccountInfo(
    val npub: String,
    val hasPrivKey: Boolean
)

object LocalPreferences {
    private const val COMMA = ","
    private var currentAccount: String? = null
    private var accountCache = LruCache<String, Account>(10)

    fun allSavedAccounts(): List<AccountInfo> {
        return savedAccounts().map { npub ->
            AccountInfo(
                npub,
                true
            )
        }.toSet().toList()
    }

    fun currentAccount(): String? {
        if (currentAccount == null) {
            currentAccount = encryptedPreferences().getString(PrefKeys.CURRENT_ACCOUNT, null)
        }
        return currentAccount
    }

    private fun updateCurrentAccount(npub: String) {
        if (currentAccount != npub) {
            currentAccount = npub

            encryptedPreferences().edit().apply {
                putString(PrefKeys.CURRENT_ACCOUNT, npub)
            }.apply()
        }
    }

    fun shouldShowRationale(): Boolean? {
        if (!encryptedPreferences().contains(PrefKeys.RATIONALE)) {
            return null
        }
        return encryptedPreferences().getBoolean(PrefKeys.RATIONALE, true)
    }

    fun updateShoulShowRationale(value: Boolean) {
        encryptedPreferences().edit().apply {
            putBoolean(PrefKeys.RATIONALE, value)
        }.apply()
    }

    private var savedAccounts: List<String>? = null

    private fun savedAccounts(): List<String> {
        if (savedAccounts == null) {
            savedAccounts = encryptedPreferences()
                .getString(PrefKeys.SAVED_ACCOUNTS, null)?.split(COMMA) ?: listOf()
        }
        return savedAccounts!!
    }

    private fun updateSavedAccounts(accounts: List<String>) {
        if (savedAccounts != accounts) {
            savedAccounts = accounts

            encryptedPreferences().edit().apply {
                putString(PrefKeys.SAVED_ACCOUNTS, accounts.joinToString(COMMA).ifBlank { null })
            }.apply()
        }
    }

    private val prefsDirPath: String
        get() = "${nostrsigner.instance.filesDir.parent}/shared_prefs/"

    private fun addAccount(npub: String) {
        val accounts = savedAccounts().toMutableList()
        if (npub !in accounts) {
            accounts.add(npub)
            updateSavedAccounts(accounts)
        }
    }

    private fun setCurrentAccount(account: Account) {
        val npub = account.keyPair.pubKey.toNpub()
        updateCurrentAccount(npub)
        addAccount(npub)
    }

    fun switchToAccount(npub: String) {
        updateCurrentAccount(npub)
    }

    fun containsAccount(npub: String): Boolean {
        return savedAccounts().contains(npub)
    }

    /**
     * Removes the account from the app level shared preferences
     */
    private fun removeAccount(npub: String) {
        val accounts = savedAccounts().toMutableList()
        if (accounts.remove(npub)) {
            updateSavedAccounts(accounts)
        }
    }

    /**
     * Deletes the npub-specific shared preference file
     */
    private fun deleteUserPreferenceFile(npub: String) {
        val prefsDir = File(prefsDirPath)
        prefsDir.list()?.forEach {
            if (it.contains(npub)) {
                File(prefsDir, it).delete()
            }
        }
    }

    private fun encryptedPreferences(npub: String? = null): SharedPreferences {
        return if (BuildConfig.DEBUG && DEBUG_PLAINTEXT_PREFERENCES) {
            val preferenceFile = if (npub == null) DEBUG_PREFERENCES_NAME else "${DEBUG_PREFERENCES_NAME}_$npub"
            nostrsigner.instance.getSharedPreferences(preferenceFile, Context.MODE_PRIVATE)
        } else {
            return EncryptedStorage.preferences(npub)
        }
    }

    /**
     * Clears the preferences for a given npub, deletes the preferences xml file,
     * and switches the user to the first account in the list if it exists
     *
     * We need to use `commit()` to write changes to disk and release the file
     * lock so that it can be deleted. If we use `apply()` there is a race
     * condition and the file will probably not be deleted
     */
    @SuppressLint("ApplySharedPref")
    fun updatePrefsForLogout(npub: String) {
        val userPrefs = encryptedPreferences(npub)
        userPrefs.edit().clear().commit()
        removeAccount(npub)
        deleteUserPreferenceFile(npub)

        if (savedAccounts().isEmpty()) {
            val appPrefs = encryptedPreferences()
            appPrefs.edit().clear().apply()
        } else if (currentAccount() == npub) {
            updateCurrentAccount(savedAccounts().elementAt(0))
        }
    }

    fun updatePrefsForLogin(account: Account) {
        setCurrentAccount(account)
        saveToEncryptedStorage(account)
    }

    suspend fun deleteSavedApps(applications: List<ApplicationEntity>) = withContext(Dispatchers.IO) {
        applications.forEach {
            nostrsigner.instance.database.applicationDao().delete(it)
        }
    }

    fun saveToEncryptedStorage(account: Account) {
        val prefs = encryptedPreferences(account.keyPair.pubKey.toNpub())
        prefs.edit().apply {
            account.keyPair.privKey.let { putString(PrefKeys.NOSTR_PRIVKEY, it?.toHexKey()) }
            account.keyPair.pubKey.let { putString(PrefKeys.NOSTR_PUBKEY, it.toHexKey()) }
            putString(PrefKeys.ACCOUNT_NAME, account.name)
        }.apply()
    }

    fun loadFromEncryptedStorage(): Account? {
        return loadFromEncryptedStorage(currentAccount())
    }

    fun setAccountName(npub: String, value: String) {
        encryptedPreferences(npub).edit().apply {
            putString(PrefKeys.ACCOUNT_NAME, value)
        }.apply()
    }

    fun getAccountName(npub: String): String {
        encryptedPreferences(npub).apply {
            return getString(PrefKeys.ACCOUNT_NAME, "") ?: ""
        }
    }

    private suspend fun convertToDatabase(map: MutableMap<String, Boolean>, pubKey: String) = withContext(Dispatchers.IO) {
        map.forEach {
            val splitData = it.key.split("-")
            nostrsigner.instance.database.applicationDao().deletePermissions(splitData.first())
        }
        map.forEach {
            val splitData = it.key.split("-")
            nostrsigner.instance.database.applicationDao().insertApplication(
                ApplicationEntity(
                    splitData.first(),
                    "",
                    emptyList(),
                    "",
                    "",
                    "",
                    pubKey,
                    true,
                    ""
                )
            )
            nostrsigner.instance.database.applicationDao().insertPermissions(
                listOf(
                    ApplicationPermissionsEntity(
                        id = null,
                        pkKey = splitData.first(),
                        type = splitData[1],
                        kind = runCatching { splitData[2].toInt() }.getOrNull(),
                        acceptable = it.value
                    )
                )
            )
        }
    }

    fun loadFromEncryptedStorage(npub: String?): Account? {
        if (accountCache.get(npub) != null) {
            return accountCache.get(npub)
        }
        encryptedPreferences(npub).apply {
            val pubKey = getString(PrefKeys.NOSTR_PUBKEY, null) ?: return null
            val privKey = getString(PrefKeys.NOSTR_PRIVKEY, null)

            val jsonString = getString(PrefKeys.REMEMBER_APPS, null)
            val outputMap = mutableMapOf<String, Boolean>()
            if (jsonString != null) {
                val jsonObject = JSONObject(jsonString)
                val keysItr: Iterator<String> = jsonObject.keys()
                while (keysItr.hasNext()) {
                    val key = keysItr.next()
                    val value: Boolean = jsonObject.getBoolean(key)
                    outputMap[key] = value
                }
            }
            runBlocking { convertToDatabase(outputMap, pubKey) }
            this.edit().apply {
                remove(PrefKeys.REMEMBER_APPS)
            }.apply()
            val name = getString(PrefKeys.ACCOUNT_NAME, "") ?: ""
            val account = Account(
                keyPair = KeyPair(privKey = privKey?.hexToByteArray(), pubKey = pubKey.hexToByteArray()),
                name = name
            )
            accountCache.put(npub, account)
            return account
        }
    }
}
