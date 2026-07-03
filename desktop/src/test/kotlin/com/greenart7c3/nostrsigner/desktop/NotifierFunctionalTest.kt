package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.Notifier
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * End-to-end check that on Linux [Notifier.notify] shells out to the
 * freedesktop `notify-send`. Enabled only when the harness has put a
 * recording stub named `notify-send` first on PATH and set AMBER_NOTIFY_OUT.
 */
class NotifierFunctionalTest {
    @Test
    fun linuxNotifyInvokesNotifySendWithSummaryAndBody() {
        val out = System.getenv("AMBER_NOTIFY_OUT")
        assumeTrue("stub not configured", out != null)
        assumeTrue(System.getProperty("os.name").lowercase().contains("linux"))

        val ok = Notifier.notify("Amber", "TestApp wants you to sign a Short text note")
        assertTrue("notify() should report delivered", ok)

        val args = File(out!!).readLines()
        assertTrue("app name flag", args.contains("Amber"))
        assertTrue("summary present", args.contains("Amber"))
        assertTrue("body present", args.any { it.contains("Short text note") })
    }
}
