package com.greenart7c3.nostrsigner.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.ui.NotificationType
import com.vitorpamplona.ammolite.relays.RelayPool
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

                if (lastNetwork != null && lastNetwork != network && NostrSigner.getInstance().settings.notificationType == NotificationType.DIRECT) {
                    scope.launch(Dispatchers.IO) {
                        NotificationDataSource.stopSync()
                        delay(1000)
                        NotificationDataSource.start()
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
                    if (NostrSigner.getInstance().updateNetworkCapabilities(networkCapabilities) && NostrSigner.getInstance().settings.notificationType == NotificationType.DIRECT) {
                        NotificationDataSource.stopSync()
                        delay(1000)
                        NotificationDataSource.start()
                    }
                }
            }
        }

    override fun onBind(intent: Intent): IBinder {
        return null!!
    }

    private fun createNotification(): Notification {
        val channelId = "ServiceChannel"
        val channel = NotificationChannel(channelId, getString(R.string.checking_connectivity), NotificationManager.IMPORTANCE_DEFAULT)
        channel.setSound(null, null)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val notificationBuilder =
            NotificationCompat.Builder(this, channelId)
                .setContentTitle(getString(R.string.amber_is_running_in_background))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setColor(0xFFBF00)

        return notificationBuilder.build()
    }

    override fun onCreate() {
        if (isStarted) return

        isStarted = true
        startForeground(1, createNotification())

        scope.launch(Dispatchers.IO) {
            PushNotificationUtils.hasInit = false
            PushNotificationUtils.init(LocalPreferences.allSavedAccounts(this@ConnectivityService))
        }

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
                    if (NostrSigner.getInstance().settings.notificationType == NotificationType.PUSH) return

                    Log.d("ConnectivityService", "Checking connectivity...")
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
