package com.greenart7c3.nostrsigner.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.relays.RelayPool
import com.greenart7c3.nostrsigner.ui.NotificationType
import java.util.Timer
import java.util.TimerTask

class ConnectivityService : Service() {
    private val timer = Timer()
    private val binder = MyBinder()

    override fun onBind(intent: Intent): IBinder {
        return null!!
    }

    inner class MyBinder : Binder() {
        fun getService(): ConnectivityService = this@ConnectivityService
    }

    private fun createNotification(): Notification {
        val channelId = "ServiceChannel"
        val channel = NotificationChannel(channelId, "Checking Connectivity", NotificationManager.IMPORTANCE_DEFAULT)
        channel.setSound(null, null)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val notificationBuilder =
            NotificationCompat.Builder(this, channelId)
                .setContentTitle("Amber is running in background")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setColor(0xFFBF00)

        return notificationBuilder.build()
    }

    override fun onCreate() {
        startForeground(1, createNotification())

        timer.scheduleAtFixedRate(
            object : TimerTask() {
                override fun run() {
                    if (LocalPreferences.getNotificationType() == NotificationType.PUSH) return

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
            0,
            61000,
        )
        super.onCreate()
    }

    override fun onDestroy() {
        timer.cancel()
        super.onDestroy()
    }
}
