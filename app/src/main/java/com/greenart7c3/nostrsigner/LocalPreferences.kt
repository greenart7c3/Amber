package com.greenart7c3.nostrsigner

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.LruCache
import androidx.compose.runtime.Immutable
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.ui.parseBiometricsTimeType
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.ammolite.service.HttpClientManager
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.signers.NostrSignerInternal
import fr.acinq.secp256k1.jni.BuildConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject

// Release mode (!BuildConfig.DEBUG) always uses encrypted preferences
// To use plaintext SharedPreferences for debugging, set this to true
// It will only apply in Debug builds
private const val DEBUG_PLAINTEXT_PREFERENCES = false
private const val DEBUG_PREFERENCES_NAME = "debug_prefs"

private object PrefKeys {
    const val PIN = "pin"
    const val USE_PIN = "use_pin"
    const val CURRENT_ACCOUNT = "currently_logged_in_account"
    const val SAVED_ACCOUNTS = "all_saved_accounts"
    const val NOSTR_PRIVKEY = "nostr_privkey"
    const val NOSTR_PUBKEY = "nostr_pubkey"
    const val REMEMBER_APPS = "remember_apps"
    const val ACCOUNT_NAME = "account_name"
    const val RATIONALE = "rationale"
    const val USE_PROXY = "use_proxy"
    const val PROXY_PORT = "proxy_port"
    const val NOTIFICATION_TYPE = "notification_type"
    const val LANGUAGE_PREFS = "languagePreferences"
    const val DEFAULT_RELAYS = "default_relays"
    const val ENDPOINT = "endpoint"
    const val PUSH_SERVER_MESSAGE = "push_server_message"
    const val ALLOW_NEW_CONNECTIONS = "allow_new_connections"
    const val USE_AUTH = "use_auth"
    const val BIOMETRICS_TYPE = "biometrics_type"
    const val LAST_BIOMETRICS_TIME = "last_biometrics_time"
    const val SIGN_POLICY = "default_sign_policy"
    const val SEED_WORDS2 = "seed_words"
    const val PROFILE_URL = "profile_url"
    const val LAST_METADATA_UPDATE = "last_metadata_update"
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
            currentAccount = encryptedPreferences(context).getString(PrefKeys.CURRENT_ACCOUNT, null)
        }
        return currentAccount
    }

    private fun updateCurrentAccount(context: Context, npub: String) {
        if (currentAccount != npub) {
            currentAccount = npub

            encryptedPreferences(context).edit().apply {
                putString(PrefKeys.CURRENT_ACCOUNT, npub)
            }.apply()
        }
    }

    fun shouldShowRationale(context: Context): Boolean? {
        if (!encryptedPreferences(context).contains(PrefKeys.RATIONALE)) {
            return null
        }
        return encryptedPreferences(context).getBoolean(PrefKeys.RATIONALE, true)
    }

    fun updateShouldShowRationale(context: Context, value: Boolean) {
        encryptedPreferences(context).edit().apply {
            putBoolean(PrefKeys.RATIONALE, value)
        }.apply()
    }

    fun saveSettingsToEncryptedStorage(settings: AmberSettings) {
        val context = NostrSigner.getInstance()
        encryptedPreferences(context).edit().apply {
            putString(PrefKeys.ENDPOINT, settings.endpoint)
            putBoolean(PrefKeys.PUSH_SERVER_MESSAGE, settings.pushServerMessage)
            putStringSet(PrefKeys.DEFAULT_RELAYS, settings.defaultRelays.map { it.url }.toSet())
            putLong(PrefKeys.LAST_BIOMETRICS_TIME, settings.lastBiometricsTime)
            putBoolean(PrefKeys.USE_AUTH, settings.useAuth)
            putInt(PrefKeys.BIOMETRICS_TYPE, settings.biometricsTimeType.screenCode)
            putBoolean(PrefKeys.USE_PIN, settings.usePin)
        }.apply()
    }

    fun getLastMetadataUpdate(context: Context, npub: String): Long {
        return encryptedPreferences(context, npub).getLong(PrefKeys.LAST_METADATA_UPDATE, 0)
    }

    fun setLastMetadataUpdate(context: Context, npub: String, time: Long) {
        encryptedPreferences(context, npub).edit().apply {
            putLong(PrefKeys.LAST_METADATA_UPDATE, time)
        }.apply()
    }

    fun loadPinFromEncryptedStorage(): String? {
        val context = NostrSigner.getInstance()
        return encryptedPreferences(context).getString(PrefKeys.PIN, null)
    }

    fun loadProfileUrlFromEncryptedStorage(npub: String): String? {
        val context = NostrSigner.getInstance()
        return encryptedPreferences(context, npub).getString(PrefKeys.PROFILE_URL, null)
    }

    fun saveProfileUrlToEncryptedStorage(profileUrl: String?, npub: String) {
        val context = NostrSigner.getInstance()
        encryptedPreferences(context, npub).edit().apply {
            if (profileUrl == null) {
                remove(PrefKeys.PROFILE_URL)
            } else {
                putString(PrefKeys.PROFILE_URL, profileUrl)
            }
        }.apply()
    }

    fun savePinToEncryptedStorage(pin: String?) {
        val context = NostrSigner.getInstance()
        encryptedPreferences(context).edit().apply {
            if (pin == null) {
                remove(PrefKeys.PIN)
            } else {
                putString(PrefKeys.PIN, pin)
            }
        }.apply()
    }

    suspend fun isNotificationTypeConfigured(): Boolean {
        val context = NostrSigner.getInstance()
        val prefs = encryptedPreferences(context)
        return prefs.contains(PrefKeys.NOTIFICATION_TYPE)
    }

    suspend fun loadSettingsFromEncryptedStorage(): AmberSettings {
        val context = NostrSigner.getInstance()

        encryptedPreferences(context).apply {
            return AmberSettings(
                endpoint = getString(PrefKeys.ENDPOINT, "") ?: "",
                pushServerMessage = getBoolean(PrefKeys.PUSH_SERVER_MESSAGE, false),
                defaultRelays = getStringSet(PrefKeys.DEFAULT_RELAYS, null)?.map {
                    RelaySetupInfo(it, read = true, write = true, feedTypes = COMMON_FEED_TYPES)
                } ?: listOf(RelaySetupInfo("wss://relay.nsec.app", read = true, write = true, feedTypes = COMMON_FEED_TYPES)),
                lastBiometricsTime = getLong(PrefKeys.LAST_BIOMETRICS_TIME, 0),
                useAuth = getBoolean(PrefKeys.USE_AUTH, false),
                biometricsTimeType = parseBiometricsTimeType(getInt(PrefKeys.BIOMETRICS_TYPE, 0)),
                usePin = getBoolean(PrefKeys.USE_PIN, false),
            )
        }
    }

    private var savedAccounts: List<String>? = null

    private fun savedAccounts(context: Context): List<String> {
        if (savedAccounts == null) {
            savedAccounts = encryptedPreferences(context)
                .getString(PrefKeys.SAVED_ACCOUNTS, null)?.split(COMMA) ?: listOf()
        }
        return savedAccounts!!
    }

    private fun updateSavedAccounts(context: Context, accounts: List<String>) {
        if (savedAccounts != accounts) {
            savedAccounts = accounts

            encryptedPreferences(context).edit().apply {
                putString(PrefKeys.SAVED_ACCOUNTS, accounts.joinToString(COMMA).ifBlank { null })
            }.apply()
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
        val npub = account.signer.keyPair.pubKey.toNpub()
        updateCurrentAccount(context, npub)
        addAccount(context, npub)
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
                File(prefsDir, it).delete()
            }
        }
    }

    private fun encryptedPreferences(
        context: Context,
        npub: String? = null,
    ): SharedPreferences {
        return if (BuildConfig.DEBUG && DEBUG_PLAINTEXT_PREFERENCES) {
            val preferenceFile = if (npub == null) DEBUG_PREFERENCES_NAME else "${DEBUG_PREFERENCES_NAME}_$npub"
            context.getSharedPreferences(preferenceFile, Context.MODE_PRIVATE)
        } else {
            return EncryptedStorage.preferences(npub, context)
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
    fun updatePrefsForLogout(npub: String, context: Context) {
        accountCache.remove(npub)
        val userPrefs = encryptedPreferences(context, npub)
        userPrefs.edit().clear().commit()
        removeAccount(context, npub)
        deleteUserPreferenceFile(context, npub)

        if (savedAccounts(context).isEmpty()) {
            val appPrefs = encryptedPreferences(context)
            appPrefs.edit().clear().apply()
        } else if (currentAccount(context) == npub) {
            updateCurrentAccount(context, savedAccounts(context).elementAt(0))
        }
    }

    fun updatePrefsForLogin(context: Context, account: Account) {
        setCurrentAccount(context, account)
        saveToEncryptedStorage(context, account)
    }

    suspend fun deleteSavedApps(
        applications: List<ApplicationEntity>,
        database: AppDatabase,
    ) = withContext(Dispatchers.IO) {
        applications.forEach {
            database.applicationDao().delete(it)
        }
    }

    fun saveToEncryptedStorage(context: Context, account: Account) {
        val prefs = encryptedPreferences(context = context, account.signer.keyPair.pubKey.toNpub())
        prefs.edit().apply {
            account.signer.keyPair.privKey.let { putString(PrefKeys.NOSTR_PRIVKEY, it?.toHexKey()) }
            account.signer.keyPair.pubKey.let { putString(PrefKeys.NOSTR_PUBKEY, it.toHexKey()) }
            putBoolean(PrefKeys.USE_PROXY, account.useProxy)
            putInt(PrefKeys.PROXY_PORT, account.proxyPort)
            putString(PrefKeys.ACCOUNT_NAME, account.name)
            putString(PrefKeys.LANGUAGE_PREFS, account.language)
            putBoolean(PrefKeys.ALLOW_NEW_CONNECTIONS, account.allowNewConnections)
            putInt(PrefKeys.SIGN_POLICY, account.signPolicy)
            putString(PrefKeys.SEED_WORDS2, account.seedWords.joinToString(separator = " ") { it })
        }.apply()
    }

    fun loadFromEncryptedStorage(context: Context): Account? {
        return loadFromEncryptedStorage(context, currentAccount(context))
    }

    fun setAccountName(
        context: Context,
        npub: String,
        value: String,
    ) {
        encryptedPreferences(context, npub).edit().apply {
            putString(PrefKeys.ACCOUNT_NAME, value)
        }.apply()
        accountCache.get(npub)?.let {
            it.name = value
        }
    }

    fun getAccountName(context: Context, npub: String): String {
        encryptedPreferences(context, npub).apply {
            return getString(PrefKeys.ACCOUNT_NAME, "") ?: ""
        }
    }

    fun updateProxy(
        context: Context,
        useProxy: Boolean,
        port: Int,
    ) {
        val npub = currentAccount(context) ?: return
        encryptedPreferences(context, npub).edit().apply {
            putBoolean(PrefKeys.USE_PROXY, useProxy)
            putInt(PrefKeys.PROXY_PORT, port)
        }.apply()
        accountCache.get(npub)?.let {
            it.useProxy = useProxy
            it.proxyPort = port
        }
        HttpClientManager.setDefaultProxyOnPort(port)
    }

    private suspend fun convertToDatabase(
        map: MutableMap<String, Boolean>,
        pubKey: String,
        database: AppDatabase,
    ) = withContext(Dispatchers.IO) {
        map.forEach {
            val splitData = it.key.split("-")
            database.applicationDao().deletePermissions(splitData.first())
        }
        map.forEach {
            val splitData = it.key.split("-")
            database.applicationDao().insertApplication(
                ApplicationEntity(
                    splitData.first(),
                    splitData.first(),
                    emptyList(),
                    "",
                    "",
                    "",
                    pubKey,
                    true,
                    "",
                    false,
                    1,
                ),
            )
            database.applicationDao().insertPermissions(
                listOf(
                    ApplicationPermissionsEntity(
                        id = null,
                        pkKey = splitData.first(),
                        type = splitData[1],
                        kind = runCatching { splitData[2].toInt() }.getOrNull(),
                        acceptable = it.value,
                    ),
                ),
            )
        }
    }

    fun loadFromEncryptedStorage(context: Context, npub: String?): Account? {
        if (npub != null && accountCache.get(npub) != null) {
            return accountCache.get(npub)
        }
        encryptedPreferences(context, npub).apply {
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
            val localNpub = pubKey.hexToByteArray().toNpub()
            runBlocking { convertToDatabase(outputMap, pubKey, NostrSigner.getInstance().getDatabase(localNpub)) }
            this.edit().apply {
                remove(PrefKeys.REMEMBER_APPS)
            }.apply()
            val name = getString(PrefKeys.ACCOUNT_NAME, "") ?: ""
            val useProxy = getBoolean(PrefKeys.USE_PROXY, false)
            val proxyPort = getInt(PrefKeys.PROXY_PORT, 9050)
            val language = getString(PrefKeys.LANGUAGE_PREFS, null)
            val allowNewConnections = getBoolean(PrefKeys.ALLOW_NEW_CONNECTIONS, false)
            val signPolicy = getInt(PrefKeys.SIGN_POLICY, 1)
            val savedSeedWords = getString(PrefKeys.SEED_WORDS2, null)
            val seedWords = savedSeedWords?.split(" ")?.toSet() ?: emptySet()

            HttpClientManager.setDefaultProxyOnPort(proxyPort)
            val account =
                Account(
                    signer = NostrSignerInternal(KeyPair(privKey = privKey?.hexToByteArray(), pubKey = pubKey.hexToByteArray())),
                    name = name,
                    useProxy = useProxy,
                    proxyPort = proxyPort,
                    language = language,
                    allowNewConnections = allowNewConnections,
                    signPolicy = signPolicy,
                    seedWords = seedWords,
                )
            accountCache.put(npub, account)
            return account
        }
    }

    fun clearPin() {
        val context = NostrSigner.getInstance()
        encryptedPreferences(context).edit().apply {
            remove(PrefKeys.USE_PIN)
        }.apply()
        NostrSigner.getInstance().settings = NostrSigner.getInstance().settings.copy(usePin = false)
    }
}
