package com.greenart7c3.nostrsigner

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.StrictMode
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.LruCache
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.SingletonImageLoader
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.database.AccountDatabases
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.models.TimeUtils as AmberTimeUtils
import com.greenart7c3.nostrsigner.models.UpdateCheckFrequency
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.greenart7c3.nostrsigner.relays.AppRelayClient
import com.greenart7c3.nostrsigner.relays.NostrClientLoggerListener
import com.greenart7c3.nostrsigner.service.BunkerResponder
import com.greenart7c3.nostrsigner.service.ConnectivityService
import com.greenart7c3.nostrsigner.service.ImageLoaderFactory
import com.greenart7c3.nostrsigner.service.IntentRateLimiter
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.LocalBunkerRelayServer
import com.greenart7c3.nostrsigner.service.NetworkState
import com.greenart7c3.nostrsigner.service.NotificationSubscription
import com.greenart7c3.nostrsigner.service.ProfileSubscription
import com.greenart7c3.nostrsigner.service.SharedFiles
import com.greenart7c3.nostrsigner.service.TorStartup
import com.greenart7c3.nostrsigner.service.TrustScoreService
import com.greenart7c3.nostrsigner.service.ZapstoreUpdater
import com.greenart7c3.nostrsigner.service.crashreports.AppPendingReports
import com.greenart7c3.nostrsigner.service.crashreports.CrashReportCache
import com.greenart7c3.nostrsigner.service.crashreports.UnexpectedCrashSaver
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import java.lang.ref.WeakReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class Amber :
    Application(),
    LifecycleObserver,
    SettingsStore {
    private var mainActivityRef: WeakReference<AppCompatActivity?>? = null
    val crashReportCache: CrashReportCache by lazy { CrashReportCache(this.applicationContext) }
    val pendingReports = AppPendingReports(this)

    fun setMainActivity(activity: AppCompatActivity?) {
        AmberLog.d(TAG, "Setting main activity ref to $activity")
        mainActivityRef = WeakReference(activity)
    }

    fun getMainActivity(): AppCompatActivity? = if (mainActivityRef != null) mainActivityRef!!.get() else null

    // Exists to avoid exceptions stopping the coroutine
    val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            AmberLog.e("AmberCoroutine", "Caught exception: ${throwable.message}", throwable)
            if (throwable is FailedMigrationException) throw throwable
            // The process survived it, so nothing else would report it.
            if (throwable is OutOfMemoryError) crashReportCache.saveReport(throwable)
        }

    val applicationIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    // Above the init blocks below: they hand it on as the AccountRepository.
    val accountDatabases = AccountDatabases(this, this, applicationIOScope)

    // At construction, not onCreate: ContentProviders are created before onCreate runs.
    init {
        AccountStorage.install(accountRepository = accountDatabases, appScope = applicationIOScope)
        SecureCryptoHelper.install(this, accountDatabases)
        LocalPreferences.install(this, accountDatabases)
        IntentUtils.install(accountDatabases, applicationIOScope) { getMainActivity() }
    }

    override var settings: AmberSettings = AmberSettings()

    val intentRateLimiter = IntentRateLimiter(
        config = { IntentRateLimiter.Config(settings.rateLimitEnabled, settings.rateLimitMaxPerWindow, settings.rateLimitWindowSeconds) },
    )

    /** The appearance chosen in Settings; the activities' themes follow it live. */
    override val themeMode by lazy { MutableStateFlow(DevicePreferences.getThemeMode(this)) }

    /**
     * False until [settings] holds the stored settings. [onCreate] loads them synchronously
     * ([LocalPreferences.loadSettingsAtStartup]), so this is true before any activity,
     * service or provider call runs. While false, [settings] is the data-class default,
     * whose torMode (DISABLED) is a guess, not a decision — any dial in that window must
     * route through the proxy client (preset from plain prefs in [onCreate]) instead of
     * trusting the default and going to the clearnet.
     */
    @Volatile var isSettingsLoaded = false

    private val imageLoaderFactory = ImageLoaderFactory(settingsStore = this, isSettingsLoaded = { isSettingsLoaded }, scope = applicationIOScope)

    val relayClient: AppRelayClient = AppRelayClient(
        appContext = this,
        settingsStore = this,
        isSettingsLoaded = { isSettingsLoaded },
        scope = applicationIOScope,
        updateSubscriptions = {
            if (isAppInForeground) profileSubscription.updateFilters()
            notificationSubscription.updateFilter()
        },
        closeSubscriptions = {
            profileSubscription.closeSub()
            notificationSubscription.closeAllSubs()
        },
    )

    // Starts the built-in Tor for the relays and reconnects them when it recovers.
    val torStartup = TorStartup(this, this, relayClient, applicationIOScope)

    // Capacity 10 was too small under bursty NIP-46 traffic — duplicate-detection
    // started missing recent events. 512 covers high-volume relays without being
    // a meaningful memory cost (event-id hex strings + Long).
    val notificationCache = LruCache<String, Long>(512)

    // logs and stat counts.
    init {
        relayClient.client.addConnectionListener(NostrClientLoggerListener(this, relayClient.stats, applicationIOScope, settingsStore = this, relayClient = relayClient, accountRepository = accountDatabases))
    }

    // Authenticates with relays; it registers itself with the client.
    init {
        RelayAuthenticator(relayClient.client, applicationIOScope) { _, event, _ ->
            // One account failing to sign (device locked under requireUnlockedDevice,
            // or a transient key error) must not abort AUTH for every other account.
            AccountStorage.all(this).mapNotNull { account ->
                runCatching { account.sign(event) }.onFailure { e ->
                    AmberLog.e(TAG, "NIP-42 auth signing failed for ${account.npub}", e)
                }.getOrNull()
            }
        }
    }

    // The local WebSocket relay for bunker connections (opt in under Settings -> Relays).
    val localBunkerRelay by lazy { LocalBunkerRelayServer(this, this, accountDatabases, relayClient) }

    // Answers bunker requests; stateless, shared by the NIP-46 consumer and the approval screens.
    val bunkerResponder by lazy { BunkerResponder(this, this, accountDatabases, relayClient, applicationIOScope, localBunkerRelay) { getMainActivity() } }

    val notificationSubscription: NotificationSubscription = NotificationSubscription(relayClient.client, this, settingsStore = this, accountRepository = accountDatabases, bunkerResponder = bunkerResponder, scope = applicationIOScope, notificationCache = notificationCache)

    // Per-account profile metadata fetch, started/stopped by the composables that display
    // each account (see ProfileSubscriptionEffect) rather than app-wide.
    val profileSubscription: ProfileSubscription = ProfileSubscription(relayClient.client, this, applicationIOScope, settingsStore = this, relayClient = relayClient)

    val zapstoreUpdater: ZapstoreUpdater? = if (!BuildFlavorChecker.isOfflineFlavor() && !BuildFlavorChecker.isFdroidBuild()) {
        ZapstoreUpdater(relayClient.client, applicationIOScope, relayClient = relayClient, appContext = this)
    } else {
        null
    }

    // Hoisted out of runMigrations() so re-entry (e.g. test re-init) can't
    // double-register and double-fire foreground/background callbacks.
    private var processLifecycleObserverRegistered = false

    private val processLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            AmberLog.d("ProcessLifecycleOwner", "App in foreground")
            isAppInForeground = true

            if (!settings.killSwitch.value && settings.autoCheckUpdates) {
                applicationIOScope.launch {
                    maybeCheckForUpdates()
                }
            }
        }

        override fun onStop(owner: LifecycleOwner) {
            AmberLog.d("ProcessLifecycleOwner", "App in background")
            isAppInForeground = false
        }
    }

    val networkState = NetworkState()

    override fun onCreate() {
        super.onCreate()
        SingletonImageLoader.setSafe(imageLoaderFactory)

        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }

        // Before anything reads the account list or the settings: an install that
        // comes from 3.3.0 or older still keeps them in the EncryptedSharedPreferences
        // files this version no longer reads, so it would look like a first run.
        // Synchronous because this is the only point ahead of the three request
        // paths and the settings load (runMigrations only runs from ConnectivityService,
        // which the offline flavor does not have); on an install with nothing to
        // migrate it is a single directory read.
        if (LegacyStorageMigration.needed(this)) {
            runBlocking { LegacyStorageMigration.run(this@Amber) }
        }
        // Before anything can dial: route the proxy client through the user's SOCKS
        // setup (or the fail-closed placeholder for BUILTIN) read synchronously from
        // plain prefs. Dials in the window before the async settings load — network
        // callbacks, profile fetches — pick this up via the factory's !isSettingsLoaded
        // gate; without it that window leaks clearnet traffic for Tor users.
        LocalPreferences.presetProxyFromPrefs(this)
        settings = LocalPreferences.loadSettingsAtStartup(this)
        isSettingsLoaded = true
        // Before any string is read: notification channels below, and background work
        // that builds notifications before an activity has applied the language.
        settings.language?.let { AppLanguage.apply(it) }
        relayClient.stats.createNotificationChannel()
        UnexpectedCrashSaver.install(this, crashReportCache, applicationIOScope)

        // Build Coil's singleton ImageLoader off the main thread. Its factory
        // (ImageLoaderFactory) touches cacheDir, so letting the first AsyncImage
        // build it lazily during composition trips StrictMode's DiskReadViolation.
        applicationIOScope.launch {
            SingletonImageLoader.get(this@Amber)
        }

        // The first time zone lookup lazily mmaps tzdata from disk.
        // Force it here so it never happens on the main thread during the first
        // timestamp formatted in composition (StrictMode DiskReadViolation).
        applicationIOScope.launch {
            AmberTimeUtils.warmUp(this@Amber)
        }
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
    }

    fun runMigrations(onDone: () -> Unit) {
        applicationIOScope.launch {
            runCatching { SharedFiles.cleanUp(this@Amber) }
            try {
                val currentAccount = SavedAccounts.current(this@Amber)
                if (currentAccount.isNullOrBlank() && SavedAccounts.all(this@Amber).isNotEmpty()) {
                    SavedAccounts.switchTo(this@Amber, SavedAccounts.all(this@Amber).first().npub)
                }
                LocalPreferences.reloadApp(this@Amber)

                // Finish a wrapping-key rotation that was interrupted, so the
                // previous key does not outlive the data it protected.
                try {
                    SecureCryptoHelper.completePendingRotation(this@Amber)
                } catch (e: Exception) {
                    AmberLog.e(TAG, "Failed to complete pending key rotation", e)
                }

                HttpClientManager.getHttpClient(false)
                HttpClientManager.getHttpClient(true)

                // Local WebSocket relay for bunker connections (opt in under
                // Settings -> Relays): up before the relays are dialed, so
                // connections that list it subscribe at once. Loopback only,
                // so it never waits for Tor.
                localBunkerRelay.startIfEnabled()

                launch(Dispatchers.Main) {
                    if (!processLifecycleObserverRegistered) {
                        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
                        processLifecycleObserverRegistered = true
                    }
                }

                val torReady = torStartup.start()

                // Do not open relay sockets through a dead SOCKS proxy: every
                // connect would fail and the retry churn would waste battery.
                // Relays connect via the recovery observer once Tor is up.
                if (torReady) {
                    relayClient.checkForNewRelaysAndUpdateAllFilters(true)
                    if (settings.killSwitch.value) {
                        relayClient.disconnectIntentionally()
                    }
                }
            } catch (e: Exception) {
                AmberLog.e(TAG, "Failed to run migrations", e)
                if (e is CancellationException) throw e
                if (e is FailedMigrationException) throw e
            }
            // Always hand over to the caller: it registers the network
            // callback, the safety-net refresh and the unlock receiver, which
            // are what recover from a start that couldn't connect (Tor down, or
            // a locked device under requireUnlockedDevice). startFunctions()
            // waits for Tor on its own.
            onDone()
        }
    }

    fun startService() {
        if (BuildFlavorChecker.isOfflineFlavor()) return
        try {
            AmberLog.d(TAG, "Starting ConnectivityService")
            val operation = PendingIntent.getForegroundService(
                this,
                10,
                Intent(this, ConnectivityService::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val alarmManager = this.getSystemService(ALARM_SERVICE) as AlarmManager
            alarmManager.set(AlarmManager.RTC_WAKEUP, 1000, operation)
        } catch (e: Exception) {
            AmberLog.e(TAG, "Failed to start ConnectivityService", e)
            if (e is CancellationException) throw e
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        // Since API 34 the system only delivers UI_HIDDEN and BACKGROUND here —
        // the RUNNING_*/MODERATE/COMPLETE levels are deprecated and never sent.
        // UI_HIDDEN (20) is not pressure, but a long-lived signer process has no
        // reason to keep UI-only caches while its UI is invisible either.
        if (level == TRIM_MEMORY_UI_HIDDEN) {
            imageLoaderFactory.trimMemoryCache(this)
        } else if (level >= TRIM_MEMORY_BACKGROUND) {
            imageLoaderFactory.trimMemoryCache(this)
            TrustScoreService.clearCache()
        }
    }

    /**
     * Pre-API-34 stand-in for foreground memory pressure (the deprecated
     * TRIM_MEMORY_RUNNING_* levels are never delivered since API 34): the
     * system only calls this when memory is low enough that process death is
     * imminent.
     */
    override fun onLowMemory() {
        super.onLowMemory()
        imageLoaderFactory.trimMemoryCache(this)
        TrustScoreService.clearCache()
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationIOScope.cancel()
    }

    suspend fun maybeCheckForUpdates() {
        if (zapstoreUpdater == null) return
        val lastCheck = UpdateCheckSettings.getLastUpdateCheckTime(this)
        val now = System.currentTimeMillis()
        val shouldCheck = when (settings.updateCheckFrequency) {
            UpdateCheckFrequency.DAILY -> now - lastCheck > 24 * 60 * 60 * 1000L
            UpdateCheckFrequency.WEEKLY -> now - lastCheck > 7 * 24 * 60 * 60 * 1000L
        }
        if (shouldCheck) {
            UpdateCheckSettings.setLastUpdateCheckTime(this, now)
            zapstoreUpdater.checkForUpdates()
        }
    }

    companion object {
        var isAppInForeground = false
        const val TAG = "Amber"

        const val DEVELOPER_HEX_KEY = "7579076d9aff0a4cfdefa7e2045f2486c7e5d8bc63bfc6b45397233e1bbfcb19"
    }
}

class FailedMigrationException(msg: String) : Exception(msg)

/**
 * The application container, for Android entry points (activities, services, receivers,
 * workers, the provider) that the system constructs and so cannot take it as a parameter.
 * Everything they create gets its dependencies passed in.
 */
val Context.amber: Amber get() = applicationContext as Amber
