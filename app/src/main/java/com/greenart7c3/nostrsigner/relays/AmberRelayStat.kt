package com.greenart7c3.nostrsigner.relays

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.MainActivity
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.service.ReconnectReceiver
import com.vitorpamplona.ammolite.relays.RelayStats
import kotlinx.coroutines.launch

object AmberRelayStats {
    var oldMessage = ""
    var connected = 0
    var available = 0
    init {
        Amber.instance.applicationIOScope.launch {
            Amber.instance.client.relayStatusFlow().collect {
                connected = it.connected
                available = it.available
            }
        }
    }
    private val innerCache = mutableMapOf<String, AmberRelayStat>()

    fun createNotification(): Notification? {
        val channelId = "ServiceChannel"
        val group = NotificationChannelGroupCompat.Builder("ServiceGroup")
            .setName(Amber.instance.getString(R.string.service))
            .setDescription(Amber.instance.getString(R.string.service_description))
            .build()
        val channel = NotificationChannelCompat.Builder(channelId, NotificationManager.IMPORTANCE_DEFAULT)
            .setName(Amber.instance.getString(R.string.service))
            .setDescription(Amber.instance.getString(R.string.amber_is_running_in_background))
            .setSound(null, null)
            .setGroup(group.id)
            .build()

        val notificationManager = NotificationManagerCompat.from(Amber.instance)
        notificationManager.createNotificationChannelGroup(group)
        notificationManager.createNotificationChannel(channel)

        val localConnected = Amber.instance.client.connectedRelays()
        val localAvailable = Amber.instance.client.getAll().size
        if (localAvailable != available || localConnected != connected) {
            available = localAvailable
            connected = localConnected
        }

        val message = Amber.instance.client.getAll().joinToString("\n") {
            val connected = "${it.url} ${if (it.isConnected()) Amber.instance.getString(R.string.connected) else Amber.instance.getString(R.string.disconnected)}\n"
            val ping = Amber.instance.getString(R.string.ping_ms, RelayStats.get(it.url).pingInMs)
            val sent = if (get(it.url).sent > 0) {
                Amber.instance.getString(R.string.sent, get(it.url).sent)
            } else {
                ""
            }
            val received = if (get(it.url).received > 0) {
                Amber.instance.getString(R.string.received, get(it.url).received)
            } else {
                ""
            }
            val failed = if (get(it.url).failed > 0) {
                Amber.instance.getString(R.string.rejected_by_relay, get(it.url).failed)
            } else {
                ""
            }
            val error = if (RelayStats.get(it.url).errorCounter > 0) Amber.instance.getString(R.string.error, RelayStats.get(it.url).errorCounter) else ""

            connected + ping + sent + received + failed + error
        }
        if (message == oldMessage && oldMessage.isNotBlank()) return null
        oldMessage = message

        val contentIntent = Intent(Amber.instance, MainActivity::class.java)
        contentIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentPendingIntent =
            PendingIntent.getActivity(
                Amber.instance,
                0,
                contentIntent,
                PendingIntent.FLAG_MUTABLE,
            )

        val reconnectIntent = Intent(Amber.instance, ReconnectReceiver::class.java)
        val reconnectPendingIntent = PendingIntent.getBroadcast(
            Amber.instance,
            0,
            reconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        val notificationBuilder =
            NotificationCompat.Builder(Amber.instance, channelId)
                .setGroup(group.id)
                .setContentTitle(Amber.instance.getString(R.string.of_connected_relays, connected, available))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(message),
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(contentPendingIntent)
                .addAction(R.drawable.ic_notification, Amber.instance.getString(R.string.reconnect), reconnectPendingIntent)

        return notificationBuilder.build()
    }

    fun updateNotification() {
        val notificationManager = NotificationManagerCompat.from(Amber.instance)
        if (ActivityCompat.checkSelfPermission(Amber.instance, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            createNotification()?.let {
                Log.d(Amber.TAG, "updateNotification")
                notificationManager.notify(1, it)
            }
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
