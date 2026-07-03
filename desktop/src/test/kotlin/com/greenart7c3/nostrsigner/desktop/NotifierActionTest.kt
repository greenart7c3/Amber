package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.Notifier
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Clicking a Linux notification must open Amber: notify() sends an actionable
 * notify-send (--wait --action) and runs the callback when the default action
 * fires. Enabled only when the harness provides a recording+clicking stub.
 */
class NotifierActionTest {
    @Test
    fun clickingTheNotificationRunsTheCallback() {
        val out = System.getenv("AMBER_NOTIFY_OUT")
        assumeTrue("stub not configured", out != null)
        assumeTrue(System.getProperty("os.name").lowercase().contains("linux"))

        val opened = CountDownLatch(1)
        val ok = Notifier.notify("Amber", "TestApp wants you to sign a Short text note") { opened.countDown() }
        assertTrue("notify() should report delivered", ok)

        val args = File(out!!).readLines()
        assertTrue("uses --wait", args.contains("--wait"))
        assertTrue("registers a default action", args.any { it.startsWith("--action=default") })
        assertTrue("callback runs on click", opened.await(3, TimeUnit.SECONDS))
    }
}
