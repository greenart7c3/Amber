package com.greenart7c3.nostrsigner.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.KeystorePolicy
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.amber
import com.greenart7c3.nostrsigner.models.TorMode
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import java.util.Timer
import java.util.TimerTask
import kotlin.collections.set
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ConnectivityService : Service() {
    @Suppress("NoComments")
    // The system constructs the service, so it reads the container from its own context once attached.
    private val amber: Amber get() = applicationContext.amber

    private val timer = Timer()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val networkChangeDetector = NetworkChangeDetector()
    private var pendingRelayReset: Job? = null

    @Suppress("NoComments")
    // With requireUnlockedDevice on, a start while the device is locked (an app
    // update, or the system restarting the process) can't decrypt the stored
    // accounts and connections, so the NIP-46 filter comes up empty and stays
    // that way. Rebuild it as soon as the user unlocks.
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_USER_PRESENT) return
            if (!KeystorePolicy.requireUnlockedDevice(context)) return
            if (amber.settings.killSwitch.value) return
            AmberLog.d(Amber.TAG, "Device unlocked, refreshing relay subscriptions")
            amber.applicationIOScope.launch {
                amber.relayClient.checkForNewRelaysAndUpdateAllFilters()
            }
        }
    }
    private var unlockReceiverRegistered = false

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            @Suppress("NoComments")
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                if (BuildFlavorChecker.isOfflineFlavor()) return

                // onCapabilitiesChanged follows right away, but read them here too in
                // case it does not, so a new default network is never missed.
                val connectivityManager =
                    (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
                connectivityManager.getNetworkCapabilities(network)?.let {
                    onNetworkState(network, it)
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                if (BuildFlavorChecker.isOfflineFlavor()) return

                onNetworkState(network, networkCapabilities)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                if (BuildFlavorChecker.isOfflineFlavor()) return

                AmberLog.d("ServiceManager NetworkCallback", "onLost: ${network.networkHandle}")
                if (!networkChangeDetector.onLost(network.networkHandle)) return
                cancelPendingRelayReset()

                val connectivityManager =
                    (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
                val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                amber.networkState.update(capabilities)
                amber.relayClient.disconnectIntentionally()
            }
        }

    private fun onNetworkState(network: Network, capabilities: NetworkCapabilities) {
        val reason = trackNetworkChangeEvenUnderKillSwitch(network, capabilities)

        if (amber.settings.killSwitch.value) return

        AmberLog.d(
            "ServiceManager NetworkCallback",
            "network ${network.networkHandle} mobile ${amber.networkState.isOnMobileData} wifi ${amber.networkState.isOnWifi} vpn ${amber.networkState.isOnVpn} change: $reason",
        )

        if (reason != null) {
            restartBuiltInTorThatGaveUpStarting()
            if (!isWaitingForBuiltInTor()) {
                scheduleRelayReset(reason)
            }
        } else if (shouldReconnectOnUnchangedNetwork()) {
            scope.launch {
                amber.relayClient.client.connect()
            }
        }
    }

    private fun isWaitingForBuiltInTor(): Boolean = amber.settings.torMode == TorMode.BUILTIN && !TorManager.isRunning.value

    private fun hasActiveSubscriptions(): Boolean = amber.relayClient.client.registrySizes().let { it.liveRequests > 0 || it.liveCounts > 0 }

    private fun shouldReconnectOnUnchangedNetwork(): Boolean = !isWaitingForBuiltInTor() &&
        !amber.relayClient.client.isActive() &&
        hasActiveSubscriptions()

    private fun trackNetworkChangeEvenUnderKillSwitch(network: Network, capabilities: NetworkCapabilities): String? {
        amber.networkState.update(capabilities)
        return networkChangeDetector.onNetwork(capabilities.toSnapshot(network))
    }

    private fun restartBuiltInTorThatGaveUpStarting() {
        if (amber.settings.torMode != TorMode.BUILTIN) return
        val status = TorManager.status.value
        if (status is TorStatus.Failed || status is TorStatus.Stopped) {
            TorManager.restart(this, amber.applicationIOScope)
        }
    }

    /**
     * Network changes arrive as a burst of callbacks (the new network, then its
     * capabilities as validation completes); wait for it to settle so the relays are
     * rebuilt once, on the final network.
     */
    @Suppress("NoComments")
    @Synchronized
    private fun scheduleRelayReset(reason: String) {
        pendingRelayReset?.cancel()
        pendingRelayReset = scope.launch {
            delay(NETWORK_SETTLE_MS.milliseconds)
            amber.relayClient.resetRelayConnections(reason)
        }
    }

    @Synchronized
    private fun cancelPendingRelayReset() {
        pendingRelayReset?.cancel()
        pendingRelayReset = null
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        AmberLog.d(Amber.TAG, "onCreate ConnectivityService")

        if (!BuildFlavorChecker.isOfflineFlavor()) {
            TorManager.init(this)
        }

        ClearLogsWorker.schedule(this)
        UpdateCheckWorker.schedule(this)
        BackupApplicationsWorker.schedule(amber)

        HttpClientManager.setDefaultUserAgent("Amber/${BuildConfig.VERSION_NAME}")

        SavedAccounts.all(this).forEach {
            amber.accountDatabases.getDatabase(it.npub)
            amber.applicationIOScope.launch {
                val cacheInvalidatingDaos = amber.accountDatabases.daos(it.npub)
                cacheInvalidatingDaos.applicationListDao.getAllNotConnected().forEach { app ->
                    if (app.application.secret.isNotEmpty() && app.application.secret != app.application.key) {
                        app.application.isConnected = true
                        cacheInvalidatingDaos.permissionWriteDao.insertApplicationWithPermissions(app)
                    }
                }
            }
        }

        amber.runMigrations(
            onDone = {
                startFunctions()
            },
        )

        super.onCreate()
    }

    fun startFunctions() {
        amber.applicationIOScope.launch {
            if (!BuildFlavorChecker.isOfflineFlavor()) {
                val connectivityManager =
                    (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.let {
                    amber.networkState.update(it)
                }

                registerUnlockReceiver()
            }

            timer.schedule(
                object : TimerTask() {
                    override fun run() {
                        if (BuildFlavorChecker.isOfflineFlavor()) {
                            return
                        }
                        if (amber.settings.killSwitch.value) return
                        if (isWaitingForBuiltInTor()) return

                        scope.launch {
                            amber.notificationSubscription.updateFilter()
                        }
                    }
                },
                5000,
                UPDATE_FILTER_PERIOD_MS,
            )

            amber.torStartup.waitIfNeeded()
            if (!BuildFlavorChecker.isOfflineFlavor() && !amber.settings.killSwitch.value) {
                amber.relayClient.checkForNewRelaysAndUpdateAllFilters()
            }
        }
    }

    @Suppress("NoComments")
    private fun registerUnlockReceiver() {
        // USER_PRESENT comes from SystemUI, not the system process, so a
        // non-exported receiver never gets it. It is a protected
        // broadcast: no other app can send it.
        ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_EXPORTED,
        )
        unlockReceiverRegistered = true
    }

    override fun onDestroy() {
        isRunning = false
        timer.cancel()
        cancelPendingRelayReset()
        if (unlockReceiverRegistered) {
            unregisterReceiver(unlockReceiver)
            unlockReceiverRegistered = false
        }
        if (!BuildFlavorChecker.isOfflineFlavor()) {
            try {
                AmberLog.d(Amber.TAG, "unregisterNetworkCallback")
                val connectivityManager =
                    (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (e: Exception) {
                AmberLog.d(Amber.TAG, "Failed to unregisterNetworkCallback", e)
            }
        }
        super.onDestroy()
    }

    @Suppress("NoComments")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AmberLog.d(Amber.TAG, "onStartCommand")
        val foregroundServiceType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        try {
            ServiceCompat.startForeground(
                this,
                1,
                amber.relayClient.stats.createForegroundNotification(),
                foregroundServiceType,
            )
            isRunning = true
        } catch (e: Exception) {
            // Android 12+ can refuse a background FGS start with ForegroundServiceStartNotAllowedException;
            // swallow it and let the next foreground start (startService) retry.
            AmberLog.e(Amber.TAG, "Failed to start ConnectivityService in foreground", e)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    companion object {
        @Volatile
        var isRunning = false
            private set

        /**
         * How often the safety-net subscription refresh runs. Every real state
         * change (login/logout, relay edits, app connect/disconnect, NIP-46
         * CONNECT, backup restore, kill switch) already refreshes the filters
         * explicitly, and Quartz replays subscriptions on reconnect, so this
         * tick only covers missed paths. Kept slow: it wakes the CPU even when
         * nothing changed, and a 30s period prevented doze 2,880 times a day.
         */
        const val UPDATE_FILTER_PERIOD_MS = 5 * 60 * 1000L

        private const val NETWORK_SETTLE_MS = 1_000L

        private val TRACKED_TRANSPORTS = intArrayOf(
            NetworkCapabilities.TRANSPORT_CELLULAR,
            NetworkCapabilities.TRANSPORT_WIFI,
            NetworkCapabilities.TRANSPORT_ETHERNET,
            NetworkCapabilities.TRANSPORT_BLUETOOTH,
            NetworkCapabilities.TRANSPORT_VPN,
        )

        private fun NetworkCapabilities.toSnapshot(network: Network) = NetworkSnapshot(
            networkId = network.networkHandle,
            transports = TRACKED_TRANSPORTS.filter { hasTransport(it) }.toSet(),
            validated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        )
    }
}
