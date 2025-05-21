package com.greenart7c3.nostrsigner

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.greenart7c3.nostrsigner.ui.parseBiometricsTimeType
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private enum class PrefKeys(val key: String) {
    NOSTR_PRIVKEY("nostr_privkey"),
    NOSTR_PUBKEY("nostr_pubkey"),
    ACCOUNT_NAME("account_name"),
    LANGUAGE_PREFS("languagePreferences"),
    ALLOW_NEW_CONNECTIONS("allow_new_connections"),
    SIGN_POLICY("default_sign_policy"),
    SEED_WORDS2("seed_words"),
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
}

@Immutable
data class AccountInfo(
    val npub: String,
    val hasPrivKey: Boolean,
)

object LocalPreferences {
    private const val COMMA = ","
    private var currentAccount: String? = null
    private var accountCache = LruCache<String, Account>(10)

    fun allLegacySavedAccounts(context: Context): List<AccountInfo> {
        return legacySavedAccounts(context).map { npub ->
            AccountInfo(
                npub,
                true,
            )
        }.toSet().toList()
    }

    fun allSavedAccounts(context: Context): List<AccountInfo> {
        return savedAccounts(context).map { npub ->
            AccountInfo(
                npub,
                true,
            )
        }.toSet().toList()
    }

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
            }
        }
    }

    fun getBatteryOptimization(context: Context): Boolean {
        return sharedPrefs(context).getBoolean(SettingsKeys.BATERRY_OPTIMIZATION.key, false)
    }

    fun updateBatteryOptimization(context: Context, value: Boolean) {
        sharedPrefs(context).edit {
            apply {
                putBoolean(SettingsKeys.BATERRY_OPTIMIZATION.key, value)
            }
        }
    }

    fun getLastCheck(context: Context, npub: String): Long {
        return sharedPrefs(context, npub).getLong(PrefKeys.LAST_CHECK.key, 0)
    }

    fun setLastCheck(context: Context, npub: String, time: Long) {
        sharedPrefs(context, npub).edit {
            apply {
                putLong(PrefKeys.LAST_CHECK.key, time)
            }
        }
    }

    fun getLastMetadataUpdate(context: Context, npub: String): Long {
        return sharedPrefs(context, npub).getLong(PrefKeys.LAST_METADATA_UPDATE.key, 0)
    }

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

    fun loadProfileUrlFromEncryptedStorage(npub: String): String? {
        val context = Amber.instance
        return sharedPrefs(context, npub).getString(PrefKeys.PROFILE_URL.key, null)
    }

    fun saveProfileUrlToEncryptedStorage(profileUrl: String?, npub: String) {
        val context = Amber.instance
        sharedPrefs(context, npub).edit {
            apply {
                if (profileUrl == null) {
                    remove(PrefKeys.PROFILE_URL.key)
                } else {
                    putString(PrefKeys.PROFILE_URL.key, profileUrl)
                }
            }
        }
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
        accountCache.evictAll()
        allSavedAccounts(context).forEach {
            loadFromEncryptedStorage(context, it.npub)
        }
        context.settings = loadSettingsFromEncryptedStorage(context)
    }

    fun loadSettingsFromEncryptedStorage(context: Context = Amber.instance): AmberSettings {
        checkNotInMainThread()
        sharedPrefs(context).apply {
            val proxyPort = getInt(SettingsKeys.PROXY_PORT.key, 9050)
            HttpClientManager.setDefaultProxyOnPort(proxyPort)

            return AmberSettings(
                defaultRelays = getStringSet(SettingsKeys.DEFAULT_RELAYS.key, null)?.map {
                    RelaySetupInfo(it, read = true, write = true, feedTypes = COMMON_FEED_TYPES)
                } ?: listOf(RelaySetupInfo("wss://relay.nsec.app", read = true, write = true, feedTypes = COMMON_FEED_TYPES)),
                defaultProfileRelays = getStringSet(SettingsKeys.DEFAULT_PROFILE_RELAYS.key, null)?.map {
                    RelaySetupInfo(it, read = true, write = false, feedTypes = COMMON_FEED_TYPES)
                } ?: listOf(
                    RelaySetupInfo("wss://relay.nostr.band", read = true, write = false, feedTypes = COMMON_FEED_TYPES),
                    RelaySetupInfo("wss://purplepag.es", read = true, write = false, feedTypes = COMMON_FEED_TYPES),
                ),
                lastBiometricsTime = getLong(SettingsKeys.LAST_BIOMETRICS_TIME.key, 0),
                useAuth = getBoolean(SettingsKeys.USE_AUTH.key, false),
                biometricsTimeType = parseBiometricsTimeType(getInt(SettingsKeys.BIOMETRICS_TYPE.key, 0)),
                usePin = getBoolean(SettingsKeys.USE_PIN.key, false),
                useProxy = getBoolean(SettingsKeys.USE_PROXY.key, false),
                proxyPort = getInt(SettingsKeys.PROXY_PORT.key, 9050),
            )
        }
    }

    private var savedAccounts: List<String>? = null

    @Suppress("DEPRECATION")
    private fun legacySavedAccounts(context: Context): List<String> {
        return encryptedPreferences(context)
            .getString(SettingsKeys.SAVED_ACCOUNTS.key, null)?.split(COMMA) ?: listOf()
    }

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

    private fun getDirPath(context: Context): String {
        return "${context.filesDir.parent}/shared_prefs/"
    }

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

    fun containsAccount(context: Context, npub: String): Boolean {
        return savedAccounts(context).contains(npub)
    }

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

    fun deleteLegacyUserPreferenceFile(context: Context, npub: String) {
        val prefsDir = File(getDirPath(context))
        prefsDir.list()?.forEach {
            if (it.contains(npub) && it.startsWith("secret_keeper")) {
                try {
                    val result = File(prefsDir, it).delete()
                    Log.d(Amber.TAG, "deleted $it: $result")
                } catch (e: Exception) {
                    Log.d(Amber.TAG, "failed to delete $it", e)
                }
            }
        }
    }

    fun existsLegacySettings(context: Context): Boolean {
        val prefsDir = File(getDirPath(context))
        var result = false
        prefsDir.list()?.forEach {
            if (it == "secret_keeper.xml") {
                result = true
            }
        }
        return result
    }

    fun deleteSettingsPreferenceFile(context: Context) {
        val prefsDir = File(getDirPath(context))
        prefsDir.list()?.forEach {
            if (it == "secret_keeper.xml") {
                try {
                    val result = File(prefsDir, it).delete()
                    Log.d(Amber.TAG, "deleted $it: $result")
                } catch (e: Exception) {
                    Log.d(Amber.TAG, "failed to delete $it", e)
                }
            }
        }
    }

    @Deprecated("Use sharedPrefs instead")
    private fun encryptedPreferences(
        context: Context,
        npub: String? = null,
    ): SharedPreferences {
        return EncryptedStorage.preferences(npub, context)
    }

    private fun sharedPrefs(
        context: Context,
        npub: String? = null,
    ): SharedPreferences {
        val preferenceFile = if (npub == null) "prefs" else "prefs_$npub"
        return context.getSharedPreferences(preferenceFile, Context.MODE_PRIVATE)
    }

    private fun deleteUserDataStoreFile(context: Context, npub: String) {
        val fileName = "secure_datastore_$npub.preferences_pb"
        val file = File(context.filesDir, "datastore/$fileName")
        if (file.exists()) {
            file.delete()
        }
        DataStoreAccess.clearCacheForNpub(npub)
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

        deleteUserDataStoreFile(context, npub)

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

    fun updatePrefsForLogin(context: Context, account: Account) {
        setCurrentAccount(context, account)
        Amber.instance.applicationIOScope.launch {
            saveToEncryptedStorage(context, account)
        }
    }

    suspend fun saveToEncryptedStorage(context: Context, account: Account) {
        val prefs = sharedPrefs(context = context, account.npub)
        prefs.edit {
            apply {
                account.signer.keyPair.pubKey.let { putString(PrefKeys.NOSTR_PUBKEY.key, it.toHexKey()) }
                putString(PrefKeys.ACCOUNT_NAME.key, account.name)
                putString(PrefKeys.LANGUAGE_PREFS.key, account.language)
                putBoolean(PrefKeys.ALLOW_NEW_CONNECTIONS.key, account.allowNewConnections)
                putInt(PrefKeys.SIGN_POLICY.key, account.signPolicy)
                putBoolean(PrefKeys.DID_BACKUP.key, account.didBackup)
            }
        }

        account.signer.keyPair.privKey?.toHexKey()?.let {
            DataStoreAccess.saveEncryptedKey(context, account.npub, DataStoreAccess.NOSTR_PRIVKEY, it)
        }

        DataStoreAccess.saveEncryptedKey(
            context,
            account.npub,
            DataStoreAccess.SEED_WORDS,
            account.seedWords.joinToString(" "),
        )
        accountCache.put(account.npub, account)
    }

    suspend fun loadFromEncryptedStorage(context: Context): Account? {
        currentAccount(context)?.let {
            return loadFromEncryptedStorage(context, it)
        }
        return null
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
            return accountCache[currentAccount]
        }
        return accountCache[npub]
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
        accountCache.get(npub)?.let {
            it.name = value
        }
    }

    fun getAccountName(context: Context, npub: String): String {
        sharedPrefs(context, npub).apply {
            return getString(PrefKeys.ACCOUNT_NAME.key, "") ?: ""
        }
    }

    suspend fun updateProxy(
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
        sharedPrefs(context, npub).apply {
            val pubKey = getString(PrefKeys.NOSTR_PUBKEY.key, null) ?: return null
            val privKey = DataStoreAccess.getEncryptedKey(context, npub, DataStoreAccess.NOSTR_PRIVKEY)
            val name = getString(PrefKeys.ACCOUNT_NAME.key, "") ?: ""
            val language = getString(PrefKeys.LANGUAGE_PREFS.key, null)
            val allowNewConnections = getBoolean(PrefKeys.ALLOW_NEW_CONNECTIONS.key, false)
            val signPolicy = getInt(PrefKeys.SIGN_POLICY.key, 1)
            val savedSeedWords = DataStoreAccess.getEncryptedKey(context, npub, DataStoreAccess.SEED_WORDS)
            val seedWords = savedSeedWords?.split(" ")?.toSet() ?: emptySet()
            val didBackup = getBoolean(PrefKeys.DID_BACKUP.key, true)

            val account =
                Account(
                    signer = NostrSignerInternal(KeyPair(privKey = privKey?.hexToByteArray(), pubKey = pubKey.hexToByteArray())),
                    name = name,
                    language = language,
                    allowNewConnections = allowNewConnections,
                    signPolicy = signPolicy,
                    seedWords = seedWords,
                    didBackup = didBackup,
                )
            accountCache.put(npub, account)
            return account
        }
    }

    suspend fun didMigrateFromLegacyStorage(context: Context, npub: String): Boolean {
        val existingPrivKey = DataStoreAccess.getEncryptedKey(context, npub, DataStoreAccess.NOSTR_PRIVKEY)
        val existingSeed = DataStoreAccess.getEncryptedKey(context, npub, DataStoreAccess.SEED_WORDS)
        return !existingPrivKey.isNullOrBlank() && !existingSeed.isNullOrBlank()
    }

    @Suppress("DEPRECATION")
    suspend fun migrateFromSharedPrefs(context: Context, npub: String) {
        withContext(Dispatchers.IO) {
            if (didMigrateFromLegacyStorage(context, npub)) return@withContext

            val legacyPrefs = encryptedPreferences(context, npub)

            val privKey = legacyPrefs.getString(PrefKeys.NOSTR_PRIVKEY.key, null)
            val seedWords = legacyPrefs.getString(PrefKeys.SEED_WORDS2.key, null)

            if (!privKey.isNullOrBlank()) {
                DataStoreAccess.saveEncryptedKey(context, npub, DataStoreAccess.NOSTR_PRIVKEY, privKey)
            }

            if (!seedWords.isNullOrBlank()) {
                DataStoreAccess.saveEncryptedKey(context, npub, DataStoreAccess.SEED_WORDS, seedWords)
            }
            legacyPrefs.edit {
                apply {
                    remove(PrefKeys.NOSTR_PRIVKEY.key)
                    remove(PrefKeys.SEED_WORDS2.key)
                }
            }

            migrateUserSharedPrefs(context, npub)
            deleteLegacyUserPreferenceFile(context, npub)
            if (existsLegacySettings(context)) {
                migrateSettings(context)
                deleteSettingsPreferenceFile(context)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun migrateUserSharedPrefs(context: Context, npub: String) {
        val legacyPrefs = encryptedPreferences(context, npub)
        val sharedPrefs = sharedPrefs(context, npub)
        for ((key, value) in legacyPrefs.all) {
            when (value) {
                is String -> sharedPrefs.edit { putString(key, value) }
                is Boolean -> sharedPrefs.edit { putBoolean(key, value) }
                is Int -> sharedPrefs.edit { putInt(key, value) }
                is Long -> sharedPrefs.edit { putLong(key, value) }
                is Float -> sharedPrefs.edit { putFloat(key, value) }
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    if (value.all { it is String }) {
                        sharedPrefs.edit { putStringSet(key, value as Set<String>) }
                    }
                }
                else -> {
                    // Ignore unsupported types
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun migrateSettings(context: Context) {
        val settingsPrefs = sharedPrefs(context)
        val legacySettingsPrefs = encryptedPreferences(context)
        for ((key, value) in legacySettingsPrefs.all) {
            when (value) {
                is String -> settingsPrefs.edit { putString(key, value) }
                is Boolean -> settingsPrefs.edit { putBoolean(key, value) }
                is Int -> settingsPrefs.edit { putInt(key, value) }
                is Long -> settingsPrefs.edit { putLong(key, value) }
                is Float -> settingsPrefs.edit { putFloat(key, value) }
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    if (value.all { it is String }) {
                        settingsPrefs.edit { putStringSet(key, value as Set<String>) }
                    }
                }
                else -> {
                    // Ignore unsupported types
                }
            }
        }
    }

    suspend fun migrateTorSettings(context: Context) {
        if (sharedPrefs(context).contains(SettingsKeys.USE_PROXY.key)) return

        val useProxy = allSavedAccounts(context).any {
            sharedPrefs(context, it.npub).getBoolean(SettingsKeys.USE_PROXY.key, false)
        }
        val proxyPort: Int = allSavedAccounts(context).firstNotNullOfOrNull {
            if (sharedPrefs(context, it.npub).getBoolean(SettingsKeys.USE_PROXY.key, false)) {
                sharedPrefs(context, it.npub).getInt(SettingsKeys.PROXY_PORT.key, 9050)
            } else {
                null
            }
        } ?: 9050
        saveSettingsToEncryptedStorage(
            settings = loadSettingsFromEncryptedStorage(context).copy(
                useProxy = useProxy,
                proxyPort = proxyPort,
            ),
        )
    }
}
