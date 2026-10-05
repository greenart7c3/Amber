package com.greenart7c3.nostrsigner.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.models.TorMode
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import java.util.Timer
import java.util.TimerTask
import kotlin.collections.set
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ConnectivityService : Service() {
    private val timer = Timer()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val networkChangeDetector = NetworkChangeDetector()
    private var pendingRelayReset: Job? = null

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
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
                Amber.instance.updateNetworkCapabilities(capabilities)
                Amber.instance.disconnectIntentionally()
            }
        }

    private fun onNetworkState(network: Network, capabilities: NetworkCapabilities) {
        // Always tracked (also under the kill switch) so the detector's baseline is
        // current when relays come back.
        Amber.instance.updateNetworkCapabilities(capabilities)
        val reason = networkChangeDetector.onNetwork(capabilities.toSnapshot(network))

        if (Amber.instance.settings.killSwitch.value) return

        AmberLog.d(
            "ServiceManager NetworkCallback",
            "network ${network.networkHandle} mobile ${Amber.instance.isOnMobileDataState.value} wifi ${Amber.instance.isOnWifiDataState.value} vpn ${Amber.instance.isOnVpnState.value} change: $reason",
        )

        if (reason != null) {
            if (Amber.instance.settings.torMode == TorMode.BUILTIN && !TorManager.isRunning.value) {
                // Built-in Tor gave up earlier (bounded startup retries
                // in runMigrations). The network is back, so retry now
                // instead of waiting for a manual restart.
                TorManager.restart(this, Amber.instance.applicationIOScope)
            }
            scheduleRelayReset(reason)
        } else if (!Amber.instance.client.isActive()) {
            scope.launch {
                Amber.instance.client.connect()
            }
        }
    }

    /**
     * Network changes arrive as a burst of callbacks (the new network, then its
     * capabilities as validation completes); wait for it to settle so the relays are
     * rebuilt once, on the final network.
     */
    @Synchronized
    private fun scheduleRelayReset(reason: String) {
        pendingRelayReset?.cancel()
        pendingRelayReset = scope.launch {
            delay(NETWORK_SETTLE_MS)
            Amber.instance.resetRelayConnections(reason)
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

        Amber.instance.startCleanLogsAlarm()
        Amber.instance.startUpdateCheckAlarm()
        Amber.instance.startBackupApplicationsAlarm()

        HttpClientManager.setDefaultUserAgent("Amber/${BuildConfig.VERSION_NAME}")

        LocalPreferences.allSavedAccounts(this).forEach {
            Amber.instance.databases[it.npub] = Amber.instance.getDatabase(it.npub)
            Amber.instance.applicationIOScope.launch {
                // Cached dao so the write invalidates CachingApplicationDao's getAll
                // entry for this account (the service can restart in a live process).
                val dao = Amber.instance.dao(it.npub)
                dao.getAllNotConnected()?.forEach { app ->
                    if (app.application.secret.isNotEmpty() && app.application.secret != app.application.key) {
                        app.application.isConnected = true
                        dao.insertApplicationWithPermissions(app)
                    }
                }
            }
        }

        Amber.instance.runMigrations(
            onDone = {
                startFunctions()
            },
        )

        super.onCreate()
    }

    fun startFunctions() {
        Amber.instance.applicationIOScope.launch {
            // Wait for Tor to be ready before connecting (if using built-in Tor)
            Amber.instance.waitForTorIfNeeded()
            if (!BuildFlavorChecker.isOfflineFlavor() && !Amber.instance.settings.killSwitch.value) {
                Amber.instance.client.connect()
                Amber.instance.applicationIOScope.launch {
                    Amber.instance.checkForNewRelaysAndUpdateAllFilters()
                }
            }

            if (!BuildFlavorChecker.isOfflineFlavor()) {
                val connectivityManager =
                    (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.let {
                    Amber.instance.updateNetworkCapabilities(it)
                }
            }

            timer.schedule(
                object : TimerTask() {
                    override fun run() {
                        if (BuildFlavorChecker.isOfflineFlavor()) {
                            return
                        }
                        if (Amber.instance.settings.killSwitch.value) return

                        scope.launch {
                            Amber.instance.notificationSubscription.updateFilter()
                        }
                    }
                },
                5000,
                UPDATE_FILTER_PERIOD_MS,
            )
        }
    }

    override fun onDestroy() {
        timer.cancel()
        cancelPendingRelayReset()
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
                Amber.instance.stats.createForegroundNotification(),
                foregroundServiceType,
            )
        } catch (e: Exception) {
            // Android 12+ can refuse a background FGS start with ForegroundServiceStartNotAllowedException;
            // swallow it and let the next foreground start (startServiceFromUi) retry.
            AmberLog.e(Amber.TAG, "Failed to start ConnectivityService in foreground", e)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    companion object {
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
