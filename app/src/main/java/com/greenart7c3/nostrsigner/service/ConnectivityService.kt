package com.greenart7c3.nostrsigner.service

import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.IBinder
import android.util.Log
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.database.LogEntity
import java.util.Timer
import java.util.TimerTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ConnectivityService : Service() {
    private var isStarted = false
    private val timer = Timer()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            var lastNetwork: Network? = null

            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                @Suppress("KotlinConstantConditions")
                if (BuildConfig.FLAVOR == "offline") return
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

                @Suppress("KotlinConstantConditions")
                if (BuildConfig.FLAVOR == "offline") return
                if (Amber.instance.settings.killSwitch.value) return

                scope.launch(Dispatchers.IO) {
                    Log.d(
                        "ServiceManager NetworkCallback",
                        "onCapabilitiesChanged: ${network.networkHandle} hasMobileData ${Amber.instance.isOnMobileDataState.value} hasWifi ${Amber.instance.isOnWifiDataState.value}",
                    )
                    if (!Amber.instance.client.isActive()) {
                        Amber.instance.client.connect()
                    }
                    Amber.instance.client.reconnect(true)
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                @Suppress("KotlinConstantConditions")
                if (BuildConfig.FLAVOR == "offline") return

                lastNetwork = null

                val connectivityManager =
                    (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
                val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                Amber.instance.updateNetworkCapabilities(capabilities)
                Amber.instance.client.disconnect()
            }
        }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        Log.d(Amber.TAG, "onCreate ConnectivityService isStarted: $isStarted")
        if (isStarted) return
        isStarted = true
        Amber.instance.stats.createNotification(forceCreate = true)?.let {
            startForeground(1, it)
        }
        Amber.instance.applicationIOScope.launch {
            while (Amber.instance.isStartingAppState.value) {
                delay(1000)
            }
            @Suppress("KotlinConstantConditions")
            if (BuildConfig.FLAVOR != "offline" && !Amber.instance.settings.killSwitch.value) {
                Amber.instance.client.connect()
                Amber.instance.applicationIOScope.launch {
                    Amber.instance.checkForNewRelaysAndUpdateAllFilters()
                }
            }

            @Suppress("KotlinConstantConditions")
            if (BuildConfig.FLAVOR != "offline") {
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
                        @Suppress("KotlinConstantConditions")
                        if (BuildConfig.FLAVOR == "offline") {
                            return
                        }
                        if (Amber.instance.settings.killSwitch.value) return

                        scope.launch {
                            LocalPreferences.allSavedAccounts(Amber.instance).forEach { accountInfo ->
                                val now = System.currentTimeMillis() / 1000
                                val deleted = Amber.instance.getDatabase(accountInfo.npub).dao().deleteOldApplications(now)
                                if (deleted > 0) {
                                    Amber.instance.getLogDatabase(accountInfo.npub).dao().insertLog(
                                        LogEntity(
                                            id = 0,
                                            url = "",
                                            type = "deleteApplications",
                                            message = "Deleted $deleted expired applications",
                                            time = System.currentTimeMillis(),
                                        ),
                                    )
                                }
                            }
                        }

                        scope.launch {
                            if (!Amber.instance.client.isActive()) {
                                Amber.instance.client.connect()
                            }
                            Amber.instance.client.reconnect(true)
                        }
                    }
                },
                5000,
                61000,
            )
        }
        super.onCreate()
    }

    override fun onDestroy() {
        isStarted = false
        timer.cancel()
        @Suppress("KotlinConstantConditions")
        if (BuildConfig.FLAVOR != "offline") {
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
        Amber.instance.stats.createNotification(forceCreate = true)?.let {
            startForeground(1, it)
        }
        return START_STICKY
    }
}
