package com.greenart7c3.nostrsigner.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import java.util.Timer
import java.util.TimerTask
import kotlin.collections.set
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ConnectivityService : Service() {
    private val timer = Timer()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            var lastNetwork: Network? = null

            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                if (BuildFlavorChecker.isOfflineFlavor()) return
                if (Amber.instance.settings.killSwitch.value) return

                if (lastNetwork != null && lastNetwork != network) {
                    scope.launch(Dispatchers.IO) {
                        if (!Amber.instance.client.isActive()) {
                            Amber.instance.client.connect()
                        }
                        Amber.instance.client.reconnect(true)
                    }
                }
                lastNetwork = network
            }

            // Network capabilities have changed for the network
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)

                if (BuildFlavorChecker.isOfflineFlavor()) return
                if (Amber.instance.settings.killSwitch.value) return

                val changed = Amber.instance.updateNetworkCapabilities(networkCapabilities)

                scope.launch(Dispatchers.IO) {
                    Log.d(
                        "ServiceManager NetworkCallback",
                        "onCapabilitiesChanged: ${network.networkHandle} hasMobileData ${Amber.instance.isOnMobileDataState.value} hasWifi ${Amber.instance.isOnWifiDataState.value}",
                    )
                    if (!Amber.instance.client.isActive()) {
                        Amber.instance.client.connect()
                    }
                    if (changed) {
                        Amber.instance.client.reconnect(true)
                    }
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                if (BuildFlavorChecker.isOfflineFlavor()) return

                lastNetwork = null

                val connectivityManager =
                    (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
                val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                Amber.instance.updateNetworkCapabilities(capabilities)
                Amber.instance.disconnectIntentionally()
            }
        }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        Log.d(Amber.TAG, "onCreate ConnectivityService")

        if (!BuildFlavorChecker.isOfflineFlavor()) {
            Amber.instance.stats.createNotificationChannel()
            TorManager.init(this)
        }

        Amber.instance.isStartingAppState.value = true
        Amber.instance.isStartingApp.value = true

        Amber.instance.startCleanLogsAlarm()
        Amber.instance.startUpdateCheckAlarm()

        HttpClientManager.setDefaultUserAgent("Amber/${BuildConfig.VERSION_NAME}")

        LocalPreferences.allSavedAccounts(this).forEach {
            Amber.instance.databases[it.npub] = Amber.instance.getDatabase(it.npub)
            Amber.instance.applicationIOScope.launch {
                Amber.instance.databases[it.npub]?.dao()?.getAllNotConnected()?.forEach { app ->
                    if (app.application.secret.isNotEmpty() && app.application.secret != app.application.key) {
                        app.application.isConnected = true
                        Amber.instance.databases[it.npub]?.dao()?.insertApplicationWithPermissions(app)
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
            Amber.instance.isStartingAppState.first { !it }

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
                30000,
            )
        }
    }

    override fun onDestroy() {
        timer.cancel()
        if (!BuildFlavorChecker.isOfflineFlavor()) {
            try {
                Log.d(Amber.TAG, "unregisterNetworkCallback")
                val connectivityManager =
                    (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (e: Exception) {
                Log.d(Amber.TAG, "Failed to unregisterNetworkCallback", e)
            }
        }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(Amber.TAG, "onStartCommand")
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
            Log.e(Amber.TAG, "Failed to start ConnectivityService in foreground", e)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return START_STICKY
    }
}
