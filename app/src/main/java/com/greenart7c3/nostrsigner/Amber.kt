package com.greenart7c3.nostrsigner

import android.app.Application
import android.content.Intent
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.models.FeedbackType
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.greenart7c3.nostrsigner.okhttp.OkHttpWebSocket
import com.greenart7c3.nostrsigner.relays.AmberListenerSingleton
import com.greenart7c3.nostrsigner.relays.AmberRelayStats
import com.greenart7c3.nostrsigner.relays.NostrClientLoggerListener
import com.greenart7c3.nostrsigner.service.ClearLogsWorker
import com.greenart7c3.nostrsigner.service.ConnectivityService
import com.greenart7c3.nostrsigner.service.NotificationSubscription
import com.greenart7c3.nostrsigner.service.ProfileSubscription
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.sendAndWaitForResponse
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.utils.TimeUtils
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
import kotlinx.coroutines.launch

class Amber : Application(), LifecycleObserver {
    private var mainActivityRef: WeakReference<MainActivity?>? = null

    fun setMainActivity(activity: MainActivity?) {
        Log.d(TAG, "Setting main activity ref to $activity")
        mainActivityRef = WeakReference<MainActivity?>(activity)
    }

    fun getMainActivity(): MainActivity? {
        return if (mainActivityRef != null) mainActivityRef!!.get() else null
    }

    // Exists to avoid exceptions stopping the coroutine
    val exceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e("AmberCoroutine", "Caught exception: ${throwable.message}", throwable)
        }

    val applicationIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    var settings: AmberSettings = AmberSettings()

    val factory = OkHttpWebSocket.Builder { url ->
        val useProxy = if (isPrivateIp(url.url)) false else settings.useProxy
        HttpClientManager.getHttpClient(useProxy)
    }

    val client: NostrClient = NostrClient(factory, applicationIOScope)

    val stats = AmberRelayStats(client, applicationIOScope, this)

    // logs and stat counts.
    val listener = NostrClientLoggerListener(this, stats, applicationIOScope).also {
        client.subscribe(it)
    }

    // Authenticates with relays.
    val authCoordinator = RelayAuthenticator(client, applicationIOScope) { challenge, relay ->
        LocalPreferences.allAccounts(this).forEach { account ->
            val authed = account.createAuthEvent(
                relayUrl = relay.url,
                challenge = challenge,
            )
            client.sendIfExists(authed, relay.url)
        }
    }

    // TODO: I assume you want this sub to stay active in the background
    // TODO: Find a way to call either client.reconnect or notificationSubscription.updateFilters() to
    // keep the filter live when the relay inevitably disconnects. NostrClient doesn't try to reconnect
    // unless the filter is refreshed or a new event is sent.
    val notificationSubscription = NotificationSubscription(client, this)

    // This runs on the foreground only
    val profileSubscription = ProfileSubscription(client, this, applicationIOScope)

    private var databases = ConcurrentHashMap<String, AppDatabase>()

    val isOnMobileDataState = mutableStateOf(false)
    val isOnWifiDataState = mutableStateOf(false)
    val isOnOfflineState = mutableStateOf(false)
    private val isStartingApp = MutableStateFlow(false)
    val isStartingAppState = isStartingApp

    fun isSocksProxyAlive(proxyHost: String, proxyPort: Int): Boolean {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(proxyHost, proxyPort), 5000) // 3-second timeout
            socket.close()
            return true
        } catch (e: Exception) {
            if (e.message?.contains("EACCES (Permission denied)") == true) {
                AmberListenerSingleton.accountStateViewModel?.toast(getString(R.string.warning), getString(R.string.network_permission_message))
            } else if (e.message?.contains("socket failed: EPERM (Operation not permitted)") == true) {
                AmberListenerSingleton.accountStateViewModel?.toast(getString(R.string.warning), getString(R.string.network_permission_message))
            }
            Log.e(TAG, "Failed to connect to proxy", e)
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

    private fun startCleanLogsAlarm() {
        val workRequest = PeriodicWorkRequestBuilder<ClearLogsWorker>(
            24,
            TimeUnit.HOURS,
        )
            .setInitialDelay(5, TimeUnit.MINUTES) // Delay first run by 5 minutes
            .addTag("clearLogsWork")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ClearLogsWorker",
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest,
        )
    }

    override fun onCreate() {
        super.onCreate()
        isStartingApp.value = true

        Log.d(TAG, "onCreate Amber")

        startCleanLogsAlarm()

        HttpClientManager.setDefaultUserAgent("Amber/${BuildConfig.VERSION_NAME}")
        _instance = this

        LocalPreferences.allSavedAccounts(this).forEach {
            databases[it.npub] = AppDatabase.getDatabase(this, it.npub)
            applicationIOScope.launch {
                databases[it.npub]?.applicationDao()?.getAllNotConnected()?.forEach { app ->
                    if (app.application.secret.isNotEmpty() && app.application.secret != app.application.key) {
                        app.application.isConnected = true
                        databases[it.npub]?.applicationDao()?.insertApplicationWithPermissions(app)
                    }
                }
            }
        }

        runMigrations()
    }

    fun runMigrations() {
        applicationIOScope.launch {
            try {
                LocalPreferences.allSavedAccounts(this@Amber).forEach {
                    if (LocalPreferences.didMigrateFromLegacyStorage(this@Amber, it.npub)) {
                        LocalPreferences.deleteLegacyUserPreferenceFile(this@Amber, it.npub)
                        if (LocalPreferences.existsLegacySettings(this@Amber)) LocalPreferences.deleteSettingsPreferenceFile(this@Amber)
                    }
                }
                if (LocalPreferences.existsLegacySettings(this@Amber)) {
                    LocalPreferences.allLegacySavedAccounts(this@Amber).forEach {
                        LocalPreferences.migrateFromSharedPrefs(this@Amber, it.npub)
                        LocalPreferences.loadFromEncryptedStorage(this@Amber, it.npub)
                    }
                }
                LocalPreferences.migrateTorSettings(this@Amber)
                settings = LocalPreferences.loadSettingsFromEncryptedStorage()
                LocalPreferences.reloadApp()
                isStartingApp.value = false

                checkForNewRelaysAndUpdateAllFilters(true)
                if (settings.killSwitch) {
                    client.disconnect()
                }
                launch(Dispatchers.Main) {
                    ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                        override fun onStart(owner: LifecycleOwner) {
                            Log.d("ProcessLifecycleOwner", "App in foreground")
                            isAppInForeground = true

                            // activates the profile filter only when the app is in the foreground
                            if (!settings.killSwitch) {
                                applicationIOScope.launch {
                                    profileSubscription.updateFilter()
                                }
                            }
                        }

                        override fun onStop(owner: LifecycleOwner) {
                            Log.d("ProcessLifecycleOwner", "App in background")
                            isAppInForeground = false

                            // closes the filter when in the background
                            applicationIOScope.launch {
                                profileSubscription.closeSub()
                            }
                        }
                    })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to run migrations", e)
                isStartingApp.value = false
                if (e is CancellationException) throw e
            }
        }
    }

    fun startService() {
        try {
            Log.d(TAG, "Starting ConnectivityService")
            val serviceIntent = Intent(this, ConnectivityService::class.java)
            this.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ConnectivityService", e)
            if (e is CancellationException) throw e
        }
    }

    fun reconnect() {
        if (settings.killSwitch) {
            return
        }
        Log.d(TAG, "reconnecting relays")
        val wasActive = client.isActive()
        if (!wasActive) {
            client.connect()
        }
        client.reconnect(wasActive)
        stats.updateNotification()
    }

    fun getDatabase(npub: String): AppDatabase {
        if (!databases.containsKey(npub)) {
            databases[npub] = AppDatabase.getDatabase(this, npub)
        }
        return databases[npub]!!
    }

    fun getSavedRelays(): Set<NormalizedRelayUrl> {
        val savedRelays = mutableSetOf<NormalizedRelayUrl>()
        LocalPreferences.allSavedAccounts(this).forEach { accountInfo ->
            val database = getDatabase(accountInfo.npub)
            database.applicationDao().getAllApplications().forEach {
                it.application.relays.forEach { setupInfo ->
                    savedRelays.add(setupInfo)
                }
            }
        }

        savedRelays.addAll(settings.defaultRelays)

        return savedRelays
    }

    suspend fun checkForNewRelaysAndUpdateAllFilters(
        shouldReconnect: Boolean = false,
    ) {
        if (settings.killSwitch) {
            client.disconnect()
            return
        }

        val wasActive = client.isActive()

        @Suppress("KotlinConstantConditions")
        // TODO: You can filter inside each update filter for only
        // localhost relays and keep these alive even on the offline
        // mode
        if (BuildConfig.FLAVOR != "offline") {
            // these update the relay list in the filters and send them to the
            // relay, reconnecting if needed
            if (isAppInForeground) {
                profileSubscription.updateFilter()
            }
            notificationSubscription.updateFilter()
        }
        delay(3000)
        Log.d(TAG, "checkForNewRelaysAndUpdateAllFilters wasActive: $wasActive")
        if (!wasActive) {
            client.connect()
        }

        if (shouldReconnect) {
            client.reconnect(wasActive)
        }
    }

    fun isPrivateIp(url: String): Boolean {
        return url.contains("127.0.0.1") ||
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
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationIOScope.cancel()
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
            "7579076d9aff0a4cfdefa7e2045f2486c7e5d8bc63bfc6b45397233e1bbfcb19",
            TimeUtils.now(),
            tags = arrayOf(arrayOf("d", "Amber")),
            "",
            "",
        )

        val template = GitIssueEvent.build(
            subject,
            body,
            EventHintBundle(repositoryEvent),
            listOf(PTag("7579076d9aff0a4cfdefa7e2045f2486c7e5d8bc63bfc6b45397233e1bbfcb19", null)),
            listOf(if (type == FeedbackType.BUG_REPORT) "bug" else "enhancement"),
        )

        val event = account.signer.signerSync.sign(
            template,
        )

        // This will automatically connect to these relays even if they are not in the list
        // once the events have been saved, it will disconnect from them unless there are other
        // filters with them
        return client.sendAndWaitForResponse(event, relays)
    }

    companion object {
        var isAppInForeground = false
        const val TAG = "Amber"

        @Volatile
        private var _instance: Amber? = null
        val instance: Amber get() =
            _instance ?: synchronized(this) {
                _instance ?: Amber().also { _instance = it }
            }
    }
}
