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
import com.greenart7c3.nostrsigner.relays.AmberRelayStats
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

                val hasAnyRelayDisconnected = Amber.instance.client.getAll().any { !it.isConnected() }
                if (lastNetwork != null && lastNetwork != network && hasAnyRelayDisconnected) {
                    scope.launch(Dispatchers.IO) {
                        reconnect()
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

                scope.launch(Dispatchers.IO) {
                    AmberRelayStats.updateNotification()
                    Log.d(
                        "ServiceManager NetworkCallback",
                        "onCapabilitiesChanged: ${network.networkHandle} hasMobileData ${Amber.instance.isOnMobileDataState.value} hasWifi ${Amber.instance.isOnWifiDataState.value}",
                    )

                    val hasAnyRelayDisconnected = Amber.instance.client.getAll().any { !it.isConnected() }
                    if (Amber.instance.updateNetworkCapabilities(networkCapabilities) && hasAnyRelayDisconnected) {
                        reconnect()
                    }
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
            }
        }

    override fun onBind(intent: Intent): IBinder {
        return null!!
    }

    override fun onCreate() {
        Log.d(Amber.TAG, "onCreate ConnectivityService isStarted: $isStarted")
        if (isStarted) return

        isStarted = true

        @Suppress("KotlinConstantConditions")
        if (BuildConfig.FLAVOR != "offline" && Amber.instance.client.getAll().isEmpty()) {
            Amber.instance.applicationIOScope.launch {
                Amber.instance.checkForNewRelays()
                NotificationDataSource.start()
            }
        }

        startForeground(1, AmberRelayStats.createNotification())

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

                    AmberRelayStats.updateNotification()
                    val hasAnyRelayDisconnected = Amber.instance.client.getAll().any { !it.isConnected() }
                    if (hasAnyRelayDisconnected) {
                        scope.launch {
                            reconnect()
                        }
                    }
                }
            },
            5000,
            61000,
        )
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
        return START_STICKY
    }

    suspend fun reconnect() {
        Log.d(Amber.TAG, "reconnecting relays")
        NotificationDataSource.stop()
        Amber.instance.client.getAll().forEach {
            it.disconnect()
        }
        delay(1000)
        Amber.instance.checkForNewRelays()
        NotificationDataSource.start()
        AmberRelayStats.updateNotification()
    }
}
