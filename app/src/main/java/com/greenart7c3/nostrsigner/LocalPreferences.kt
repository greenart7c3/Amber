package com.greenart7c3.nostrsigner

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.HistoryDatabase
import com.greenart7c3.nostrsigner.database.LogDatabase
import com.greenart7c3.nostrsigner.database.LogRetentionManager
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.models.ProfileFetchInterval
import com.greenart7c3.nostrsigner.models.RetentionPeriod
import com.greenart7c3.nostrsigner.models.TorMode
import com.greenart7c3.nostrsigner.models.UpdateChannel
import com.greenart7c3.nostrsigner.models.UpdateCheckFrequency
import com.greenart7c3.nostrsigner.models.defaultAppRelays
import com.greenart7c3.nostrsigner.models.defaultIndexerRelays
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.greenart7c3.nostrsigner.service.TorManager
import com.greenart7c3.nostrsigner.ui.parseBiometricsTimeType
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.cache.LargeCache
import java.io.File
import java.security.InvalidKeyException
import java.security.KeyStoreException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

private enum class PrefKeys(val key: String) {
    NOSTR_PUBKEY("nostr_pubkey"),
    ACCOUNT_NAME("account_name"),
    SIGN_POLICY("default_sign_policy"),
    PROFILE_URL("profile_url"),
    LAST_METADATA_UPDATE("last_metadata_update"),
    METADATA_CREATED_AT("metadata_created_at"),
    LAST_CHECK("last_check"),
    USER_RELAYS("user_relays"),
    USER_RELAYS_CREATED_AT("user_relays_created_at"),
    DID_BACKUP("did_backup"),
    BACKUP_APPLICATIONS("backup_applications"),
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
    TOR_MODE("tor_mode"),
    BATERRY_OPTIMIZATION("battery_optimization"),
    KILL_SWITCH("kill_switch"),
    LANGUAGE_PREFS("languagePreferences"),
    AUTH_WHITELIST("auth_whitelist"),
    AUTO_CHECK_UPDATES("auto_check_updates"),
    UPDATE_CHECK_FREQUENCY("update_check_frequency"),
    UPDATE_CHANNEL("update_channel"),
    LAST_UPDATE_CHECK_TIME("last_update_check_time"),
    START_SERVICE_ON_BOOT("start_service_on_boot"),
    PRIVACY_MODE("privacy_mode"),
    WEBDAV_URL("webdav_url"),
    WEBDAV_USERNAME("webdav_username"),
    WEBDAV_PASSWORD_ENCRYPTED("webdav_password_encrypted"),
    WEBDAV_FILENAME("webdav_filename"),
    GDRIVE_FOLDER_URI("gdrive_folder_uri"),
    RATE_LIMIT_ENABLED("rate_limit_enabled"),
    RATE_LIMIT_MAX_PER_WINDOW("rate_limit_max_per_window"),
    RATE_LIMIT_WINDOW_SECONDS("rate_limit_window_seconds"),
    PROFILE_FETCH_INTERVAL("profile_fetch_interval"),
    LOG_RETENTION("log_retention"),
    HISTORY_RETENTION("history_retention"),
    TRUST_SCORE_ENABLED("trust_score_enabled_v2"),
    REQUIRE_UNLOCKED_DEVICE("require_unlocked_device"),
    ACTIVE_KEYSTORE_ALIAS("active_keystore_alias"),
}

@Immutable
data class AccountInfo(
    val npub: String,
    val hasPrivKey: Boolean,
)

object LocalPreferences {
    private const val COMMA = ","

    // Pre-rename consent key, kept only so loadSettingsFromEncryptedStorage can
    // scrub it. Never read.
    private const val LEGACY_TRUST_SCORE_ENABLED_KEY = "trust_score_enabled"
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
            AmberLog.d(Amber.TAG, "currentAccount is null fetching from shared preferences")
            currentAccount = sharedPrefs(context).getString(SettingsKeys.CURRENT_ACCOUNT.key, null)
            if (currentAccount == null) {
                AmberLog.d(Amber.TAG, "currentAccount is null fetching from all saved accounts")
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
                putString(SettingsKeys.TOR_MODE.key, settings.torMode.name)
                putInt(SettingsKeys.PROXY_PORT.key, settings.proxyPort)
                putBoolean(SettingsKeys.KILL_SWITCH.key, settings.killSwitch.value)
                putString(SettingsKeys.LANGUAGE_PREFS.key, settings.language)
                putStringSet(SettingsKeys.AUTH_WHITELIST.key, settings.authWhitelist.toSet())
                putBoolean(SettingsKeys.START_SERVICE_ON_BOOT.key, settings.startServiceOnBoot)
                putBoolean(SettingsKeys.PRIVACY_MODE.key, settings.privacyMode)
                putBoolean(SettingsKeys.RATE_LIMIT_ENABLED.key, settings.rateLimitEnabled)
                putInt(SettingsKeys.RATE_LIMIT_MAX_PER_WINDOW.key, settings.rateLimitMaxPerWindow)
                putInt(SettingsKeys.RATE_LIMIT_WINDOW_SECONDS.key, settings.rateLimitWindowSeconds)
                putBoolean(SettingsKeys.TRUST_SCORE_ENABLED.key, settings.trustScoreEnabled)
            }
        }
    }

    /**
     * Read synchronously, not from [Amber.settings], so pruning that runs before the
     * async settings load never applies the default (possibly shorter) period and
     * deletes rows the user chose to keep.
     */
    fun getLogRetention(context: Context): RetentionPeriod = getRetention(context, SettingsKeys.LOG_RETENTION, RetentionPeriod.SEVEN_DAYS)

    fun getHistoryRetention(context: Context): RetentionPeriod = getRetention(context, SettingsKeys.HISTORY_RETENTION, RetentionPeriod.THIRTY_DAYS)

    private fun getRetention(context: Context, key: SettingsKeys, default: RetentionPeriod): RetentionPeriod = try {
        RetentionPeriod.valueOf(sharedPrefs(context).getString(key.key, default.name)!!)
    } catch (_: IllegalArgumentException) {
        default
    }

    fun getBatteryOptimization(context: Context): Boolean = sharedPrefs(context).getBoolean(SettingsKeys.BATERRY_OPTIMIZATION.key, false)

    /**
     * The only source of the device-lock flag. It is written together with the
     * active Keystore alias ([commitActiveKeystoreAlias]) so it always matches
     * the key actually in use, and it is read synchronously, so it is correct
     * before the async settings load (offline flavor, WorkManager or
     * ContentProvider cold start) and can't be overwritten by a stale settings
     * copy saved while a key rotation is running.
     */
    fun getRequireUnlockedDevice(context: Context): Boolean = sharedPrefs(context).getBoolean(SettingsKeys.REQUIRE_UNLOCKED_DEVICE.key, false)

    fun updateBatteryOptimization(context: Context, value: Boolean) {
        sharedPrefs(context).edit {
            apply {
                putBoolean(SettingsKeys.BATERRY_OPTIMIZATION.key, value)
            }
        }
    }

    fun getBackupApplications(context: Context, npub: String): Boolean = sharedPrefs(context, npub).getBoolean(PrefKeys.BACKUP_APPLICATIONS.key, false)

    fun setBackupApplications(context: Context, npub: String, enabled: Boolean) {
        sharedPrefs(context, npub).edit {
            apply {
                putBoolean(PrefKeys.BACKUP_APPLICATIONS.key, enabled)
            }
        }
    }

    fun anyAccountBackupEnabled(context: Context): Boolean = allSavedAccounts(context).any { getBackupApplications(context, it.npub) }

    fun getLastCheck(context: Context, npub: String): Long = sharedPrefs(context, npub).getLong(PrefKeys.LAST_CHECK.key, 0)

    fun setLastCheck(context: Context, npub: String, time: Long) {
        sharedPrefs(context, npub).edit {
            apply {
                putLong(PrefKeys.LAST_CHECK.key, time)
            }
        }
    }

    fun getUserRelays(context: Context, npub: String): List<NormalizedRelayUrl> = sharedPrefs(context, npub).getStringSet(PrefKeys.USER_RELAYS.key, null)?.mapNotNull {
        RelayUrlNormalizer.normalizeOrNull(it)
    } ?: emptyList()

    fun getUserRelaysCreatedAt(context: Context, npub: String): Long = sharedPrefs(context, npub).getLong(PrefKeys.USER_RELAYS_CREATED_AT.key, 0)

    fun setUserRelays(context: Context, npub: String, relays: List<NormalizedRelayUrl>, createdAt: Long) {
        sharedPrefs(context, npub).edit {
            apply {
                putStringSet(PrefKeys.USER_RELAYS.key, relays.map { it.url }.toSet())
                putLong(PrefKeys.USER_RELAYS_CREATED_AT.key, createdAt)
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

    /** created_at of the kind-0 event the account's name/picture were last taken from. */
    fun getMetadataCreatedAt(context: Context, npub: String): Long = sharedPrefs(context, npub).getLong(PrefKeys.METADATA_CREATED_AT.key, 0)

    fun setMetadataCreatedAt(context: Context, npub: String, createdAt: Long) {
        sharedPrefs(context, npub).edit {
            apply {
                putLong(PrefKeys.METADATA_CREATED_AT.key, createdAt)
            }
        }
    }

    suspend fun loadPinFromEncryptedStorage(): String? {
        val context = Amber.instance
        // Migration: move PIN from plain SharedPreferences to encrypted DataStore
        val legacyPin = sharedPrefs(context).getString(SettingsKeys.PIN.key, null)
        if (legacyPin != null) {
            DataStoreAccess.savePin(context, legacyPin)
            sharedPrefs(context).edit { remove(SettingsKeys.PIN.key) }
        }
        return DataStoreAccess.loadPin(context)
    }

    suspend fun savePinToEncryptedStorage(pin: String?) {
        val context = Amber.instance
        DataStoreAccess.savePin(context, pin)
    }

    suspend fun reloadApp() {
        val context = Amber.instance
        currentAccount = null
        savedAccounts = null
        accountCache.clear()
        warmAccountCache(context)
        context.settings = loadSettingsFromEncryptedStorage(context)
        context.isSettingsLoaded = true
        context.settings.language?.let {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(it),
            )
        }
    }

    /**
     * Synchronously presets the OkHttp proxy from plain (unencrypted) prefs, before any
     * dial can happen. The full settings load is async; until it lands, the relay/Coil
     * factories treat torMode as unknown and route through the proxy client — which only
     * protects Tor users if that client already carries the SOCKS proxy (fail-closed
     * placeholder for BUILTIN, the configured port for ORBOT).
     */
    fun presetProxyFromPrefs(context: Context) {
        val prefs = sharedPrefs(context)
        val torMode = try {
            TorMode.valueOf(prefs.getString(SettingsKeys.TOR_MODE.key, TorMode.DISABLED.name)!!)
        } catch (_: IllegalArgumentException) {
            TorMode.DISABLED
        }
        when (torMode) {
            TorMode.ORBOT -> HttpClientManager.setDefaultProxyOnPort(
                prefs.getInt(SettingsKeys.PROXY_PORT.key, 9050),
            )
            TorMode.BUILTIN -> HttpClientManager.setDefaultProxyOnPort(TorManager.socksPort.value)
            TorMode.DISABLED -> {}
        }
    }

    fun getStartServiceOnBoot(context: Context): Boolean = sharedPrefs(context).getBoolean(SettingsKeys.START_SERVICE_ON_BOOT.key, true)

    fun loadSettingsFromEncryptedStorage(context: Context = Amber.instance): AmberSettings {
        checkNotInMainThread()
        return readSettings(context)
    }

    /**
     * Loads the settings in `Amber.onCreate`, on the main thread, before any
     * activity, service or ContentProvider call can read them. Otherwise an
     * approval screen or provider call on a cold start saw the data-class
     * defaults (an empty auth whitelist, rate limits on, Tor off…) until the
     * async [reloadApp] landed. They are plain SharedPreferences, like
     * [presetProxyFromPrefs], so this is a small synchronous read.
     */
    fun loadSettingsAtStartup(context: Context): AmberSettings = readSettings(context)

    private fun readSettings(context: Context): AmberSettings {
        sharedPrefs(context).apply {
            // One-time cleanup: the pre-rename legacy key is never read again, so
            // remove it instead of leaving a stale opt-in bit in every prefs file.
            // It must run before the return below.
            if (contains(LEGACY_TRUST_SCORE_ENABLED_KEY)) {
                edit { remove(LEGACY_TRUST_SCORE_ENABLED_KEY) }
            }

            // Migrate old use_proxy boolean to TorMode enum
            val torMode = if (contains(SettingsKeys.TOR_MODE.key)) {
                try {
                    TorMode.valueOf(getString(SettingsKeys.TOR_MODE.key, TorMode.DISABLED.name)!!)
                } catch (_: IllegalArgumentException) {
                    TorMode.DISABLED
                }
            } else if (getBoolean(SettingsKeys.USE_PROXY.key, false)) {
                TorMode.ORBOT
            } else {
                TorMode.DISABLED
            }

            val proxyPort = getInt(SettingsKeys.PROXY_PORT.key, 9050)
            when (torMode) {
                TorMode.ORBOT -> HttpClientManager.setDefaultProxyOnPort(proxyPort)
                // For built-in Tor, set a fail-closed placeholder proxy so no clearnet
                // traffic can leak before TorManager configures the real SOCKS port.
                TorMode.BUILTIN -> HttpClientManager.setDefaultProxyOnPort(TorManager.socksPort.value)
                TorMode.DISABLED -> {}
            }

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
                torMode = torMode,
                proxyPort = proxyPort,
                killSwitch = MutableStateFlow(getBoolean(SettingsKeys.KILL_SWITCH.key, false)),
                language = getString(SettingsKeys.LANGUAGE_PREFS.key, null),
                authWhitelist = getStringSet(SettingsKeys.AUTH_WHITELIST.key, null)?.toList() ?: emptyList(),
                autoCheckUpdates = getBoolean(SettingsKeys.AUTO_CHECK_UPDATES.key, true),
                updateCheckFrequency = try {
                    UpdateCheckFrequency.valueOf(
                        getString(SettingsKeys.UPDATE_CHECK_FREQUENCY.key, UpdateCheckFrequency.DAILY.name)!!,
                    )
                } catch (_: IllegalArgumentException) {
                    UpdateCheckFrequency.DAILY
                },
                updateChannel = try {
                    UpdateChannel.valueOf(
                        getString(SettingsKeys.UPDATE_CHANNEL.key, UpdateChannel.STABLE.name)!!,
                    )
                } catch (_: IllegalArgumentException) {
                    UpdateChannel.STABLE
                },
                startServiceOnBoot = getBoolean(SettingsKeys.START_SERVICE_ON_BOOT.key, true),
                privacyMode = getBoolean(SettingsKeys.PRIVACY_MODE.key, false),
                rateLimitEnabled = getBoolean(SettingsKeys.RATE_LIMIT_ENABLED.key, true),
                rateLimitMaxPerWindow = getInt(SettingsKeys.RATE_LIMIT_MAX_PER_WINDOW.key, 5),
                rateLimitWindowSeconds = getInt(SettingsKeys.RATE_LIMIT_WINDOW_SECONDS.key, 30),
                profileFetchInterval = try {
                    ProfileFetchInterval.valueOf(
                        getString(SettingsKeys.PROFILE_FETCH_INTERVAL.key, ProfileFetchInterval.FIFTEEN_MINUTES.name)!!,
                    )
                } catch (_: IllegalArgumentException) {
                    ProfileFetchInterval.FIFTEEN_MINUTES
                },
                trustScoreEnabled = getBoolean(SettingsKeys.TRUST_SCORE_ENABLED.key, false),
                logRetention = getLogRetention(context),
                historyRetention = getHistoryRetention(context),
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
                    AmberLog.d(Amber.TAG, "deleted $it: $result")
                } catch (e: Exception) {
                    AmberLog.d(Amber.TAG, "failed to delete $it", e)
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
        // Defense-in-depth (GHSA-8844-q5vh-9j8f, I3): best-effort zeroize the
        // private-key byte array of the logging-out account before dropping
        // the reference from the cache, so the key material does not linger
        // in heap until GC. KeyPair.privKey is a `val` referencing a mutable
        // ByteArray, so we overwrite its contents in place.
        accountCache.get(npub)?.signer?.keyPair?.privKey?.fill(0)
        accountCache.remove(npub)
        // Close Room handles BEFORE deleting on-disk preference/data files so
        // we release file locks and worker-pool threads instead of leaking
        // them for the lifetime of the process.
        Amber.instance.closeDatabasesFor(npub)
        // Logout deletes all local information for the account, as the logout
        // dialog says: dropping the databases (with their -wal/-shm/-journal
        // files) means re-importing the key starts with no connections, grants,
        // history or logs instead of silently restoring them.
        deleteAccountDatabases(context, npub)
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

    internal fun deleteAccountDatabases(context: Context, npub: String) {
        listOf(AppDatabase.fileName(npub), LogDatabase.fileName(npub), HistoryDatabase.fileName(npub)).forEach { name ->
            if (!context.deleteDatabase(name) && context.getDatabasePath(name).exists()) {
                AmberLog.e(Amber.TAG, "Could not delete database $name at logout")
            }
        }
    }

    suspend fun updatePrefsForLogin(
        context: Context,
        account: Account,
        pubKey: String?,
        privKey: String?,
        seedWords: String?,
    ) {
        saveToEncryptedStorage(context, account, pubKey, privKey, seedWords)
        setCurrentAccount(context, account)
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

    /**
     * Eagerly decrypts and caches every saved account.
     *
     * The account switcher in the approval screens (LoginWithPubKey,
     * BunkerConnectRequestScreen) reads allCachedAccounts() synchronously
     * inside remember {}, so the cache must be fully populated before the
     * first approval screen composes. AccountStateViewModel calls this at
     * app start — the only deterministic startup point that covers both
     * flavors (ConnectivityService runs reloadApp() asynchronously and does
     * not exist in the offline flavor). loadFromEncryptedStorageSync stays
     * lazy for the SignerProvider / NIP-46 paths that only ever need one
     * known npub (GHSA-8844-q5vh-9j8f, I3).
     */
    suspend fun warmAccountCache(context: Context) {
        allSavedAccounts(context).forEach {
            loadFromEncryptedStorage(context, it.npub)
        }
    }

    suspend fun allAccounts(context: Context): List<Account> {
        val accountInfos = allSavedAccounts(context)
        return accountInfos.mapNotNull {
            loadFromEncryptedStorage(context, it.npub)
        }
    }

    fun loadFromEncryptedStorageSync(context: Context, npub: String? = null): Account? {
        // Defense-in-depth (GHSA-8844-q5vh-9j8f, I3): previously this warmed
        // the entire cache (runBlocking over all saved accounts) on any cold
        // cache / size mismatch, decrypting every account's private key
        // process-wide on the first IPC/relay lookup. Lazy-load only the
        // requested account. The UI account switcher relies on
        // allCachedAccounts() being populated, which warmAccountCache()
        // handles at app start (called from AccountStateViewModel); this
        // path is for direct lookups (SignerProvider / NIP-46)
        // that always know which npub they want.
        val targetNpub = npub ?: currentAccount(context) ?: return null
        accountCache.get(targetNpub)?.let { return it }
        if (!containsAccount(context, targetNpub)) return null
        return runBlocking { loadFromEncryptedStorage(context, targetNpub) }
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

    fun getProfileUrl(context: Context, npub: String): String {
        sharedPrefs(context, npub).apply {
            return getString(PrefKeys.PROFILE_URL.key, "") ?: ""
        }
    }

    fun updateProxy(
        context: Context,
        useProxy: Boolean,
        port: Int,
    ) {
        val torMode = if (useProxy) TorMode.ORBOT else TorMode.DISABLED
        updateTorMode(context, torMode, port)
    }

    fun updateTorMode(
        context: Context,
        torMode: TorMode,
        port: Int = Amber.instance.settings.proxyPort,
    ) {
        sharedPrefs(context).edit {
            apply {
                putString(SettingsKeys.TOR_MODE.key, torMode.name)
                putInt(SettingsKeys.PROXY_PORT.key, port)
            }
        }
        Amber.instance.settings = loadSettingsFromEncryptedStorage()
        when (torMode) {
            TorMode.ORBOT -> HttpClientManager.setDefaultProxyOnPort(port)
            TorMode.DISABLED -> HttpClientManager.clearProxy()
            // Fail-closed placeholder; TorManager will update to the real port once running.
            TorMode.BUILTIN -> HttpClientManager.setDefaultProxyOnPort(TorManager.socksPort.value)
        }
    }

    fun updateAutoCheckUpdates(context: Context, enabled: Boolean) {
        sharedPrefs(context).edit {
            apply {
                putBoolean(SettingsKeys.AUTO_CHECK_UPDATES.key, enabled)
            }
        }
        Amber.instance.settings = loadSettingsFromEncryptedStorage()
    }

    fun updateStartServiceOnBoot(context: Context, enabled: Boolean) {
        sharedPrefs(context).edit {
            apply {
                putBoolean(SettingsKeys.START_SERVICE_ON_BOOT.key, enabled)
            }
        }
        Amber.instance.settings = loadSettingsFromEncryptedStorage()
    }

    /**
     * Toggles privacy mode. When enabled, immediately wipes all existing logs and
     * activity history for every account so no previously collected data survives.
     * Future writes are blocked at the DAO layer while this setting stays on.
     */
    suspend fun updatePrivacyMode(context: Context, enabled: Boolean) {
        sharedPrefs(context).edit {
            apply {
                putBoolean(SettingsKeys.PRIVACY_MODE.key, enabled)
            }
        }
        Amber.instance.settings = loadSettingsFromEncryptedStorage()
        if (enabled) {
            allSavedAccounts(context).forEach {
                try {
                    Amber.instance.getLogDatabase(it.npub).dao().clearLogs()
                    Amber.instance.getHistoryDatabase(it.npub).dao().clearHistory()
                } catch (e: Exception) {
                    AmberLog.e(Amber.TAG, "Error wiping data for privacy mode", e)
                }
            }
        }
    }

    /**
     * Toggles the opt-in "require unlocked device" key policy (GHSA-8844-q5vh-9j8f, L1).
     * The Keystore flag is set at key-generation time, so changing the setting
     * requires rotating to a new wrapping key. [SecureCryptoHelper.rotateKey]
     * persists the setting together with the new active key alias.
     */
    suspend fun updateRequireUnlockedDevice(context: Context, enabled: Boolean) {
        SecureCryptoHelper.rotateKey(context, requireUnlockedDevice = enabled)
        Amber.instance.settings = loadSettingsFromEncryptedStorage()
    }

    /** Keystore alias currently used to encrypt new secrets, or null if never recorded. */
    fun getActiveKeystoreAlias(context: Context): String? = sharedPrefs(context).getString(SettingsKeys.ACTIVE_KEYSTORE_ALIAS.key, null)

    /**
     * Durably (synchronous `commit`) records the active Keystore alias and,
     * when given, the matching key policy in one atomic write, so the
     * setting can never disagree with the key that is actually in use.
     * Returns false if the write did not reach disk. Not the KTX `edit {}`:
     * it discards `commit()`'s result, which key rotation depends on.
     */
    @SuppressLint("UseKtx")
    fun commitActiveKeystoreAlias(context: Context, alias: String, requireUnlockedDevice: Boolean? = null): Boolean = sharedPrefs(context).edit()
        .putString(SettingsKeys.ACTIVE_KEYSTORE_ALIAS.key, alias)
        .apply { requireUnlockedDevice?.let { putBoolean(SettingsKeys.REQUIRE_UNLOCKED_DEVICE.key, it) } }
        .commit()

    fun updateUpdateCheckFrequency(context: Context, frequency: UpdateCheckFrequency) {
        sharedPrefs(context).edit {
            apply {
                putString(SettingsKeys.UPDATE_CHECK_FREQUENCY.key, frequency.name)
            }
        }
        Amber.instance.settings = loadSettingsFromEncryptedStorage()
    }

    fun updateProfileFetchInterval(context: Context, interval: ProfileFetchInterval) {
        sharedPrefs(context).edit {
            apply {
                putString(SettingsKeys.PROFILE_FETCH_INTERVAL.key, interval.name)
            }
        }
        Amber.instance.settings = loadSettingsFromEncryptedStorage()
    }

    suspend fun updateLogRetention(context: Context, retention: RetentionPeriod) {
        sharedPrefs(context).edit {
            apply {
                putString(SettingsKeys.LOG_RETENTION.key, retention.name)
            }
        }
        Amber.instance.settings = loadSettingsFromEncryptedStorage()
        pruneAllAccounts(context)
    }

    suspend fun updateHistoryRetention(context: Context, retention: RetentionPeriod) {
        sharedPrefs(context).edit {
            apply {
                putString(SettingsKeys.HISTORY_RETENTION.key, retention.name)
            }
        }
        Amber.instance.settings = loadSettingsFromEncryptedStorage()
        pruneAllAccounts(context)
    }

    // Applies a shortened retention period right away instead of at the next cleanup.
    private suspend fun pruneAllAccounts(context: Context) {
        allSavedAccounts(context).forEach {
            try {
                LogRetentionManager.prune(it.npub)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AmberLog.e(Amber.TAG, "Error pruning logs for ${it.npub}", e)
            }
        }
    }

    fun updateUpdateChannel(context: Context, channel: UpdateChannel) {
        sharedPrefs(context).edit {
            apply {
                putString(SettingsKeys.UPDATE_CHANNEL.key, channel.name)
            }
        }
        Amber.instance.settings = loadSettingsFromEncryptedStorage()
    }

    fun getLastUpdateCheckTime(context: Context): Long = sharedPrefs(context).getLong(SettingsKeys.LAST_UPDATE_CHECK_TIME.key, 0L)

    fun setLastUpdateCheckTime(context: Context, time: Long) {
        sharedPrefs(context).edit {
            apply {
                putLong(SettingsKeys.LAST_UPDATE_CHECK_TIME.key, time)
            }
        }
    }

    // WebDAV settings
    fun getWebDavUrl(context: Context): String = sharedPrefs(context).getString(SettingsKeys.WEBDAV_URL.key, "") ?: ""

    fun getWebDavUsername(context: Context): String = sharedPrefs(context).getString(SettingsKeys.WEBDAV_USERNAME.key, "") ?: ""

    fun getWebDavFilename(context: Context): String = sharedPrefs(context).getString(SettingsKeys.WEBDAV_FILENAME.key, "amber_backup.txt") ?: "amber_backup.txt"

    suspend fun getWebDavPassword(context: Context): String {
        val encrypted = getWebDavPasswordEncrypted(context)
        return if (encrypted.isBlank()) {
            ""
        } else {
            try {
                SecureCryptoHelper.decrypt(encrypted)
            } catch (e: Exception) {
                ""
            }
        }
    }

    /**
     * Returns the still-encrypted WebDAV password (raw SharedPreferences
     * value). Used by [SecureCryptoHelper.rotateKey] to avoid mutex
     * reentrancy.
     */
    fun getWebDavPasswordEncrypted(context: Context): String = sharedPrefs(context).getString(SettingsKeys.WEBDAV_PASSWORD_ENCRYPTED.key, "") ?: ""

    /**
     * Durably replaces the encrypted WebDAV password with [encryptedPassword]
     * only if it still equals [expected]. Used by [SecureCryptoHelper.rotateKey].
     */
    @Synchronized
    @SuppressLint("UseKtx") // needs commit()'s result, see commitActiveKeystoreAlias
    fun replaceWebDavPasswordEncrypted(context: Context, expected: String, encryptedPassword: String): Boolean {
        if (getWebDavPasswordEncrypted(context) != expected) return false
        return sharedPrefs(context).edit()
            .putString(SettingsKeys.WEBDAV_PASSWORD_ENCRYPTED.key, encryptedPassword)
            .commit()
    }

    suspend fun saveWebDavSettings(
        context: Context,
        url: String,
        username: String,
        password: String,
        fileName: String,
    ) {
        val encryptedPassword = if (password.isBlank()) "" else SecureCryptoHelper.encrypt(password)
        sharedPrefs(context).edit {
            apply {
                putString(SettingsKeys.WEBDAV_URL.key, url)
                putString(SettingsKeys.WEBDAV_USERNAME.key, username)
                putString(SettingsKeys.WEBDAV_PASSWORD_ENCRYPTED.key, encryptedPassword)
                putString(SettingsKeys.WEBDAV_FILENAME.key, fileName)
            }
        }
    }

    // Google Drive folder URI (persisted SAF permission)
    fun getGdriveFolderUri(context: Context): String = sharedPrefs(context).getString(SettingsKeys.GDRIVE_FOLDER_URI.key, "") ?: ""

    fun saveGdriveFolderUri(context: Context, uri: String) {
        sharedPrefs(context).edit {
            apply { putString(SettingsKeys.GDRIVE_FOLDER_URI.key, uri) }
        }
    }

    /**
     * With requireUnlockedDevice on, the wrapping key refuses every operation
     * while the device is locked. That is the setting working, not a broken
     * key, so it must not be reported as one (the report tells the user to log
     * out and back in).
     */
    private fun isKeystoreLockedOut(context: Context): Boolean = getRequireUnlockedDevice(context) &&
        context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true

    suspend fun loadFromEncryptedStorage(context: Context, npub: String): Account? {
        if (accountCache.get(npub) != null) {
            return accountCache.get(npub)
        }
        if (!containsAccount(context, npub)) {
            return null
        }
        val privKey = try {
            DataStoreAccess.getEncryptedKey(
                context,
                npub,
                DataStoreAccess.NOSTR_PRIVKEY,
            )
        } catch (e: InvalidKeyException) {
            if (isKeystoreLockedOut(context)) {
                AmberLog.d(Amber.TAG, "Device locked: account $npub loads after unlock")
                return null
            }
            AmberLog.e(Amber.TAG, "AndroidKeyStore key for $npub is broken (device KeyMint may not support key upgrade). Account skipped.", e)
            Amber.instance.keystoreFailedAccounts.value = (Amber.instance.keystoreFailedAccounts.value + npub).distinct()
            return null
        } catch (e: KeyStoreException) {
            if (isKeystoreLockedOut(context)) {
                AmberLog.d(Amber.TAG, "Device locked: account $npub loads after unlock")
                return null
            }
            AmberLog.e(Amber.TAG, "KeyStore operation failed for $npub. Account skipped.", e)
            Amber.instance.keystoreFailedAccounts.value = (Amber.instance.keystoreFailedAccounts.value + npub).distinct()
            return null
        }
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
            if (npub in Amber.instance.keystoreFailedAccounts.value) {
                Amber.instance.keystoreFailedAccounts.value -= npub
            }
            return account
        }
    }
}
