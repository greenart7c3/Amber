package com.greenart7c3.nostrsigner.relays

import android.Manifest
import android.annotation.SuppressLint
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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class AmberRelayStats(
    client: NostrClient,
    val appContext: Context,
) {
    var oldMessage = ""
    var available = emptySet<NormalizedRelayUrl>()
    var connected = emptySet<NormalizedRelayUrl>()

    @OptIn(FlowPreview::class)
    @SuppressLint("MissingPermission")
    val relayStatus = combine(client.availableRelaysFlow(), client.connectedRelaysFlow()) { available, connected ->
        available to connected
    }.debounce(300).onEach {
        this.available = it.first
        this.connected = it.second
        val notificationManager = NotificationManagerCompat.from(appContext)
        if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            createNotification(
                client.connectedRelaysFlow().value,
                client.availableRelaysFlow().value,
                false,
            )?.let { notification ->
                Log.d(Amber.TAG, "updateNotification")
                notificationManager.notify(2, notification)
            }
        }
    }

    lateinit var group: NotificationChannelGroupCompat
    lateinit var channel: NotificationChannelCompat
    lateinit var statusGroup: NotificationChannelGroupCompat
    lateinit var statusChannel: NotificationChannelCompat

    fun createNotificationChannel() {
        val channelId = "ServiceChannel"
        val statusId = "StatusChannel"
        group = NotificationChannelGroupCompat.Builder("ServiceGroup")
            .setName(appContext.getString(R.string.service))
            .setDescription(appContext.getString(R.string.service_description))
            .build()
        channel = NotificationChannelCompat.Builder(channelId, NotificationManager.IMPORTANCE_LOW)
            .setName(appContext.getString(R.string.service))
            .setDescription(appContext.getString(R.string.amber_is_running_in_background))
            .setSound(null, null)
            .setGroup(group.id)
            .build()

        statusGroup = NotificationChannelGroupCompat.Builder("StatusGroup")
            .setName(appContext.getString(R.string.status))
            .setDescription(appContext.getString(R.string.status_service_description))
            .build()
        statusChannel = NotificationChannelCompat.Builder(statusId, NotificationManager.IMPORTANCE_LOW)
            .setName(appContext.getString(R.string.status))
            .setDescription(appContext.getString(R.string.status_service_description))
            .setSound(null, null)
            .setGroup(statusGroup.id)
            .build()

        val notificationManager = NotificationManagerCompat.from(appContext)
        notificationManager.createNotificationChannelGroup(group)
        notificationManager.createNotificationChannel(channel)
        notificationManager.createNotificationChannelGroup(statusGroup)
        notificationManager.createNotificationChannel(statusChannel)

        Amber.instance.applicationIOScope.launch {
            Amber.instance.client.availableRelaysFlow().debounce(300).collect {
                available = it
                updateNotification()
            }
        }
        Amber.instance.applicationIOScope.launch {
            Amber.instance.client.connectedRelaysFlow().debounce(300).collect {
                connected = it
                updateNotification()
            }
        }
    }

    private val innerCache = mutableMapOf<NormalizedRelayUrl, AmberRelayStat>()

    fun createForegroundNotification(): Notification {
        val contentIntent = Intent(appContext, MainActivity::class.java)
        contentIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentPendingIntent =
            PendingIntent.getActivity(
                appContext,
                0,
                contentIntent,
                PendingIntent.FLAG_MUTABLE,
            )

        return NotificationCompat.Builder(appContext, channel.id)
            .setContentTitle(appContext.getString(R.string.service))
            .setContentText(appContext.getString(R.string.amber_is_running_in_background))
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setGroup(group.id)
            .setGroupSummary(true)
            .setContentIntent(contentPendingIntent)
            .build()
    }

    fun createNotification(
        connected: Set<NormalizedRelayUrl>,
        available: Set<NormalizedRelayUrl>,
        forceCreate: Boolean = false,
    ): Notification? {
        val message = available.joinToString("\n") { relay ->
            val stats = Amber.instance.relayStats.get(relay)
            val session = get(relay)

            val status = if (relay in connected) "✓" else "✗"
            val ping = "${stats.pingInMs}ms"

            // Build a comma-separated list of stats to save vertical space
            val details = mutableListOf<String>()
            if (session.sent > 0) details.add("S:${session.sent}")
            if (session.received > 0) details.add("R:${session.received}")
            if (session.failed > 0) details.add("F:${session.failed}")
            if (stats.errorCounter > 0) details.add("E:${stats.errorCounter}")

            val detailsStr = if (details.isNotEmpty()) " | ${details.joinToString(" ")}" else ""

            // Result: wss://relay.url ✓ 474ms | S:1 R:2
            "${relay.displayUrl()} $status $ping$detailsStr"
        }.trim()
        if (message == oldMessage && oldMessage.isNotBlank() && !forceCreate) {
            return null
        }
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
            NotificationCompat.Builder(appContext, statusChannel.id)
                .setGroup(statusGroup.id)
                .setContentTitle(appContext.getString(R.string.of_connected_relays, connected.size, available.size))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(message.trim())
                        .setBigContentTitle(appContext.getString(R.string.of_connected_relays, connected.size, available.size))
                        .setSummaryText(appContext.getString(R.string.status_detail)),
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(contentPendingIntent)
                .addAction(R.drawable.ic_notification, appContext.getString(R.string.reconnect), reconnectPendingIntent)
                .addAction(R.drawable.ic_notification, appContext.getString(if (Amber.instance.settings.killSwitch.value) R.string.disable_kill_switch else R.string.enable_kill_switch), killSwitchPendingIntent)
                .setCategory(Notification.CATEGORY_STATUS)

        return notificationBuilder.build()
    }

    fun updateNotification() {
        val notificationManager = NotificationManagerCompat.from(appContext)
        if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            createNotification(
                connected,
                available,
            )?.let {
                Log.d(Amber.TAG, "updateNotification")
                notificationManager.notify(2, it)
            }
        }
    }

    fun get(url: NormalizedRelayUrl): AmberRelayStat = innerCache.getOrPut(url) { AmberRelayStat() }

    fun addSent(url: NormalizedRelayUrl) {
        get(url).addSent()
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
