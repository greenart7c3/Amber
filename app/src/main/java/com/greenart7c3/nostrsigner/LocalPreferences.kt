package com.greenart7c3.nostrsigner

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
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

    private fun getRetention(context: Context, key: SettingsKeys, default: RetentionPeriod): RetentionPeriod = try {
        RetentionPeriod.valueOf(sharedPrefs(context).getString(key.key, default.name)!!)
    } catch (_: IllegalArgumentException) {
        default
    }

    /** Read synchronously so the first frame of each activity already uses the chosen theme. */
    fun getThemeMode(context: Context): ThemeMode = try {
        ThemeMode.valueOf(sharedPrefs(context).getString(SettingsKeys.THEME_MODE.key, ThemeMode.SYSTEM.name)!!)
    } catch (_: IllegalArgumentException) {
        ThemeMode.SYSTEM
    }

    fun updateThemeMode(context: Context, mode: ThemeMode) {
        sharedPrefs(context).edit {
            apply {
                putString(SettingsKeys.THEME_MODE.key, mode.name)
            }
        }
        context.amber.themeMode.value = mode
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
        context.amber.settings = loadSettingsFromEncryptedStorage(context)
        context.amber.isSettingsLoaded = true
        context.amber.settings.language?.let { AppLanguage.apply(it) }
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
                sortApplicationsAlphabetically = getBoolean(SettingsKeys.SORT_APPLICATIONS_ALPHABETICALLY.key, false),
                localBunkerServer = getBoolean(SettingsKeys.LOCAL_BUNKER_SERVER.key, false),
                localBunkerPort = if (contains(SettingsKeys.LOCAL_BUNKER_PORT.key)) getInt(SettingsKeys.LOCAL_BUNKER_PORT.key, 0) else null,
            )
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
        port: Int = context.amber.settings.proxyPort,
    ) {
        sharedPrefs(context).edit {
            apply {
                putString(SettingsKeys.TOR_MODE.key, torMode.name)
                putInt(SettingsKeys.PROXY_PORT.key, port)
            }
        }
        context.amber.settings = loadSettingsFromEncryptedStorage(context)
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
        context.amber.settings = loadSettingsFromEncryptedStorage(context)
    }

    fun updateStartServiceOnBoot(context: Context, enabled: Boolean) {
        sharedPrefs(context).edit {
            apply {
                putBoolean(SettingsKeys.START_SERVICE_ON_BOOT.key, enabled)
            }
        }
        context.amber.settings = loadSettingsFromEncryptedStorage(context)
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
        context.amber.settings = loadSettingsFromEncryptedStorage(context)
        if (enabled) {
            SavedAccounts.all(context).forEach {
                try {
                    context.amber.getLogDatabase(it.npub).dao().clearLogs()
                    context.amber.getHistoryDatabase(it.npub).dao().clearHistory()
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
        context.amber.settings = loadSettingsFromEncryptedStorage(context)
    }

    fun updateUpdateCheckFrequency(context: Context, frequency: UpdateCheckFrequency) {
        sharedPrefs(context).edit {
            apply {
                putString(SettingsKeys.UPDATE_CHECK_FREQUENCY.key, frequency.name)
            }
        }
        context.amber.settings = loadSettingsFromEncryptedStorage(context)
    }

    fun updateProfileFetchInterval(context: Context, interval: ProfileFetchInterval) {
        sharedPrefs(context).edit {
            apply {
                putString(SettingsKeys.PROFILE_FETCH_INTERVAL.key, interval.name)
            }
        }
        context.amber.settings = loadSettingsFromEncryptedStorage(context)
    }

    suspend fun updateLogRetention(context: Context, retention: RetentionPeriod) {
        sharedPrefs(context).edit {
            apply {
                putString(SettingsKeys.LOG_RETENTION.key, retention.name)
            }
        }
        context.amber.settings = loadSettingsFromEncryptedStorage(context)
        pruneAllAccounts(context)
    }

    suspend fun updateHistoryRetention(context: Context, retention: RetentionPeriod) {
        sharedPrefs(context).edit {
            apply {
                putString(SettingsKeys.HISTORY_RETENTION.key, retention.name)
            }
        }
        context.amber.settings = loadSettingsFromEncryptedStorage(context)
        pruneAllAccounts(context)
    }

    // Applies a shortened retention period right away instead of at the next cleanup.
    private suspend fun pruneAllAccounts(context: Context) {
        SavedAccounts.all(context).forEach {
            try {
                LogRetentionManager.prune(context, context.amber, it.npub)
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
        context.amber.settings = loadSettingsFromEncryptedStorage(context)
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
