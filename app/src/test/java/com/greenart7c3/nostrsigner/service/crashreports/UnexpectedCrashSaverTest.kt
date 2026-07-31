package com.greenart7c3.nostrsigner.service.crashreports

import java.util.concurrent.TimeoutException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnexpectedCrashSaverTest {
    private fun finalizerWatchdogCrash(): TimeoutException {
        val e = TimeoutException("com.android.internal.os.BinderInternal\$GcWatcher.finalize() timed out after 10 seconds")
        e.stackTrace =
            arrayOf(
                StackTraceElement("com.android.internal.os.BinderInternal\$GcWatcher", "finalize", "BinderInternal.java", 64),
                StackTraceElement("java.lang.Daemons\$FinalizerDaemon", "doFinalize", "Daemons.java", 389),
                StackTraceElement("java.lang.Daemons\$FinalizerDaemon", "processReference", "Daemons.java", 369),
                StackTraceElement("java.lang.Daemons\$FinalizerDaemon", "runInternal", "Daemons.java", 354),
                StackTraceElement("java.lang.Daemons\$Daemon", "run", "Daemons.java", 135),
                StackTraceElement("java.lang.Thread", "run", "Thread.java", 1564),
            )
        return e
    }

    @Test
    fun `binder gc watcher finalizer timeout is junk`() {
        assertTrue(UnexpectedCrashSaver.isJunkReport(finalizerWatchdogCrash()))
    }

    @Test
    fun `finalizer daemon stack is junk even without the message`() {
        val e = TimeoutException(null as String?)
        e.stackTrace =
            arrayOf(
                StackTraceElement("android.os.BinderProxy", "finalize", "BinderProxy.java", 100),
                StackTraceElement("java.lang.Daemons\$FinalizerDaemon", "doFinalize", "Daemons.java", 389),
            )
        assertTrue(UnexpectedCrashSaver.isJunkReport(e))
    }

    @Test
    fun `out of memory is junk`() {
        assertTrue(UnexpectedCrashSaver.isJunkReport(OutOfMemoryError()))
    }

    @Test
    fun `app timeout exception is not junk`() {
        assertFalse(UnexpectedCrashSaver.isJunkReport(TimeoutException("socket timed out")))
    }

    @Test
    fun `regular crash is not junk`() {
        assertFalse(UnexpectedCrashSaver.isJunkReport(IllegalStateException("boom")))
    }
}
