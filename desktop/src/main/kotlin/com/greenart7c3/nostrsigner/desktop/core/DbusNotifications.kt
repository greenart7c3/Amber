package com.greenart7c3.nostrsigner.desktop.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

/**
 * The freedesktop.org notification daemon's D-Bus interface (the parts Amber
 * uses). [JvmSuppressWildcards]: dbus-java derives the wire signature from
 * the Java generic types and cannot map Kotlin's `? extends` wildcards.
 */
@DBusInterfaceName("org.freedesktop.Notifications")
@JvmSuppressWildcards
interface FreedesktopNotifications : DBusInterface {
    @DBusMemberName("Notify")
    fun postNotification(
        appName: String,
        replacesId: UInt32,
        appIcon: String,
        summary: String,
        body: String,
        actions: List<String>,
        hints: Map<String, Variant<*>>,
        expireTimeout: Int,
    ): UInt32

    @DBusMemberName("CloseNotification")
    fun closeNotification(id: UInt32)

    @DBusMemberName("ActionInvoked")
    class ActionInvoked(path: String, val id: UInt32, val actionKey: String) : DBusSignal(path, id, actionKey)

    @DBusMemberName("NotificationClosed")
    class NotificationClosed(path: String, val id: UInt32, val reason: UInt32) : DBusSignal(path, id, reason)
}

/**
 * Actionable notifications over the session bus. The daemon delivers
 * `ActionInvoked` only to the connection that posted the notification (GNOME
 * does), so posting and listening must share one connection — which neither
 * `gdbus call` + `gdbus monitor` nor notify-send can do reliably (notify-send
 * 0.8.8 wrongly reports that GNOME 50 has no actions and drops the buttons).
 *
 * One connection is opened lazily and kept for the app's lifetime; handlers
 * are kept per notification id until an action fires, the notification
 * closes, or [WATCH_MS] passes (GNOME keeps the buttons usable from its
 * notification list after the banner hides).
 */
internal object DbusNotifications {
    private const val BUS_NAME = "org.freedesktop.Notifications"
    private const val OBJECT_PATH = "/org/freedesktop/Notifications"
    private const val WATCH_MS = 10 * 60 * 1000L

    /** Test hook: connect to this bus address instead of the session bus. */
    @Volatile
    internal var busAddress: String? = null

    private val handlers = ConcurrentHashMap<Long, (String) -> Unit>()
    private val expiry = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "amber-notify-expiry").apply { isDaemon = true }
    }

    @Volatile
    private var connection: DBusConnection? = null

    @Synchronized
    private fun connection(): DBusConnection = connection?.takeIf { it.isConnected } ?: run {
        val address = busAddress
        val conn = if (address != null) DBusConnectionBuilder.forAddress(address).build() else DBusConnectionBuilder.forSessionBus().build()
        conn.addSigHandler(FreedesktopNotifications.ActionInvoked::class.java) { signal ->
            handlers.remove(signal.id.toLong())?.invoke(signal.actionKey)
        }
        conn.addSigHandler(FreedesktopNotifications.NotificationClosed::class.java) { signal ->
            handlers.remove(signal.id.toLong())
        }
        connection = conn
        conn
    }

    /**
     * Post a notification with [actions] as (key, label) pairs and run
     * [onAction] with the key the user picks ("default" for a body click).
     * Throws when there is no session bus or notification daemon.
     */
    fun show(appName: String, title: String, message: String, actions: List<Pair<String, String>>, timeoutMs: Int, onAction: (String) -> Unit) {
        val daemon = connection().getRemoteObject(BUS_NAME, OBJECT_PATH, FreedesktopNotifications::class.java)
        val id = daemon.postNotification(
            appName,
            UInt32(0),
            "",
            title,
            message,
            actions.flatMap { listOf(it.first, it.second) },
            emptyMap(),
            timeoutMs,
        ).toLong()
        handlers[id] = onAction
        expiry.schedule({ handlers.remove(id) }, WATCH_MS, TimeUnit.MILLISECONDS)
    }
}
