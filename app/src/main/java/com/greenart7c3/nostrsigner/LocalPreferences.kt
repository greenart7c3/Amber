package com.greenart7c3.nostrsigner

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.database.LogRetentionManager
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.models.ProfileFetchInterval
import com.greenart7c3.nostrsigner.models.RetentionPeriod
import com.greenart7c3.nostrsigner.models.ThemeMode
import com.greenart7c3.nostrsigner.models.TorMode
import com.greenart7c3.nostrsigner.models.UpdateChannel
import com.greenart7c3.nostrsigner.models.UpdateCheckFrequency
import com.greenart7c3.nostrsigner.models.defaultAppRelays
import com.greenart7c3.nostrsigner.models.defaultIndexerRelays
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.greenart7c3.nostrsigner.service.TorManager
import com.greenart7c3.nostrsigner.ui.parseBiometricsTimeType
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

private enum class SettingsKeys(val key: String) {
    DEFAULT_RELAYS("default_relays"),
    DEFAULT_PROFILE_RELAYS("default_profile_relays"),
    LAST_BIOMETRICS_TIME("last_biometrics_time"),
    USE_AUTH("use_auth"),
    BIOMETRICS_TYPE("biometrics_type"),
    USE_PIN("use_pin"),
    RATIONALE("rationale"),
    PIN("pin"),
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
    RATE_LIMIT_ENABLED("rate_limit_enabled"),
    RATE_LIMIT_MAX_PER_WINDOW("rate_limit_max_per_window"),
    RATE_LIMIT_WINDOW_SECONDS("rate_limit_window_seconds"),
    PROFILE_FETCH_INTERVAL("profile_fetch_interval"),
    LOG_RETENTION("log_retention"),
    HISTORY_RETENTION("history_retention"),
    TRUST_SCORE_ENABLED("trust_score_enabled_v2"),
    THEME_MODE("theme_mode"),
    SORT_APPLICATIONS_ALPHABETICALLY("sort_applications_alphabetically"),
    LOCAL_BUNKER_SERVER("local_bunker_server"),
    LOCAL_BUNKER_PORT("local_bunker_port"),
}

object LocalPreferences {
    // The settings in effect (updated after every write) and the per-account databases;
    // Amber's constructor installs them before anything reads the settings.
    private lateinit var settingsStore: SettingsStore
    private lateinit var accountRepository: AccountRepository

    fun install(settings: SettingsStore, accounts: AccountRepository) {
        settingsStore = settings
        accountRepository = accounts
    }

    // Pre-rename consent key, kept only so loadSettingsFromEncryptedStorage can
    // scrub it. Never read.
    private const val LEGACY_TRUST_SCORE_ENABLED_KEY = "trust_score_enabled"

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

    fun saveSettingsToEncryptedStorage(context: Context, settings: AmberSettings) {
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
                putBoolean(SettingsKeys.SORT_APPLICATIONS_ALPHABETICALLY.key, settings.sortApplicationsAlphabetically)
                putBoolean(SettingsKeys.LOCAL_BUNKER_SERVER.key, settings.localBunkerServer)
                val localBunkerPort = settings.localBunkerPort
                if (localBunkerPort != null) putInt(SettingsKeys.LOCAL_BUNKER_PORT.key, localBunkerPort) else remove(SettingsKeys.LOCAL_BUNKER_PORT.key)
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

    private fun getRetention(context: Context, key: SettingsKeys, default: RetentionPeriod): RetentionPeriod = sharedPrefs(context).enumOr(key, default)

    /** Read synchronously so the first frame of each activity already uses the chosen theme. */
    fun getThemeMode(context: Context): ThemeMode = sharedPrefs(context).enumOr(SettingsKeys.THEME_MODE, ThemeMode.SYSTEM)

    fun updateThemeMode(context: Context, mode: ThemeMode) {
        sharedPrefs(context).edit {
            apply {
                putString(SettingsKeys.THEME_MODE.key, mode.name)
            }
        }
        settingsStore.themeMode.value = mode
    }

    fun getBatteryOptimization(context: Context): Boolean = sharedPrefs(context).getBoolean(SettingsKeys.BATERRY_OPTIMIZATION.key, false)

    fun updateBatteryOptimization(context: Context, value: Boolean) {
        sharedPrefs(context).edit {
            apply {
                putBoolean(SettingsKeys.BATERRY_OPTIMIZATION.key, value)
            }
        }
    }

    suspend fun loadPinFromEncryptedStorage(context: Context): String? {
        // Migration: move PIN from plain SharedPreferences to encrypted DataStore
        val legacyPin = sharedPrefs(context).getString(SettingsKeys.PIN.key, null)
        if (legacyPin != null) {
            DataStoreAccess.savePin(context, legacyPin)
            sharedPrefs(context).edit { remove(SettingsKeys.PIN.key) }
        }
        return DataStoreAccess.loadPin(context)
    }

    suspend fun savePinToEncryptedStorage(context: Context, pin: String?) {
        DataStoreAccess.savePin(context, pin)
    }

    suspend fun reloadApp(context: Context) {
        AccountStorage.invalidateCaches()
        AccountStorage.all(context)
        settingsStore.settings = loadSettingsFromEncryptedStorage(context)
        settingsStore.settings.language?.let { AppLanguage.apply(it) }
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
        applyProxy(prefs.enumOr(SettingsKeys.TOR_MODE, TorMode.DISABLED), prefs.getInt(SettingsKeys.PROXY_PORT.key, 9050))
    }

    fun getStartServiceOnBoot(context: Context): Boolean = sharedPrefs(context).getBoolean(SettingsKeys.START_SERVICE_ON_BOOT.key, true)

    fun loadSettingsFromEncryptedStorage(context: Context): AmberSettings {
        checkNotInMainThread()
        return readSettings(context)
    }

    /**
     * Loads the settings in `Amber.onCreate`, on the main thread, before any
     * activity, service or ContentProvider call can read them. Otherwise, an
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
            if (contains(LEGACY_TRUST_SCORE_ENABLED_KEY)) {
                edit { remove(LEGACY_TRUST_SCORE_ENABLED_KEY) }
            }

            val torMode = torMode()
            val proxyPort = getInt(SettingsKeys.PROXY_PORT.key, 9050)
            applyProxy(torMode, proxyPort)

            return AmberSettings(
                defaultRelays = relays(SettingsKeys.DEFAULT_RELAYS, defaultAppRelays),
                defaultProfileRelays = relays(SettingsKeys.DEFAULT_PROFILE_RELAYS, defaultIndexerRelays),
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
                updateCheckFrequency = enumOr(SettingsKeys.UPDATE_CHECK_FREQUENCY, UpdateCheckFrequency.DAILY),
                updateChannel = enumOr(SettingsKeys.UPDATE_CHANNEL, UpdateChannel.STABLE),
                startServiceOnBoot = getBoolean(SettingsKeys.START_SERVICE_ON_BOOT.key, true),
                privacyMode = getBoolean(SettingsKeys.PRIVACY_MODE.key, false),
                rateLimitEnabled = getBoolean(SettingsKeys.RATE_LIMIT_ENABLED.key, true),
                rateLimitMaxPerWindow = getInt(SettingsKeys.RATE_LIMIT_MAX_PER_WINDOW.key, 5),
                rateLimitWindowSeconds = getInt(SettingsKeys.RATE_LIMIT_WINDOW_SECONDS.key, 30),
                profileFetchInterval = enumOr(SettingsKeys.PROFILE_FETCH_INTERVAL, ProfileFetchInterval.FIFTEEN_MINUTES),
                trustScoreEnabled = getBoolean(SettingsKeys.TRUST_SCORE_ENABLED.key, false),
                logRetention = getLogRetention(context),
                historyRetention = getHistoryRetention(context),
                sortApplicationsAlphabetically = getBoolean(SettingsKeys.SORT_APPLICATIONS_ALPHABETICALLY.key, false),
                localBunkerServer = getBoolean(SettingsKeys.LOCAL_BUNKER_SERVER.key, false),
                localBunkerPort = if (contains(SettingsKeys.LOCAL_BUNKER_PORT.key)) getInt(SettingsKeys.LOCAL_BUNKER_PORT.key, 0) else null,
            )
        }
    }

    // A stored enum by name; a missing or unknown name (an older or newer build) reads as [default].
    private inline fun <reified T : Enum<T>> SharedPreferences.enumOr(key: SettingsKeys, default: T): T = try {
        enumValueOf<T>(getString(key.key, default.name)!!)
    } catch (_: IllegalArgumentException) {
        default
    }

    // The old use_proxy boolean predates TorMode: true meant Orbot.
    private fun SharedPreferences.torMode(): TorMode = when {
        contains(SettingsKeys.TOR_MODE.key) -> enumOr(SettingsKeys.TOR_MODE, TorMode.DISABLED)
        getBoolean(SettingsKeys.USE_PROXY.key, false) -> TorMode.ORBOT
        else -> TorMode.DISABLED
    }

    private fun SharedPreferences.relays(key: SettingsKeys, default: List<NormalizedRelayUrl>): List<NormalizedRelayUrl> = getStringSet(key.key, null)?.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) } ?: default

    // Points OkHttp at the proxy before any dial: Orbot's port, or for built-in Tor a
    // fail-closed placeholder so no clearnet traffic leaks before TorManager sets the real port.
    private fun applyProxy(torMode: TorMode, proxyPort: Int) {
        when (torMode) {
            TorMode.ORBOT -> HttpClientManager.setDefaultProxyOnPort(proxyPort)
            TorMode.BUILTIN -> HttpClientManager.setDefaultProxyOnPort(TorManager.socksPort.value)
            TorMode.DISABLED -> {}
        }
    }

    private fun sharedPrefs(
        context: Context,
        npub: String? = null,
    ): SharedPreferences = if (npub == null) appPreferences(context) else accountPreferences(context, npub)

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
        port: Int = settingsStore.settings.proxyPort,
    ) {
        sharedPrefs(context).edit {
            apply {
                putString(SettingsKeys.TOR_MODE.key, torMode.name)
                putInt(SettingsKeys.PROXY_PORT.key, port)
            }
        }
        settingsStore.settings = loadSettingsFromEncryptedStorage(context)
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
        settingsStore.settings = loadSettingsFromEncryptedStorage(context)
    }

    fun updateStartServiceOnBoot(context: Context, enabled: Boolean) {
        sharedPrefs(context).edit {
            apply {
                putBoolean(SettingsKeys.START_SERVICE_ON_BOOT.key, enabled)
            }
        }
        settingsStore.settings = loadSettingsFromEncryptedStorage(context)
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
        settingsStore.settings = loadSettingsFromEncryptedStorage(context)
        if (enabled) {
            SavedAccounts.all(context).forEach {
                try {
                    accountRepository.getLogDatabase(it.npub).cleanupDao().clearLogs()
                    accountRepository.getHistoryDatabase(it.npub).dao().clearHistory()
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
        settingsStore.settings = loadSettingsFromEncryptedStorage(context)
    }

    fun updateUpdateCheckFrequency(context: Context, frequency: UpdateCheckFrequency) {
        sharedPrefs(context).edit {
            apply {
                putString(SettingsKeys.UPDATE_CHECK_FREQUENCY.key, frequency.name)
            }
        }
        settingsStore.settings = loadSettingsFromEncryptedStorage(context)
    }

    fun updateProfileFetchInterval(context: Context, interval: ProfileFetchInterval) {
        sharedPrefs(context).edit {
            apply {
                putString(SettingsKeys.PROFILE_FETCH_INTERVAL.key, interval.name)
            }
        }
        settingsStore.settings = loadSettingsFromEncryptedStorage(context)
    }

    suspend fun updateLogRetention(context: Context, retention: RetentionPeriod) {
        sharedPrefs(context).edit {
            apply {
                putString(SettingsKeys.LOG_RETENTION.key, retention.name)
            }
        }
        settingsStore.settings = loadSettingsFromEncryptedStorage(context)
        pruneAllAccounts(context)
    }

    suspend fun updateHistoryRetention(context: Context, retention: RetentionPeriod) {
        sharedPrefs(context).edit {
            apply {
                putString(SettingsKeys.HISTORY_RETENTION.key, retention.name)
            }
        }
        settingsStore.settings = loadSettingsFromEncryptedStorage(context)
        pruneAllAccounts(context)
    }

    // Applies a shortened retention period right away instead of at the next cleanup.
    private suspend fun pruneAllAccounts(context: Context) {
        SavedAccounts.all(context).forEach {
            try {
                LogRetentionManager.prune(context, accountRepository, it.npub)
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
        settingsStore.settings = loadSettingsFromEncryptedStorage(context)
    }

    fun getLastUpdateCheckTime(context: Context): Long = sharedPrefs(context).getLong(SettingsKeys.LAST_UPDATE_CHECK_TIME.key, 0L)

    fun setLastUpdateCheckTime(context: Context, time: Long) {
        sharedPrefs(context).edit {
            apply {
                putLong(SettingsKeys.LAST_UPDATE_CHECK_TIME.key, time)
            }
        }
    }
}
