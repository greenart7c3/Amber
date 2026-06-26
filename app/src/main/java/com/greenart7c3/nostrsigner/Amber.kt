package com.greenart7c3.nostrsigner

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import android.os.StrictMode
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.LruCache
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil3.EventListener
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.crossfade
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.CachingApplicationDao
import com.greenart7c3.nostrsigner.database.HistoryDatabase
import com.greenart7c3.nostrsigner.database.LogDatabase
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.models.FeedbackType
import com.greenart7c3.nostrsigner.models.TorMode
import com.greenart7c3.nostrsigner.models.UpdateCheckFrequency
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.greenart7c3.nostrsigner.okhttp.OkHttpWebSocket
import com.greenart7c3.nostrsigner.relays.AmberRelayStats
import com.greenart7c3.nostrsigner.relays.NostrClientLoggerListener
import com.greenart7c3.nostrsigner.service.ApplicationNameCache
import com.greenart7c3.nostrsigner.service.BackupApplicationsWorker
import com.greenart7c3.nostrsigner.service.ClearLogsWorker
import com.greenart7c3.nostrsigner.service.ConnectivityService
import com.greenart7c3.nostrsigner.service.NotificationSubscription
import com.greenart7c3.nostrsigner.service.ProfileSubscription
import com.greenart7c3.nostrsigner.service.TorManager
import com.greenart7c3.nostrsigner.service.UpdateCheckWorker
import com.greenart7c3.nostrsigner.service.ZapstoreUpdater
import com.greenart7c3.nostrsigner.service.crashreports.CrashReportCache
import com.greenart7c3.nostrsigner.service.crashreports.UnexpectedCrashSaver
import com.greenart7c3.nostrsigner.signer.RemoteSigner
import com.greenart7c3.nostrsigner.ui.ToastManager
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.io.File
import java.lang.ref.WeakReference
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okio.Path.Companion.toOkioPath

class Amber :
    Application(),
    LifecycleObserver,
    SingletonImageLoader.Factory {
    private var mainActivityRef: WeakReference<AppCompatActivity?>? = null
    val crashReportCache: CrashReportCache by lazy { CrashReportCache(this.applicationContext) }
    var pendingCrashReport: String? = null
    val pendingTranslationReport = MutableStateFlow<String?>(null)

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
        }

    val applicationIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    var settings: AmberSettings = AmberSettings()

    // The relay/networking singletons below are lazy so the isolated :signer
    // process (which shares this Application class) never constructs them — it
    // only needs DataStore + Keystore. The main process warms them eagerly in
    // onCreate so their construction-time side effects (logger/auth registration
    // on the client) run exactly as before.
    val factory by lazy {
        OkHttpWebSocket.Builder { url ->
            val useProxy = if (isPrivateIp(url.url)) false else settings.torMode != TorMode.DISABLED
            HttpClientManager.getHttpClient(useProxy)
        }
    }

    val client: NostrClient by lazy { NostrClient(factory, applicationIOScope) }

    val stats by lazy { AmberRelayStats(client, this) }

    // logs and stat counts.
    val listener by lazy {
        NostrClientLoggerListener(this, stats, applicationIOScope).also {
            client.addConnectionListener(it)
        }
    }

    // Authenticates with relays.
    val authCoordinator by lazy {
        RelayAuthenticator(client, applicationIOScope) { event ->
            LocalPreferences.allAccounts(this).map { account ->
                account.sign(event)
            }
        }
    }

    val relayStats by lazy { RelayStats(client) }

    val notificationSubscription by lazy { NotificationSubscription(client, this) }

    // This runs on the foreground only
    val profileSubscription by lazy { ProfileSubscription(client, this, applicationIOScope) }

    val zapstoreUpdater: ZapstoreUpdater? by lazy {
        if (!BuildFlavorChecker.isOfflineFlavor() && !BuildConfig.IS_FDROID_BUILD) {
            ZapstoreUpdater(client, applicationIOScope)
        } else {
            null
        }
    }

    var databases = ConcurrentHashMap<String, AppDatabase>()
    private var logDatabases = ConcurrentHashMap<String, LogDatabase>()
    private var historyDatabases = ConcurrentHashMap<String, HistoryDatabase>()
    private val cachingDaos = ConcurrentHashMap<String, CachingApplicationDao>()

    // Hoisted out of runMigrations() so re-entry (e.g. test re-init) can't
    // double-register and double-fire foreground/background callbacks.
    private var processLifecycleObserverRegistered = false
    private val processLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            AmberLog.d("ProcessLifecycleOwner", "App in foreground")
            isAppInForeground = true

            // activates the profile filter only when the app is in the foreground
            if (!settings.killSwitch.value) {
                applicationIOScope.launch {
                    profileSubscription.updateFilter()
                    if (settings.autoCheckUpdates) {
                        maybeCheckForUpdates()
                    }
                }
            }
        }

        override fun onStop(owner: LifecycleOwner) {
            AmberLog.d("ProcessLifecycleOwner", "App in background")
            isAppInForeground = false

            // closes the filter when in the background
            applicationIOScope.launch {
                profileSubscription.closeSub()
            }
        }
    }

    val isOnMobileDataState = mutableStateOf(false)
    val isOnWifiDataState = mutableStateOf(false)
    val isOnOfflineState = mutableStateOf(false)

    /** npubs whose AndroidKeyStore key failed to decrypt (device KeyMint bug). */
    val keystoreFailedAccounts = MutableStateFlow<List<String>>(emptyList())

    @Volatile var intentionalDisconnectTime = 0L

    // Capacity 10 was too small under bursty NIP-46 traffic — duplicate-detection
    // started missing recent events. 512 covers high-volume relays without being
    // a meaningful memory cost (event-id hex strings + Long).
    val notificationCache = LruCache<String, Long>(512)

    fun isSocksProxyAlive(proxyHost: String, proxyPort: Int): Boolean {
        if (settings.torMode == TorMode.BUILTIN) {
            val port = TorManager.socksPort.value
            if (port == 0) return false
            return try {
                if (TrafficStats.getThreadStatsTag() == -1) {
                    TrafficStats.setThreadStatsTag(0x0001)
                }
                val socket = Socket()
                socket.connect(InetSocketAddress(proxyHost, port), 5000)
                TrafficStats.tagSocket(socket)
                socket.close()
                true
            } catch (_: Exception) {
                false
            }
        }
        try {
            if (TrafficStats.getThreadStatsTag() == -1) {
                TrafficStats.setThreadStatsTag(0x0001)
            }
            val socket = Socket()
            socket.connect(InetSocketAddress(proxyHost, proxyPort), 5000) // 3-second timeout
            TrafficStats.tagSocket(socket)
            socket.close()
            return true
        } catch (e: Exception) {
            if (e.message?.contains("EACCES (Permission denied)") == true) {
                ToastManager.toast(getString(R.string.warning), getString(R.string.network_permission_message))
            } else if (e.message?.contains("socket failed: EPERM (Operation not permitted)") == true) {
                ToastManager.toast(getString(R.string.warning), getString(R.string.network_permission_message))
            }
            AmberLog.e(TAG, "Failed to connect to proxy", e)
            return false
        }
    }

    fun updateNetworkCapabilities(networkCapabilities: NetworkCapabilities?): Boolean {
        val isOnMobileData = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val isOnWifi = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isOffline = !isOnMobileData && !isOnWifi

        var changedNetwork = false

        if (isOnMobileDataState.value != isOnMobileData) {
            isOnMobileDataState.value = isOnMobileData

            changedNetwork = true
        }

        if (isOnWifiDataState.value != isOnWifi) {
            isOnWifiDataState.value = isOnWifi

            changedNetwork = true
        }

        if (isOnOfflineState.value != isOffline) {
            isOnOfflineState.value = isOffline
            changedNetwork = true
        }

        if (changedNetwork) {
            if (isOnMobileData) {
                HttpClientManager.setDefaultTimeout(HttpClientManager.DEFAULT_TIMEOUT_ON_MOBILE)
            } else {
                HttpClientManager.setDefaultTimeout(HttpClientManager.DEFAULT_TIMEOUT_ON_WIFI)
            }
        }

        return changedNetwork
    }

    fun startCleanLogsAlarm() {
        val workManager = WorkManager.getInstance(this)

        // Run an immediate one-time cleanup on every startup to handle accumulated data.
        // KEEP policy ensures only one instance runs at a time if the app is restarted quickly.
        val immediateRequest = OneTimeWorkRequestBuilder<ClearLogsWorker>()
            .addTag("clearLogsWorkOneTime")
            .build()
        workManager.enqueueUniqueWork(
            "ClearLogsWorkerOneTime",
            ExistingWorkPolicy.KEEP,
            immediateRequest,
        )

        // Also schedule a daily periodic run.
        // KEEP policy preserves the existing schedule so app restarts don't reset the timer.
        val periodicRequest = PeriodicWorkRequestBuilder<ClearLogsWorker>(
            24,
            TimeUnit.HOURS,
        )
            .addTag("clearLogsWork")
            .build()
        workManager.enqueueUniquePeriodicWork(
            "ClearLogsWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest,
        )
    }

    fun startUpdateCheckAlarm() {
        if (BuildFlavorChecker.isOfflineFlavor() || BuildConfig.IS_FDROID_BUILD) return

        val workManager = WorkManager.getInstance(this)

        val periodicRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
            .addTag("updateCheckWork")
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            "UpdateCheckWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest,
        )
    }

    fun startBackupApplicationsAlarm() {
        if (BuildFlavorChecker.isOfflineFlavor()) return
        if (!LocalPreferences.anyAccountBackupEnabled(this)) return

        val workManager = WorkManager.getInstance(this)

        val periodicRequest = PeriodicWorkRequestBuilder<BackupApplicationsWorker>(1, TimeUnit.DAYS)
            .addTag("backupApplicationsWork")
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            "BackupApplicationsWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest,
        )
    }

    fun cancelBackupApplicationsAlarm() {
        WorkManager.getInstance(this).cancelUniqueWork("BackupApplicationsWorker")
    }

    override fun onCreate() {
        super.onCreate()

        instance = this
        Thread.setDefaultUncaughtExceptionHandler(UnexpectedCrashSaver(crashReportCache, applicationIOScope))

        if (isSignerProcess()) {
            // The isolated crypto process needs only the (process-independent)
            // encrypted DataStore + Android Keystore and the applicationIOScope
            // field. Skip the relay client, subscriptions, workers, Coil,
            // StrictMode VM policy and ProcessLifecycle entirely.
            AmberLog.d(TAG, "Started :signer process; minimal init")
            return
        }

        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }

        stats.createNotificationChannel()
        warmMainProcessSingletons()

        // The main process talks to the :signer process for every crypto op.
        RemoteSigner.bind(this)

        // Build Coil's singleton ImageLoader off the main thread. Its factory
        // (newImageLoader) touches cacheDir, so letting the first AsyncImage
        // build it lazily during composition trips StrictMode's DiskReadViolation.
        applicationIOScope.launch {
            SingletonImageLoader.get(this@Amber)
        }
    }

    /** True when running in the isolated ":signer" crypto process. */
    private fun isSignerProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            runCatching { File("/proc/self/cmdline").readText().trim { it <= ' ' } }.getOrNull()
        }
        return name?.endsWith(":signer") == true
    }

    /**
     * Touches the lazy relay/networking singletons so their construction-time
     * side effects (the logger listener and relay authenticator registering on
     * the client) run at main-process startup, exactly as when they were eager
     * fields — but they never construct in the :signer process.
     */
    private fun warmMainProcessSingletons() {
        factory
        client
        stats
        listener
        authCoordinator
        relayStats
        notificationSubscription
        profileSubscription
        zapstoreUpdater
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
            try {
                val currentAccount = LocalPreferences.currentAccount(this@Amber)
                if (currentAccount.isNullOrBlank() && LocalPreferences.allSavedAccounts(this@Amber).isNotEmpty()) {
                    LocalPreferences.switchToAccount(this@Amber, LocalPreferences.allSavedAccounts(this@Amber).first().npub)
                }
                LocalPreferences.reloadApp()

                HttpClientManager.getHttpClient(false)
                HttpClientManager.getHttpClient(true)

                // Start Tor immediately in the background without blocking app startup
                if (settings.torMode == TorMode.BUILTIN && !BuildFlavorChecker.isOfflineFlavor()) {
                    TorManager.start(this@Amber, applicationIOScope)
                }

                launch(Dispatchers.Main) {
                    if (!processLifecycleObserverRegistered) {
                        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
                        processLifecycleObserverRegistered = true
                    }
                }

                // Wait for Tor to be ready before establishing relay connections
                if (settings.torMode == TorMode.BUILTIN && !BuildFlavorChecker.isOfflineFlavor()) {
                    var attempt = 0
                    while (!TorManager.isRunning.value) {
                        if (attempt > 0) {
                            TorManager.showRetrying()
                            TorManager.stop()
                            delay(3000)
                            TorManager.start(this@Amber, applicationIOScope)
                        }
                        attempt++
                        withTimeoutOrNull(120_000L) {
                            TorManager.isRunning.first { it }
                        }
                    }
                }

                checkForNewRelaysAndUpdateAllFilters(true)
                if (settings.killSwitch.value) {
                    disconnectIntentionally()
                }
                onDone()
            } catch (e: Exception) {
                AmberLog.e(TAG, "Failed to run migrations", e)
                if (e is CancellationException) throw e
                if (e is FailedMigrationException) throw e
            }
        }
    }

    suspend fun waitForTorIfNeeded() {
        if (settings.torMode == TorMode.BUILTIN && !BuildFlavorChecker.isOfflineFlavor()) {
            TorManager.isRunning.first { it }
        }
    }

    fun startServiceFromUi() {
        startService()
    }

    fun startService() {
        if (BuildFlavorChecker.isOfflineFlavor()) return
        try {
            AmberLog.d(TAG, "Starting ConnectivityService")
            val operation = PendingIntent.getForegroundService(
                this,
                10,
                Intent(this, ConnectivityService::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            val alarmManager = this.getSystemService(ALARM_SERVICE) as AlarmManager
            alarmManager.set(AlarmManager.RTC_WAKEUP, 1000, operation)
        } catch (e: Exception) {
            AmberLog.e(TAG, "Failed to start ConnectivityService", e)
            if (e is CancellationException) throw e
        }
    }

    fun disconnectIntentionally() {
        intentionalDisconnectTime = System.currentTimeMillis()
        client.disconnect()
    }

    fun reconnect() {
        if (settings.killSwitch.value) {
            return
        }
        AmberLog.d(TAG, "reconnecting relays")
        val wasActive = client.isActive()
        // Always call connect() so that failed relays (socket == null) are retried
        // directly, bypassing BasicRelayClient's internal backoff delay. For relays
        // that are already connected (socket != null), connect() is a safe no-op.
        client.connect()
        client.reconnect(wasActive)
        if (!BuildFlavorChecker.isOfflineFlavor()) {
            stats.updateNotification()
        }
    }

    // computeIfAbsent (not check-then-put): AppDatabase.getDatabase builds a
    // fresh RoomDatabase every call, so a racing second caller would build a
    // duplicate that loses the put() and leaks its SQLiteConnection (caught by
    // StrictMode's CloseGuard on finalize).
    fun getDatabase(npub: String): AppDatabase = databases.computeIfAbsent(npub) {
        AppDatabase.getDatabase(applicationContext, npub)
    }

    /**
     * Returns the per-account permission DAO with read-through caching. All hot-path
     * permission lookups should go through this; writes routed through it also
     * invalidate the cache.
     */
    fun dao(npub: String): ApplicationDao {
        cachingDaos[npub]?.let { return it }
        return cachingDaos.computeIfAbsent(npub) {
            CachingApplicationDao(getDatabase(npub).dao())
        }
    }

    fun getLogDatabase(npub: String): LogDatabase = logDatabases.computeIfAbsent(npub) {
        LogDatabase.getDatabase(applicationContext, npub)
    }

    fun getHistoryDatabase(npub: String): HistoryDatabase = historyDatabases.computeIfAbsent(npub) {
        HistoryDatabase.getDatabase(applicationContext, npub)
    }

    /**
     * Closes and evicts every cached Room handle for [npub] so file locks and
     * worker threads are released. Called from the logout path; without it each
     * removed account leaks 3 open Room connections forever.
     */
    fun closeDatabasesFor(npub: String) {
        databases.remove(npub)?.runCatching { close() }
        logDatabases.remove(npub)?.runCatching { close() }
        historyDatabases.remove(npub)?.runCatching { close() }
        cachingDaos.remove(npub)
        ApplicationNameCache.clearForAccount(npub)
    }

    fun getSavedRelays(account: Account): Set<NormalizedRelayUrl> {
        val database = getDatabase(account.npub)
        val savedRelays = buildSet {
            database.dao().getAllRelayLists().forEach {
                addAll(it.relays)
            }
        }

        return savedRelays
    }

    suspend fun checkForNewRelaysAndUpdateAllFilters(
        shouldReconnect: Boolean = false,
    ) {
        if (settings.killSwitch.value) {
            disconnectIntentionally()
            return
        }

        val wasActive = client.isActive()
        if (!BuildFlavorChecker.isOfflineFlavor()) {
            // these update the relay list in the filters and send them to the
            // relay, reconnecting if needed
            if (isAppInForeground) {
                profileSubscription.updateFilter()
            }
            notificationSubscription.updateFilter()
        }
        AmberLog.d(TAG, "checkForNewRelaysAndUpdateAllFilters wasActive: $wasActive")
        if (!wasActive) {
            delay(3000)
            client.connect()
        }

        if (shouldReconnect) {
            client.reconnect(wasActive)
        }
    }

    fun isPrivateIp(url: String): Boolean = url.contains("127.0.0.1") ||
        url.contains("localhost") ||
        url.contains("192.168.") ||
        url.contains("172.16.") ||
        url.contains("172.17.") ||
        url.contains("172.18.") ||
        url.contains("172.19.") ||
        url.contains("172.20.") ||
        url.contains("172.21.") ||
        url.contains("172.22.") ||
        url.contains("172.23.") ||
        url.contains("172.24.") ||
        url.contains("172.25.") ||
        url.contains("172.26.") ||
        url.contains("172.27.") ||
        url.contains("172.28.") ||
        url.contains("172.29.") ||
        url.contains("172.30.") ||
        url.contains("172.31.")

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val memCache = MemoryCache.Builder()
            .maxSizeBytes(32 * 1024 * 1024) // 32 MB cap to prevent DMA-BUF OOM
            .build()
        val diskCache = DiskCache.Builder()
            .directory(context.cacheDir.resolve("coil_image_cache").toOkioPath())
            .maxSizeBytes(10 * 1024 * 1024) // 10 MB
            .build()
        val coilCallFactory = okhttp3.Call.Factory { request ->
            val url = request.url.toString()
            val useProxy = if (isPrivateIp(url)) false else settings.torMode != TorMode.DISABLED
            HttpClientManager.getHttpClient(useProxy).newCall(request)
        }
        return ImageLoader.Builder(context)
            .memoryCache { memCache }
            .diskCache { diskCache }
            .crossfade(true)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { coilCallFactory })) }
            .eventListener(
                object : EventListener() {
                    override fun onStart(request: ImageRequest) {
                        AmberLog.d(TAG, "Coil: loading ${request.data}")
                    }

                    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                        AmberLog.d(
                            TAG,
                            "Coil: loaded ${request.data} from ${result.dataSource}" +
                                " | mem=${memCache.size / 1024}/${memCache.maxSize / 1024} KB" +
                                " | disk=${diskCache.size / 1024}/${diskCache.maxSize / 1024} KB",
                        )
                    }

                    override fun onError(request: ImageRequest, result: ErrorResult) {
                        AmberLog.w(TAG, "Coil: error loading ${request.data}", result.throwable)
                    }

                    override fun onCancel(request: ImageRequest) {
                        AmberLog.d(TAG, "Coil: cancelled ${request.data}")
                    }
                },
            )
            .build()
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationIOScope.cancel()
    }

    suspend fun maybeCheckForUpdates() {
        val updater = zapstoreUpdater ?: return
        val lastCheck = LocalPreferences.getLastUpdateCheckTime(this)
        val now = System.currentTimeMillis()
        val shouldCheck = when (settings.updateCheckFrequency) {
            UpdateCheckFrequency.ON_STARTUP -> true
            UpdateCheckFrequency.DAILY -> now - lastCheck > 24 * 60 * 60 * 1000L
            UpdateCheckFrequency.WEEKLY -> now - lastCheck > 7 * 24 * 60 * 60 * 1000L
        }
        if (shouldCheck) {
            LocalPreferences.setLastUpdateCheckTime(this, now)
            updater.checkForUpdates()
        }
    }

    suspend fun sendFeedBack(
        subject: String,
        body: String,
        type: FeedbackType,
        account: Account,
    ): Boolean {
        val relays = setOfNotNull(
            RelayUrlNormalizer.normalizeOrNull("wss://nos.lol/"),
            RelayUrlNormalizer.normalizeOrNull("wss://relay.damus.io/"),
        )

        val repositoryEvent = GitRepositoryEvent(
            "",
            DEVELOPER_HEX_KEY,
            TimeUtils.now(),
            tags = arrayOf(arrayOf("d", "Amber")),
            "",
            "",
        )

        val template = GitIssueEvent.build(
            subject,
            body,
            EventHintBundle(repositoryEvent),
            listOf(PTag(DEVELOPER_HEX_KEY, null)),
            listOf(if (type == FeedbackType.BUG_REPORT) "bug" else "enhancement"),
        )

        val event = account.sign(
            template,
        )

        // This will automatically connect to these relays even if they are not in the list
        // once the events have been saved, it will disconnect from them unless there are other
        // filters with them
        return client.publishAndConfirm(event, relays)
    }

    companion object {
        var isAppInForeground = false
        const val TAG = "Amber"
        lateinit var instance: Amber
            private set

        const val DEVELOPER_HEX_KEY = "7579076d9aff0a4cfdefa7e2045f2486c7e5d8bc63bfc6b45397233e1bbfcb19"
    }
}

class FailedMigrationException(msg: String) : Exception(msg)
