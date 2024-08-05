package com.greenart7c3.nostrsigner.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
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
import kotlinx.coroutines.launch

class ConnectivityService : Service() {
    private var isStarted = false
    private val timer = Timer()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
        super.onDestroy()
    }
}
