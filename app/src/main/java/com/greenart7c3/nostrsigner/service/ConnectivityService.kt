package com.greenart7c3.nostrsigner.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.vitorpamplona.ammolite.relays.RelayPool
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

                val hasAnyRelayDisconnected = RelayPool.getAll().any { !it.isConnected() }

                if (lastNetwork != null && lastNetwork != network && hasAnyRelayDisconnected) {
                    scope.launch(Dispatchers.IO) {
                        RelayPool.getAll().forEach {
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
                        "onCapabilitiesChanged: ${network.networkHandle} hasMobileData ${NostrSigner.getInstance().isOnMobileDataState.value} hasWifi ${NostrSigner.getInstance().isOnWifiDataState.value}",
                    )

                    val hasAnyRelayDisconnected = RelayPool.getAll().any { !it.isConnected() }

                    if (NostrSigner.getInstance().updateNetworkCapabilities(networkCapabilities) && hasAnyRelayDisconnected) {
                        RelayPool.getAll().forEach {
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

    private fun createNotification(): Notification {
        val channelId = "ServiceChannel"
        val group = NotificationChannelGroupCompat.Builder("ServiceGroup")
            .setName(getString(R.string.service))
            .setDescription(getString(R.string.service_description))
            .build()
        val channel = NotificationChannelCompat.Builder(channelId, NotificationManager.IMPORTANCE_DEFAULT)
            .setName(getString(R.string.service))
            .setDescription(getString(R.string.amber_is_running_in_background))
            .setSound(null, null)
            .setGroup(group.id)
            .build()

        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.createNotificationChannelGroup(group)
        notificationManager.createNotificationChannel(channel)

        val notificationBuilder =
            NotificationCompat.Builder(this, channelId)
                .setGroup(group.id)
                .setContentTitle(getString(R.string.amber_is_running_in_background))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.ic_notification)

        return notificationBuilder.build()
    }

    override fun onCreate() {
        if (isStarted) return

        isStarted = true
        startForeground(1, createNotification())

        @Suppress("KotlinConstantConditions")
        if (BuildConfig.FLAVOR != "offline") {
            val connectivityManager =
                (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.let {
                NostrSigner.getInstance().updateNetworkCapabilities(it)
            }
        }

        timer.schedule(
            object : TimerTask() {
                override fun run() {
                    @Suppress("KotlinConstantConditions")
                    if (BuildConfig.FLAVOR == "offline") {
                        return
                    }

                    RelayPool.getAll().forEach {
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
}
