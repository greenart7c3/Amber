package com.greenart7c3.nostrsigner

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.greenart7c3.nostrsigner.ui.parseBiometricsTimeType
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import java.io.File

// Release mode (!BuildConfig.DEBUG) always uses encrypted preferences
// To use plaintext SharedPreferences for debugging, set this to true
// It will only apply in Debug builds
private const val DEBUG_PLAINTEXT_PREFERENCES = false
private const val DEBUG_PREFERENCES_NAME = "debug_prefs"

private object PrefKeys {
    const val NOSTR_PRIVKEY = "nostr_privkey"
    const val NOSTR_PUBKEY = "nostr_pubkey"
    const val ACCOUNT_NAME = "account_name"
    const val LANGUAGE_PREFS = "languagePreferences"
    const val ALLOW_NEW_CONNECTIONS = "allow_new_connections"
    const val SIGN_POLICY = "default_sign_policy"
    const val SEED_WORDS2 = "seed_words"
    const val PROFILE_URL = "profile_url"
    const val LAST_METADATA_UPDATE = "last_metadata_update"
    const val LAST_CHECK = "last_check"
    const val NCRYPT_SEC = "ncrypt_sec"
    const val DID_BACKUP = "did_backup"
}

private object SettingsKeys {
    const val DEFAULT_RELAYS = "default_relays"
    const val DEFAULT_PROFILE_RELAYS = "default_profile_relays"
    const val LAST_BIOMETRICS_TIME = "last_biometrics_time"
    const val USE_AUTH = "use_auth"
    const val BIOMETRICS_TYPE = "biometrics_type"
    const val USE_PIN = "use_pin"
    const val RATIONALE = "rationale"
    const val CURRENT_ACCOUNT = "currently_logged_in_account"
    const val PIN = "pin"
    const val SAVED_ACCOUNTS = "all_saved_accounts"
    const val USE_PROXY = "use_proxy"
    const val PROXY_PORT = "proxy_port"
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
            currentAccount = encryptedPreferences(context).getString(SettingsKeys.CURRENT_ACCOUNT, null)
        }
        return currentAccount
    }

    private fun updateCurrentAccount(context: Context, npub: String) {
        if (currentAccount != npub) {
            currentAccount = npub

            encryptedPreferences(context).edit {
                apply {
                    putString(SettingsKeys.CURRENT_ACCOUNT, npub)
                }
            }
        }
    }

    fun shouldShowRationale(context: Context): Boolean? {
        if (!encryptedPreferences(context).contains(SettingsKeys.RATIONALE)) {
            return null
        }
        return encryptedPreferences(context).getBoolean(SettingsKeys.RATIONALE, true)
    }

    fun updateShouldShowRationale(context: Context, value: Boolean) {
        encryptedPreferences(context).edit {
            apply {
                putBoolean(SettingsKeys.RATIONALE, value)
            }
        }
    }

    fun saveSettingsToEncryptedStorage(settings: AmberSettings) {
        val context = NostrSigner.instance
        encryptedPreferences(context).edit {
            apply {
                putStringSet(SettingsKeys.DEFAULT_RELAYS, settings.defaultRelays.map { it.url }.toSet())
                putStringSet(SettingsKeys.DEFAULT_PROFILE_RELAYS, settings.defaultProfileRelays.map { it.url }.toSet())
                putLong(SettingsKeys.LAST_BIOMETRICS_TIME, settings.lastBiometricsTime)
                putBoolean(SettingsKeys.USE_AUTH, settings.useAuth)
                putInt(SettingsKeys.BIOMETRICS_TYPE, settings.biometricsTimeType.screenCode)
                putBoolean(SettingsKeys.USE_PIN, settings.usePin)
                putBoolean(SettingsKeys.USE_PROXY, settings.useProxy)
                putInt(SettingsKeys.PROXY_PORT, settings.proxyPort)
            }
        }
    }

    fun getLastCheck(context: Context, npub: String): Long {
        return encryptedPreferences(context, npub).getLong(PrefKeys.LAST_CHECK, 0)
    }

    fun setLastCheck(context: Context, npub: String, time: Long) {
        encryptedPreferences(context, npub).edit {
            apply {
                putLong(PrefKeys.LAST_CHECK, time)
            }
        }
    }

    fun getLastMetadataUpdate(context: Context, npub: String): Long {
        return encryptedPreferences(context, npub).getLong(PrefKeys.LAST_METADATA_UPDATE, 0)
    }

    fun setLastMetadataUpdate(context: Context, npub: String, time: Long) {
        encryptedPreferences(context, npub).edit {
            apply {
                putLong(PrefKeys.LAST_METADATA_UPDATE, time)
            }
        }
    }

    fun loadPinFromEncryptedStorage(): String? {
        val context = NostrSigner.instance
        return encryptedPreferences(context).getString(SettingsKeys.PIN, null)
    }

    fun loadProfileUrlFromEncryptedStorage(npub: String): String? {
        val context = NostrSigner.instance
        return encryptedPreferences(context, npub).getString(PrefKeys.PROFILE_URL, null)
    }

    fun saveProfileUrlToEncryptedStorage(profileUrl: String?, npub: String) {
        val context = NostrSigner.instance
        encryptedPreferences(context, npub).edit {
            apply {
                if (profileUrl == null) {
                    remove(PrefKeys.PROFILE_URL)
                } else {
                    putString(PrefKeys.PROFILE_URL, profileUrl)
                }
            }
        }
    }

    fun savePinToEncryptedStorage(pin: String?) {
        val context = NostrSigner.instance
        encryptedPreferences(context).edit {
            apply {
                if (pin == null) {
                    remove(SettingsKeys.PIN)
                } else {
                    putString(SettingsKeys.PIN, pin)
                }
            }
        }
    }

    suspend fun loadSettingsFromEncryptedStorage(context: Context = NostrSigner.instance): AmberSettings {
        encryptedPreferences(context).apply {
            val proxyPort = getInt(SettingsKeys.PROXY_PORT, 9050)
            HttpClientManager.setDefaultProxyOnPort(proxyPort)

            return AmberSettings(
                defaultRelays = getStringSet(SettingsKeys.DEFAULT_RELAYS, null)?.map {
                    RelaySetupInfo(it, read = true, write = true, feedTypes = COMMON_FEED_TYPES)
                } ?: listOf(RelaySetupInfo("wss://relay.nsec.app", read = true, write = true, feedTypes = COMMON_FEED_TYPES)),
                defaultProfileRelays = getStringSet(SettingsKeys.DEFAULT_PROFILE_RELAYS, null)?.map {
                    RelaySetupInfo(it, read = true, write = false, feedTypes = COMMON_FEED_TYPES)
                } ?: listOf(
                    RelaySetupInfo("wss://relay.nostr.band", read = true, write = false, feedTypes = COMMON_FEED_TYPES),
                    RelaySetupInfo("wss://purplepag.es", read = true, write = false, feedTypes = COMMON_FEED_TYPES),
                ),
                lastBiometricsTime = getLong(SettingsKeys.LAST_BIOMETRICS_TIME, 0),
                useAuth = getBoolean(SettingsKeys.USE_AUTH, false),
                biometricsTimeType = parseBiometricsTimeType(getInt(SettingsKeys.BIOMETRICS_TYPE, 0)),
                usePin = getBoolean(SettingsKeys.USE_PIN, false),
                useProxy = getBoolean(SettingsKeys.USE_PROXY, false),
                proxyPort = getInt(SettingsKeys.PROXY_PORT, 9050),
            )
        }
    }

    private var savedAccounts: List<String>? = null

    private fun savedAccounts(context: Context): List<String> {
        if (savedAccounts == null) {
            savedAccounts = encryptedPreferences(context)
                .getString(SettingsKeys.SAVED_ACCOUNTS, null)?.split(COMMA) ?: listOf()
        }
        return savedAccounts!!
    }

    private fun updateSavedAccounts(context: Context, accounts: List<String>) {
        if (savedAccounts != accounts) {
            savedAccounts = accounts

            encryptedPreferences(context).edit {
                apply {
                    putString(SettingsKeys.SAVED_ACCOUNTS, accounts.joinToString(COMMA).ifBlank { null })
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
    fun updatePrefsForLogout(npub: String, context: Context): Boolean {
        accountCache.remove(npub)
        val userPrefs = encryptedPreferences(context, npub)
        userPrefs.edit(commit = true) { clear() }
        removeAccount(context, npub)
        deleteUserPreferenceFile(context, npub)

        if (savedAccounts(context).isEmpty()) {
            val appPrefs = encryptedPreferences(context)
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
        saveToEncryptedStorage(context, account)
    }

    fun saveToEncryptedStorage(context: Context, account: Account) {
        val prefs = encryptedPreferences(context = context, account.npub)
        prefs.edit {
            apply {
                account.signer.keyPair.privKey.let { putString(PrefKeys.NOSTR_PRIVKEY, it?.toHexKey()) }
                account.signer.keyPair.pubKey.let { putString(PrefKeys.NOSTR_PUBKEY, it.toHexKey()) }
                putString(PrefKeys.ACCOUNT_NAME, account.name)
                putString(PrefKeys.LANGUAGE_PREFS, account.language)
                putBoolean(PrefKeys.ALLOW_NEW_CONNECTIONS, account.allowNewConnections)
                putInt(PrefKeys.SIGN_POLICY, account.signPolicy)
                putString(PrefKeys.SEED_WORDS2, account.seedWords.joinToString(separator = " ") { it })
                putBoolean(PrefKeys.DID_BACKUP, account.didBackup)
            }
        }
    }

    fun loadFromEncryptedStorage(context: Context): Account? {
        return loadFromEncryptedStorage(context, currentAccount(context))
    }

    fun setAccountName(
        context: Context,
        npub: String,
        value: String,
    ) {
        encryptedPreferences(context, npub).edit {
            apply {
                putString(PrefKeys.ACCOUNT_NAME, value)
            }
        }
        accountCache.get(npub)?.let {
            it.name = value
        }
    }

    fun getAccountName(context: Context, npub: String): String {
        encryptedPreferences(context, npub).apply {
            return getString(PrefKeys.ACCOUNT_NAME, "") ?: ""
        }
    }

    suspend fun saveNcryptsec(
        npub: String,
        privateKeyHex: HexKey,
        password: String,
    ) {
        val context = NostrSigner.instance
        val ncryptsec = Nip49().encrypt(privateKeyHex, password)
        encryptedPreferences(context, npub).edit {
            apply {
                putString(PrefKeys.NCRYPT_SEC, ncryptsec)
            }
        }
    }

    suspend fun updateProxy(
        context: Context,
        useProxy: Boolean,
        port: Int,
    ) {
        encryptedPreferences(context).edit {
            apply {
                putBoolean(SettingsKeys.USE_PROXY, useProxy)
                putInt(SettingsKeys.PROXY_PORT, port)
            }
        }
        NostrSigner.instance.settings = loadSettingsFromEncryptedStorage()
        HttpClientManager.setDefaultProxyOnPort(port)
    }

    fun loadFromEncryptedStorage(context: Context, npub: String?): Account? {
        if (npub != null && accountCache.get(npub) != null) {
            return accountCache.get(npub)
        }
        encryptedPreferences(context, npub).apply {
            val pubKey = getString(PrefKeys.NOSTR_PUBKEY, null) ?: return null
            val privKey = getString(PrefKeys.NOSTR_PRIVKEY, null)
            val name = getString(PrefKeys.ACCOUNT_NAME, "") ?: ""
            val language = getString(PrefKeys.LANGUAGE_PREFS, null)
            val allowNewConnections = getBoolean(PrefKeys.ALLOW_NEW_CONNECTIONS, false)
            val signPolicy = getInt(PrefKeys.SIGN_POLICY, 1)
            val savedSeedWords = getString(PrefKeys.SEED_WORDS2, null)
            val seedWords = savedSeedWords?.split(" ")?.toSet() ?: emptySet()
            val didBackup = getBoolean(PrefKeys.DID_BACKUP, true)

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

    suspend fun migrateTorSettings(context: Context) {
        val useProxy = allSavedAccounts(context).any {
            encryptedPreferences(context, it.npub).getBoolean(SettingsKeys.USE_PROXY, false)
        }
        val proxyPort: Int = allSavedAccounts(context).firstNotNullOfOrNull {
            if (encryptedPreferences(context, it.npub).getBoolean(SettingsKeys.USE_PROXY, false)) {
                encryptedPreferences(context, it.npub).getInt(SettingsKeys.PROXY_PORT, 9050)
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
