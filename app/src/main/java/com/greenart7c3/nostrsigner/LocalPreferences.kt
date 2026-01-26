package com.greenart7c3.nostrsigner

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.models.defaultAppRelays
import com.greenart7c3.nostrsigner.models.defaultIndexerRelays
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.greenart7c3.nostrsigner.ui.parseBiometricsTimeType
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.cache.LargeCache
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

private enum class PrefKeys(val key: String) {
    NOSTR_PUBKEY("nostr_pubkey"),
    ACCOUNT_NAME("account_name"),
    SIGN_POLICY("default_sign_policy"),
    PROFILE_URL("profile_url"),
    LAST_METADATA_UPDATE("last_metadata_update"),
    LAST_CHECK("last_check"),
    DID_BACKUP("did_backup"),
}

private enum class SettingsKeys(val key: String) {
    DEFAULT_RELAYS("default_relays"),
    DEFAULT_PROFILE_RELAYS("default_profile_relays"),
    LAST_BIOMETRICS_TIME("last_biometrics_time"),
    USE_AUTH("use_auth"),
    BIOMETRICS_TYPE("biometrics_type"),
    USE_PIN("use_pin"),
    RATIONALE("rationale"),
    CURRENT_ACCOUNT("currently_logged_in_account"),
    PIN("pin"),
    SAVED_ACCOUNTS("all_saved_accounts"),
    USE_PROXY("use_proxy"),
    PROXY_PORT("proxy_port"),
    BATERRY_OPTIMIZATION("battery_optimization"),
    KILL_SWITCH("kill_switch"),
    LANGUAGE_PREFS("languagePreferences"),
}

@Immutable
data class AccountInfo(
    val npub: String,
    val hasPrivKey: Boolean,
)

object LocalPreferences {
    private const val COMMA = ","
    private var currentAccount: String? = null
    private var accountCache = LargeCache<String, Account>()

    fun allSavedAccounts(context: Context): List<AccountInfo> = savedAccounts(context).map { npub ->
        AccountInfo(
            npub,
            true,
        )
    }.toSet().toList()

    fun currentAccount(context: Context): String? {
        if (currentAccount == null) {
            Log.d(Amber.TAG, "currentAccount is null fetching from shared preferences")
            currentAccount = sharedPrefs(context).getString(SettingsKeys.CURRENT_ACCOUNT.key, null)
            if (currentAccount == null) {
                Log.d(Amber.TAG, "currentAccount is null fetching from all saved accounts")
                currentAccount = allSavedAccounts(context).firstOrNull()?.npub
            }
        }
        return currentAccount
    }

    private fun updateCurrentAccount(context: Context, npub: String) {
        if (currentAccount != npub) {
            currentAccount = npub

            sharedPrefs(context).edit {
                apply {
                    putString(SettingsKeys.CURRENT_ACCOUNT.key, npub)
                }
            }
        }
    }

    fun shouldShowRationale(context: Context): Boolean? {
        if (!sharedPrefs(context).contains(SettingsKeys.RATIONALE.key)) {
            return null
        }
        return sharedPrefs(context).getBoolean(SettingsKeys.RATIONALE.key, true)
    }

    fun updateShouldShowRationale(context: Context, value: Boolean) {
        sharedPrefs(context).edit {
            apply {
                putBoolean(SettingsKeys.RATIONALE.key, value)
            }
        }
    }

    fun saveSettingsToEncryptedStorage(settings: AmberSettings) {
        val context = Amber.instance
        sharedPrefs(context).edit {
            apply {
                putStringSet(SettingsKeys.DEFAULT_RELAYS.key, settings.defaultRelays.map { it.url }.toSet())
                putStringSet(SettingsKeys.DEFAULT_PROFILE_RELAYS.key, settings.defaultProfileRelays.map { it.url }.toSet())
                putLong(SettingsKeys.LAST_BIOMETRICS_TIME.key, settings.lastBiometricsTime)
                putBoolean(SettingsKeys.USE_AUTH.key, settings.useAuth)
                putInt(SettingsKeys.BIOMETRICS_TYPE.key, settings.biometricsTimeType.screenCode)
                putBoolean(SettingsKeys.USE_PIN.key, settings.usePin)
                putBoolean(SettingsKeys.USE_PROXY.key, settings.useProxy)
                putInt(SettingsKeys.PROXY_PORT.key, settings.proxyPort)
                putBoolean(SettingsKeys.KILL_SWITCH.key, settings.killSwitch.value)
                putString(SettingsKeys.LANGUAGE_PREFS.key, settings.language)
            }
        }
    }

    fun getBatteryOptimization(context: Context): Boolean = sharedPrefs(context).getBoolean(SettingsKeys.BATERRY_OPTIMIZATION.key, false)

    fun updateBatteryOptimization(context: Context, value: Boolean) {
        sharedPrefs(context).edit {
            apply {
                putBoolean(SettingsKeys.BATERRY_OPTIMIZATION.key, value)
            }
        }
    }

    fun getLastCheck(context: Context, npub: String): Long = sharedPrefs(context, npub).getLong(PrefKeys.LAST_CHECK.key, 0)

    fun setLastCheck(context: Context, npub: String, time: Long) {
        sharedPrefs(context, npub).edit {
            apply {
                putLong(PrefKeys.LAST_CHECK.key, time)
            }
        }
    }

    fun getLastMetadataUpdate(context: Context, npub: String): Long = sharedPrefs(context, npub).getLong(PrefKeys.LAST_METADATA_UPDATE.key, 0)

    fun setLastMetadataUpdate(context: Context, npub: String, time: Long) {
        sharedPrefs(context, npub).edit {
            apply {
                putLong(PrefKeys.LAST_METADATA_UPDATE.key, time)
            }
        }
    }

    fun loadPinFromEncryptedStorage(): String? {
        val context = Amber.instance
        return sharedPrefs(context).getString(SettingsKeys.PIN.key, null)
    }

    fun savePinToEncryptedStorage(pin: String?) {
        val context = Amber.instance
        sharedPrefs(context).edit {
            apply {
                if (pin == null) {
                    remove(SettingsKeys.PIN.key)
                } else {
                    putString(SettingsKeys.PIN.key, pin)
                }
            }
        }
    }

    suspend fun reloadApp() {
        val context = Amber.instance
        currentAccount = null
        savedAccounts = null
        accountCache.clear()
        allSavedAccounts(context).forEach {
            loadFromEncryptedStorage(context, it.npub)
        }
        context.settings = loadSettingsFromEncryptedStorage(context)
        context.settings.language?.let {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(it),
            )
        }
    }

    fun loadSettingsFromEncryptedStorage(context: Context = Amber.instance): AmberSettings {
        checkNotInMainThread()
        sharedPrefs(context).apply {
            val proxyPort = getInt(SettingsKeys.PROXY_PORT.key, 9050)
            HttpClientManager.setDefaultProxyOnPort(proxyPort)

            return AmberSettings(
                defaultRelays = getStringSet(SettingsKeys.DEFAULT_RELAYS.key, null)?.mapNotNull {
                    RelayUrlNormalizer.normalizeOrNull(it)
                } ?: defaultAppRelays,
                defaultProfileRelays = getStringSet(SettingsKeys.DEFAULT_PROFILE_RELAYS.key, null)?.mapNotNull {
                    RelayUrlNormalizer.normalizeOrNull(it)
                } ?: defaultIndexerRelays,
                lastBiometricsTime = getLong(SettingsKeys.LAST_BIOMETRICS_TIME.key, 0),
                useAuth = getBoolean(SettingsKeys.USE_AUTH.key, false),
                biometricsTimeType = parseBiometricsTimeType(getInt(SettingsKeys.BIOMETRICS_TYPE.key, 0)),
                usePin = getBoolean(SettingsKeys.USE_PIN.key, false),
                useProxy = getBoolean(SettingsKeys.USE_PROXY.key, false),
                proxyPort = getInt(SettingsKeys.PROXY_PORT.key, 9050),
                killSwitch = MutableStateFlow(getBoolean(SettingsKeys.KILL_SWITCH.key, false)),
                language = getString(SettingsKeys.LANGUAGE_PREFS.key, null),
            )
        }
    }

    private var savedAccounts: List<String>? = null

    private fun savedAccounts(context: Context): List<String> {
        if (savedAccounts == null) {
            savedAccounts = sharedPrefs(context)
                .getString(SettingsKeys.SAVED_ACCOUNTS.key, null)?.split(COMMA) ?: listOf()
        }
        return savedAccounts!!
    }

    private fun updateSavedAccounts(context: Context, accounts: List<String>) {
        if (savedAccounts != accounts) {
            savedAccounts = accounts

            sharedPrefs(context).edit {
                apply {
                    putString(SettingsKeys.SAVED_ACCOUNTS.key, accounts.joinToString(COMMA).ifBlank { null })
                }
            }
        }
    }

    private fun getDirPath(context: Context): String = "${context.filesDir.parent}/shared_prefs/"

    private fun addAccount(context: Context, npub: String) {
        val accounts = savedAccounts(context).toMutableList()
        if (npub !in accounts) {
            accounts.add(npub)
            updateSavedAccounts(context, accounts)
        }
    }

    private fun setCurrentAccount(context: Context, account: Account) {
        updateCurrentAccount(context, account.npub)
        addAccount(context, account.npub)
    }

    fun switchToAccount(context: Context, npub: String) {
        updateCurrentAccount(context, npub)
    }

    fun containsAccount(context: Context, npub: String): Boolean = savedAccounts(context).contains(npub)

    /**
     * Removes the account from the app level shared preferences
     */
    private fun removeAccount(context: Context, npub: String) {
        val accounts = savedAccounts(context).toMutableList()
        if (accounts.remove(npub)) {
            updateSavedAccounts(context, accounts)
        }
    }

    /**
     * Deletes the npub-specific shared preference file
     */
    private fun deleteUserPreferenceFile(context: Context, npub: String) {
        val prefsDir = File(getDirPath(context))
        prefsDir.list()?.forEach {
            if (it.contains(npub)) {
                try {
                    val result = File(prefsDir, it).delete()
                    Log.d(Amber.TAG, "deleted $it: $result")
                } catch (e: Exception) {
                    Log.d(Amber.TAG, "failed to delete $it", e)
                }
            }
        }
    }

    private fun sharedPrefs(
        context: Context,
        npub: String? = null,
    ): SharedPreferences {
        val preferenceFile = if (npub == null) "prefs" else "prefs_$npub"
        return context.getSharedPreferences(preferenceFile, Context.MODE_PRIVATE)
    }

    private suspend fun deleteUserDataStoreFile(context: Context, npub: String) {
        DataStoreAccess.clearCacheForNpub(context, npub)
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
    fun updatePrefsForLogout(npub: String, context: Context): Boolean {
        accountCache.remove(npub)
        val userPrefs = sharedPrefs(context, npub)
        userPrefs.edit(commit = true) { clear() }
        removeAccount(context, npub)
        deleteUserPreferenceFile(context, npub)

        runBlocking { deleteUserDataStoreFile(context, npub) }

        if (savedAccounts(context).isEmpty()) {
            val appPrefs = sharedPrefs(context)
            appPrefs.edit(commit = true) { clear() }
            return true
        } else if (currentAccount(context) == npub) {
            updateCurrentAccount(context, savedAccounts(context).elementAt(0))
            return false
        }
        return false
    }

    suspend fun updatePrefsForLogin(
        context: Context,
        account: Account,
        pubKey: String?,
        privKey: String?,
        seedWords: String?,
    ) {
        setCurrentAccount(context, account)
        saveToEncryptedStorage(context, account, pubKey, privKey, seedWords)
    }

    suspend fun saveToEncryptedStorage(
        context: Context,
        account: Account,
        pubKey: String?,
        privKey: String?,
        seedWords: String?,
    ) {
        privKey?.let {
            DataStoreAccess.saveEncryptedKey(context, account.npub, DataStoreAccess.NOSTR_PRIVKEY, it)
        }

        seedWords?.let {
            DataStoreAccess.saveEncryptedKey(
                context,
                account.npub,
                DataStoreAccess.SEED_WORDS,
                seedWords,
            )
        }

        val prefs = sharedPrefs(context = context, account.npub)
        prefs.edit {
            apply {
                pubKey?.let { putString(PrefKeys.NOSTR_PUBKEY.key, it) }
                putString(PrefKeys.ACCOUNT_NAME.key, account.name.value)
                putString(PrefKeys.PROFILE_URL.key, account.picture.value)
                putInt(PrefKeys.SIGN_POLICY.key, account.signPolicy)
                putBoolean(PrefKeys.DID_BACKUP.key, account.didBackup)
            }
        }

        accountCache.put(account.npub, account)
    }

    suspend fun loadFromEncryptedStorage(context: Context): Account? {
        currentAccount(context)?.let {
            return loadFromEncryptedStorage(context, it)
        }
        return null
    }

    fun allCachedAccounts(): List<Account> = accountCache.values().toList()

    suspend fun allAccounts(context: Context): List<Account> {
        val accountInfos = allSavedAccounts(context)
        return accountInfos.mapNotNull {
            loadFromEncryptedStorage(context, it.npub)
        }
    }

    fun loadFromEncryptedStorageSync(context: Context, npub: String? = null): Account? {
        if (savedAccounts == null || accountCache.size() == 0 || savedAccounts?.size != accountCache.size()) {
            Log.d(Amber.TAG, "accountCache is null loading accounts")
            runBlocking {
                allSavedAccounts(context).forEach {
                    loadFromEncryptedStorage(context, it.npub)
                }
            }
        }
        if (npub == null) {
            val currentAccount = currentAccount(context) ?: return null
            return accountCache.get(currentAccount)
        }
        return accountCache.get(npub)
    }

    fun setAccountName(
        context: Context,
        npub: String,
        value: String,
    ) {
        sharedPrefs(context, npub).edit {
            apply {
                putString(PrefKeys.ACCOUNT_NAME.key, value)
            }
        }
        accountCache.get(npub)?.name?.tryEmit(value)
    }

    fun getAccountName(context: Context, npub: String): String {
        sharedPrefs(context, npub).apply {
            return getString(PrefKeys.ACCOUNT_NAME.key, "") ?: ""
        }
    }

    fun updateProxy(
        context: Context,
        useProxy: Boolean,
        port: Int,
    ) {
        sharedPrefs(context).edit {
            apply {
                putBoolean(SettingsKeys.USE_PROXY.key, useProxy)
                putInt(SettingsKeys.PROXY_PORT.key, port)
            }
        }
        Amber.instance.settings = loadSettingsFromEncryptedStorage()
        HttpClientManager.setDefaultProxyOnPort(port)
    }

    suspend fun loadFromEncryptedStorage(context: Context, npub: String): Account? {
        if (accountCache.get(npub) != null) {
            return accountCache.get(npub)
        }
        if (!containsAccount(context, npub)) {
            return null
        }
        val privKey = DataStoreAccess.getEncryptedKey(
            context,
            npub,
            DataStoreAccess.NOSTR_PRIVKEY,
        )
        sharedPrefs(context, npub).apply {
            val pubKey = getString(PrefKeys.NOSTR_PUBKEY.key, null) ?: return null
            val name = getString(PrefKeys.ACCOUNT_NAME.key, "") ?: ""
            val picture = getString(PrefKeys.PROFILE_URL.key, "") ?: ""
            val signPolicy = getInt(PrefKeys.SIGN_POLICY.key, 1)
            val didBackup = getBoolean(PrefKeys.DID_BACKUP.key, true)

            val account =
                Account(
                    signer = NostrSignerInternal(KeyPair(privKey.hexToByteArray())),
                    hexKey = pubKey,
                    npub = pubKey.hexToByteArray().toNpub(),
                    name = MutableStateFlow(name),
                    picture = MutableStateFlow(picture),
                    signPolicy = signPolicy,
                    didBackup = didBackup,
                )
            accountCache.put(npub, account)
            return account
        }
    }
}
