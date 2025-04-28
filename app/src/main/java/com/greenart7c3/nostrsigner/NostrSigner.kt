package com.greenart7c3.nostrsigner

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.greenart7c3.nostrsigner.okhttp.OkHttpWebSocket
import com.greenart7c3.nostrsigner.relays.MetadataRelayListener
import com.greenart7c3.nostrsigner.service.BootReceiver
import com.greenart7c3.nostrsigner.service.ConnectivityService
import com.greenart7c3.nostrsigner.service.RelayDisconnectService
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.MutableSubscriptionManager
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.ammolite.relays.RelaySetupInfoToConnect
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class NostrSigner : Application() {
    val factory = OkHttpWebSocket.BuilderFactory { _, useProxy ->
        HttpClientManager.getHttpClient(useProxy)
    }
    val client: NostrClient = NostrClient(factory)
    val applicationIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var databases = ConcurrentHashMap<String, AppDatabase>()
    lateinit var settings: AmberSettings

    val isOnMobileDataState = mutableStateOf(false)
    val isOnWifiDataState = mutableStateOf(false)

    fun updateNetworkCapabilities(networkCapabilities: NetworkCapabilities): Boolean {
        val isOnMobileData = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val isOnWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

        var changedNetwork = false

        if (isOnMobileDataState.value != isOnMobileData) {
            isOnMobileDataState.value = isOnMobileData

            changedNetwork = true
        }

        if (isOnWifiDataState.value != isOnWifi) {
            isOnWifiDataState.value = isOnWifi

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

    override fun onCreate() {
        super.onCreate()

        val alarmManager = this.getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, BootReceiver::class.java)
        intent.action = "CLEAR_LOGS"
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT + PendingIntent.FLAG_MUTABLE)

        // Set up the alarm to trigger every 24 hours
        val interval: Long = 24 * 60 * 60 * 1000 // 24 hours in milliseconds
        val triggerAtMillis = System.currentTimeMillis()

        // Set a repeating alarm
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            interval,
            pendingIntent,
        )

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

        runBlocking(Dispatchers.IO) {
            LocalPreferences.migrateTorSettings(this@NostrSigner)
            settings = LocalPreferences.loadSettingsFromEncryptedStorage()
        }

        try {
            this.startForegroundService(
                Intent(
                    this,
                    ConnectivityService::class.java,
                ),
            )
        } catch (e: Exception) {
            Log.e("NostrSigner", "Failed to start ConnectivityService", e)
        }
    }

    fun getDatabase(npub: String): AppDatabase {
        if (!databases.containsKey(npub)) {
            databases[npub] = AppDatabase.getDatabase(this, npub)
        }
        return databases[npub]!!
    }

    fun getSavedRelays(): Set<RelaySetupInfo> {
        val savedRelays = mutableSetOf<RelaySetupInfo>()
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

    suspend fun checkForNewRelays(
        shouldReconnect: Boolean = false,
        newRelays: Set<RelaySetupInfo> = emptySet(),
    ) {
        val savedRelays = getSavedRelays() + newRelays

        if (shouldReconnect) {
            checkIfRelaysAreConnected()
        }
        @Suppress("KotlinConstantConditions")
        if (BuildConfig.FLAVOR != "offline" && savedRelays.isNotEmpty()) {
            client.reconnect(
                savedRelays.map { RelaySetupInfoToConnect(it.url, if (isPrivateIp(it.url)) false else settings.useProxy, it.read, it.write, it.feedTypes) }.toTypedArray(),
                true,
            )
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

    private suspend fun checkIfRelaysAreConnected(tryAgain: Boolean = true) {
        Log.d("NostrSigner", "Checking if relays are connected")
        client.getAll().forEach { relay ->
            if (!relay.isConnected()) {
                relay.connectAndRunAfterSync {
                    val builder = OneTimeWorkRequest.Builder(RelayDisconnectService::class.java)
                    val inputData = Data.Builder()
                    inputData.putString("relay", relay.url)
                    builder.setInputData(inputData.build())
                    WorkManager.getInstance(instance).enqueue(builder.build())
                }
            }
        }
        var count = 0
        while (client.getAll().any { !it.isConnected() } && count < 10) {
            count++
            delay(1000)
        }
        if (client.getAll().any { !it.isConnected() } && tryAgain) {
            checkIfRelaysAreConnected(false)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationIOScope.cancel()
    }

    suspend fun fetchProfileData(
        account: Account,
        onPictureFound: (String) -> Unit,
    ) {
        @Suppress("KotlinConstantConditions")
        if (BuildConfig.FLAVOR == "offline") {
            return
        }

        SingletonImageLoader.setSafe {
            ImageLoader.Builder(this)
                .crossfade(true)
                .logger(DebugLogger())
                .components {
                    add(
                        OkHttpNetworkFetcherFactory(
                            callFactory = {
                                HttpClientManager.getHttpClient(settings.useProxy)
                            },
                        ),
                    )
                }
                .build()
        }

        val lastMetaData = LocalPreferences.getLastMetadataUpdate(this, account.npub)
        val lastCheck = LocalPreferences.getLastCheck(this, account.npub)
        val oneDayAgo = TimeUtils.oneDayAgo()
        val fifteenMinutesAgo = TimeUtils.fifteenMinutesAgo()
        if ((lastMetaData == 0L || oneDayAgo > lastMetaData) && (lastCheck == 0L || fifteenMinutesAgo > lastCheck)) {
            Log.d("NostrSigner", "Fetching profile data for ${account.npub}")
            LocalPreferences.setLastCheck(this, account.npub, TimeUtils.now())
            val listener = MetadataRelayListener(
                account = account,
                onReceiveEvent = { _, _, event ->
                    if (event.kind == MetadataEvent.KIND) {
                        (event as MetadataEvent).contactMetaData()?.let { metadata ->
                            metadata.name?.let { name ->
                                account.name = name
                                LocalPreferences.saveToEncryptedStorage(account = account, context = this)
                            }

                            metadata.profilePicture()?.let { url ->
                                LocalPreferences.saveProfileUrlToEncryptedStorage(url, account.npub)
                                LocalPreferences.setLastMetadataUpdate(this, account.npub, TimeUtils.now())
                                onPictureFound(url)
                            }
                        }
                    }
                },
            )

            val relays = LocalPreferences.loadSettingsFromEncryptedStorage().defaultProfileRelays.map {
                Relay(
                    url = it.url,
                    read = it.read,
                    write = it.write,
                    activeTypes = it.feedTypes,
                    socketBuilderFactory = factory,
                    forceProxy = settings.useProxy,
                    subs = MutableSubscriptionManager(),
                )
            }

            relays.forEach {
                it.register(listener)
                it.connectAndRunAfterSync {
                    it.sendFilter(
                        UUID.randomUUID().toString().substring(0, 4),
                        filters = listOf(
                            TypedFilter(
                                types = COMMON_FEED_TYPES,
                                filter = SincePerRelayFilter(
                                    kinds = listOf(MetadataEvent.KIND),
                                    authors = listOf(account.hexKey),
                                    limit = 1,
                                ),
                            ),
                        ),
                    )
                }
            }
        } else {
            Log.d("NostrSigner", "Using cached profile data for ${account.npub}")
            LocalPreferences.loadProfileUrlFromEncryptedStorage(account.npub)?.let {
                onPictureFound(it)
                return
            }
        }
    }

    companion object {
        @Volatile
        private var _instance: NostrSigner? = null
        val instance: NostrSigner get() =
            _instance ?: synchronized(this) {
                _instance ?: NostrSigner().also { _instance = it }
            }
    }
}
