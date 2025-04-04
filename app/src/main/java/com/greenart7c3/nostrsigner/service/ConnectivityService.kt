package com.greenart7c3.nostrsigner.service

import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.IBinder
import android.util.Log
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.relays.AmberRelayStats
import java.util.Timer
import java.util.TimerTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

                val hasAnyRelayDisconnected = NostrSigner.instance.client.getAll().any { !it.isConnected() }

                if (lastNetwork != null && lastNetwork != network && hasAnyRelayDisconnected) {
                    scope.launch(Dispatchers.IO) {
                        NostrSigner.instance.client.getAll().forEach {
                            if (!it.isConnected()) {
                                Log.d(
                                    "ConnectivityService",
                                    "Relay ${it.url} is not connected, reconnecting...",
                                )
                                it.connectAndSendFiltersIfDisconnected()
                            }
                        }
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
                    Log.d(
                        "ServiceManager NetworkCallback",
                        "onCapabilitiesChanged: ${network.networkHandle} hasMobileData ${NostrSigner.instance.isOnMobileDataState.value} hasWifi ${NostrSigner.instance.isOnWifiDataState.value}",
                    )

                    val hasAnyRelayDisconnected = NostrSigner.instance.client.getAll().any { !it.isConnected() }

                    if (NostrSigner.instance.updateNetworkCapabilities(networkCapabilities) && hasAnyRelayDisconnected) {
                        NostrSigner.instance.client.getAll().forEach {
                            if (!it.isConnected()) {
                                Log.d(
                                    "ConnectivityService",
                                    "Relay ${it.url} is not connected, reconnecting...",
                                )
                                it.connectAndSendFiltersIfDisconnected()
                            }
                        }
                    }
                }
            }
        }

    override fun onBind(intent: Intent): IBinder {
        return null!!
    }

    override fun onCreate() {
        if (isStarted) return

        Log.d("ConnectivityService", "onCreate")

        isStarted = true

        @Suppress("KotlinConstantConditions")
        if (BuildConfig.FLAVOR != "offline" && NostrSigner.instance.client.getAll().isEmpty()) {
            NostrSigner.instance.applicationIOScope.launch {
                NostrSigner.instance.checkForNewRelays()
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
                NostrSigner.instance.updateNetworkCapabilities(it)
            }
        }

        timer.schedule(
            object : TimerTask() {
                override fun run() {
                    @Suppress("KotlinConstantConditions")
                    if (BuildConfig.FLAVOR == "offline") {
                        return
                    }

                    NostrSigner.instance.client.getAll().forEach {
                        if (!it.isConnected()) {
                            Log.d(
                                "ConnectivityService",
                                "Relay ${it.url} is not connected, reconnecting...",
                            )
                            it.connectAndSendFiltersIfDisconnected()
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
                val connectivityManager =
                    (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (e: Exception) {
                Log.d("connectivityManager", "Failed to unregisterNetworkCallback", e)
            }
        }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ConnectivityService", "onStartCommand")
        return START_STICKY
    }
}
