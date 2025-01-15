package com.greenart7c3.nostrsigner.relays

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.vitorpamplona.ammolite.relays.RelayStats
import kotlinx.coroutines.launch

object AmberRelayStats {
    var connected = 0
    var available = 0
    init {
        NostrSigner.getInstance().applicationIOScope.launch {
            NostrSigner.getInstance().client.relayStatusFlow().collect {
                connected = it.connected
                available = it.available
            }
        }
    }
    private val innerCache = mutableMapOf<String, AmberRelayStat>()

    fun createNotification(): Notification {
        val channelId = "ServiceChannel"
        val group = NotificationChannelGroupCompat.Builder("ServiceGroup")
            .setName(NostrSigner.getInstance().getString(R.string.service))
            .setDescription(NostrSigner.getInstance().getString(R.string.service_description))
            .build()
        val channel = NotificationChannelCompat.Builder(channelId, NotificationManager.IMPORTANCE_DEFAULT)
            .setName(NostrSigner.getInstance().getString(R.string.service))
            .setDescription(NostrSigner.getInstance().getString(R.string.amber_is_running_in_background))
            .setSound(null, null)
            .setGroup(group.id)
            .build()

        val notificationManager = NotificationManagerCompat.from(NostrSigner.getInstance())
        notificationManager.createNotificationChannelGroup(group)
        notificationManager.createNotificationChannel(channel)

        val message = NostrSigner.getInstance().client.getAll().joinToString("\n") {
            "${it.url} ${if (it.isConnected()) "connected" else "disconnected"}\n" +
                "Ping: ${RelayStats.get(it.url).pingInMs}ms" +
                if (get(it.url).sent > 0) {
                    " Sent: ${get(it.url).sent}"
                } else {
                    "" +
                        if (get(it.url).received > 0) {
                            " Received: ${get(it.url).received}"
                        } else {
                            "" +
                                if (get(it.url).failed > 0) {
                                    " Rejected by relay: ${get(it.url).failed}"
                                } else {
                                    "" +
                                        if (RelayStats.get(it.url).errorCounter > 0) " Error: ${RelayStats.get(it.url).errorCounter}" else ""
                                }
                        }
                }
        }

        val notificationBuilder =
            NotificationCompat.Builder(NostrSigner.getInstance(), channelId)
                .setGroup(group.id)
                .setContentTitle("$connected of $available connected relays")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(message),
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.ic_notification)

        return notificationBuilder.build()
    }

    fun updateNotification() {
        Log.d("ConnectivityService", "updateNotification")
        val notificationManager = NotificationManagerCompat.from(NostrSigner.getInstance())
        if (ActivityCompat.checkSelfPermission(NostrSigner.getInstance(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(1, createNotification())
        }
    }

    fun get(url: String): AmberRelayStat = innerCache.getOrPut(url) { AmberRelayStat() }

    fun addSent(url: String) {
        get(url).addSent()
        updateNotification()
    }

    fun addReceived(url: String) {
        get(url).addReceived()
        updateNotification()
    }

    fun addFailed(url: String) {
        get(url).addFailed()
        updateNotification()
    }
}

class AmberRelayStat(
    var sent: Long = 0L,
    var received: Long = 0L,
    var failed: Long = 0L,
) {
    fun addSent() {
        sent++
    }

    fun addReceived() {
        received++
    }

    fun addFailed() {
        failed++
    }
}
