package com.greenart7c3.nostrsigner.relays

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
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
import com.greenart7c3.nostrsigner.service.KillSwitchReceiver
import com.greenart7c3.nostrsigner.service.ReconnectReceiver
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

class AmberRelayStats(
    client: NostrClient,
    scope: CoroutineScope,
    val appContext: Context,
) {
    var oldMessage = ""
    val relayStatus = client.relayStatusFlow()

    @OptIn(FlowPreview::class)
    val relayStatusUpdater = client.relayStatusFlow().debounce(300).onEach {
        try {
            updateNotification()
        } catch (_: NullPointerException) {
        }
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        relayStatus.value,
    )

    private val innerCache = mutableMapOf<NormalizedRelayUrl, AmberRelayStat>()

    fun createNotification(forceCreate: Boolean = false) = createNotification(
        relayStatus.value.connected,
        relayStatus.value.available,
        forceCreate,
    )

    fun createNotification(
        connected: Set<NormalizedRelayUrl>,
        available: Set<NormalizedRelayUrl>,
        forceCreate: Boolean = false,
    ): Notification? {
        val channelId = "ServiceChannel"
        val group = NotificationChannelGroupCompat.Builder("ServiceGroup")
            .setName(appContext.getString(R.string.service))
            .setDescription(appContext.getString(R.string.service_description))
            .build()
        val channel = NotificationChannelCompat.Builder(channelId, NotificationManager.IMPORTANCE_DEFAULT)
            .setName(appContext.getString(R.string.service))
            .setDescription(appContext.getString(R.string.amber_is_running_in_background))
            .setSound(null, null)
            .setGroup(group.id)
            .build()

        val notificationManager = NotificationManagerCompat.from(appContext)
        notificationManager.createNotificationChannelGroup(group)
        notificationManager.createNotificationChannel(channel)

        val message = available.joinToString("\n") {
            val connected = "${it.url} ${if (it in connected) appContext.getString(R.string.connected) else appContext.getString(R.string.disconnected)}\n"
            val ping = appContext.getString(R.string.ping_ms, RelayStats.get(it).pingInMs)
            val sent = if (get(it).sent > 0) {
                appContext.getString(R.string.sent, get(it).sent)
            } else {
                ""
            }
            val received = if (get(it).received > 0) {
                appContext.getString(R.string.received, get(it).received)
            } else {
                ""
            }
            val failed = if (get(it).failed > 0) {
                appContext.getString(R.string.rejected_by_relay, get(it).failed)
            } else {
                ""
            }
            val error = if (RelayStats.get(it).errorCounter > 0) appContext.getString(R.string.error, RelayStats.get(it).errorCounter) else ""

            connected + ping + sent + received + failed + error
        }
        if (message == oldMessage && oldMessage.isNotBlank() && !forceCreate) return null
        this.oldMessage = message

        val contentIntent = Intent(appContext, MainActivity::class.java)
        contentIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentPendingIntent =
            PendingIntent.getActivity(
                appContext,
                0,
                contentIntent,
                PendingIntent.FLAG_MUTABLE,
            )

        val reconnectIntent = Intent(appContext, ReconnectReceiver::class.java)
        val reconnectPendingIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            reconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        val killSwitchIntent = Intent(appContext, KillSwitchReceiver::class.java)
        val killSwitchPendingIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            killSwitchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        val notificationBuilder =
            NotificationCompat.Builder(appContext, channelId)
                .setGroup(group.id)
                .setContentTitle(appContext.getString(R.string.of_connected_relays, connected.size, available.size))
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(contentPendingIntent)
                .addAction(R.drawable.ic_notification, appContext.getString(R.string.reconnect), reconnectPendingIntent)
                .addAction(R.drawable.ic_notification, appContext.getString(if (Amber.instance.settings.killSwitch) R.string.disable_kill_switch else R.string.enable_kill_switch), killSwitchPendingIntent)

        return notificationBuilder.build()
    }

    fun updateNotification() {
        val notificationManager = NotificationManagerCompat.from(appContext)
        if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            createNotification()?.let {
                Log.d(Amber.TAG, "updateNotification")
                notificationManager.notify(1, it)
            }
        }
    }

    fun get(url: NormalizedRelayUrl): AmberRelayStat = innerCache.getOrPut(url) { AmberRelayStat() }

    fun addSent(url: NormalizedRelayUrl) {
        get(url).addSent()
        updateNotification()
    }

    fun addReceived(url: NormalizedRelayUrl) {
        get(url).addReceived()
        updateNotification()
    }

    fun addFailed(url: NormalizedRelayUrl) {
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
