package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.DbusNotifications
import com.greenart7c3.nostrsigner.desktop.core.FreedesktopNotifications
import com.greenart7c3.nostrsigner.desktop.core.Notifier
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Approve/Reject buttons on a Linux request notification: notify() posts the
 * actions over D-Bus and runs the one the daemon reports in ActionInvoked.
 * Runs against a private dbus-daemon with a fake notification daemon, so
 * nothing reaches the real desktop; skipped where dbus-daemon is missing.
 */
class NotifierButtonsTest {
    private var bus: Process? = null
    private var daemonConnection: DBusConnection? = null

    private class FakeDaemon : FreedesktopNotifications {
        @Volatile var actions: List<String> = emptyList()

        // Read after [closeReceived] fires, which publishes the write.
        var closed = -1L

        val posted = CountDownLatch(1)
        val closeReceived = CountDownLatch(1)

        override fun postNotification(
            appName: String,
            replacesId: UInt32,
            appIcon: String,
            summary: String,
            body: String,
            actions: List<String>,
            hints: Map<String, Variant<*>>,
            expireTimeout: Int,
        ): UInt32 {
            this.actions = actions
            posted.countDown()
            return UInt32(7)
        }

        override fun closeNotification(id: UInt32) {
            closed = id.toLong()
            closeReceived.countDown()
        }

        override fun getObjectPath() = "/org/freedesktop/Notifications"
    }

    @Before
    fun startBus() {
        assumeTrue(System.getProperty("os.name").lowercase().contains("linux"))
        val process = runCatching {
            ProcessBuilder("dbus-daemon", "--session", "--nofork", "--print-address").start()
        }.getOrNull()
        assumeTrue("dbus-daemon not installed", process != null)
        bus = process
        val address = process!!.inputStream.bufferedReader().readLine()
        assumeTrue("dbus-daemon did not start", !address.isNullOrBlank())
        DbusNotifications.busAddress = address
    }

    @After
    fun stopBus() {
        DbusNotifications.busAddress = null
        daemonConnection?.close()
        bus?.destroy()
    }

    @Test
    fun clickingAButtonRunsItsAction() {
        val daemon = FakeDaemon()
        val conn = DBusConnectionBuilder.forAddress(DbusNotifications.busAddress!!).build()
        daemonConnection = conn
        conn.requestBusName("org.freedesktop.Notifications")
        conn.exportObject(daemon.objectPath, daemon)

        val invoked = mutableListOf<String>()
        val done = CountDownLatch(1)
        val posted = Notifier.notify(
            "Amber",
            "TestApp wants you to sign a Short text note",
            listOf(
                Notifier.Action("approve", "Approve") { invoked += "approve" },
                Notifier.Action("reject", "Reject") {
                    invoked += "reject"
                    done.countDown()
                },
            ),
        ) { invoked += "default" }
        assertNotNull("notify() should report delivered", posted)
        assertTrue("posted to the daemon", daemon.posted.await(3, TimeUnit.SECONDS))
        assertEquals(listOf("default", "Open", "approve", "Approve", "reject", "Reject"), daemon.actions)

        conn.sendMessage(FreedesktopNotifications.ActionInvoked(daemon.objectPath, UInt32(7), "reject"))
        assertTrue("action runs on click", done.await(3, TimeUnit.SECONDS))
        assertEquals(listOf("reject"), invoked)
    }

    /**
     * Answering a request elsewhere (its card, the tray window) must take its
     * notification down, not leave Approve/Reject buttons behind on a request
     * that is already gone.
     */
    @Test
    fun closingTheNotificationWithdrawsIt() {
        val daemon = FakeDaemon()
        val conn = DBusConnectionBuilder.forAddress(DbusNotifications.busAddress!!).build()
        daemonConnection = conn
        conn.requestBusName("org.freedesktop.Notifications")
        conn.exportObject(daemon.objectPath, daemon)

        val posted = Notifier.notify(
            "Amber",
            "TestApp wants you to sign a Short text note",
            listOf(Notifier.Action("reject", "Reject") {}),
        )
        assertNotNull("notify() should report delivered", posted)
        assertTrue("posted to the daemon", daemon.posted.await(3, TimeUnit.SECONDS))

        posted!!.close()
        assertTrue("close() asks the daemon to remove it", daemon.closeReceived.await(3, TimeUnit.SECONDS))
        assertEquals(7L, daemon.closed)
    }
}
