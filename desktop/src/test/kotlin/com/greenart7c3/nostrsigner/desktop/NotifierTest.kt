package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.Notifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The external-process plumbing behind desktop notifications. */
class NotifierTest {
    @Test
    fun missingBinaryReportsFailureSoTheNextChannelCanBeTried() {
        assertFalse(Notifier.tryCommand(listOf("amber-no-such-binary-zzz", "hi")))
    }

    @Test
    fun successfulCommandReportsDelivered() {
        // `true` exits 0 on any POSIX system; skip elsewhere (e.g. bare Windows).
        assumeTrue(!System.getProperty("os.name").lowercase().contains("win"))
        assertTrue(Notifier.tryCommand(listOf("true")))
    }

    @Test
    fun failingCommandReportsFailure() {
        assumeTrue(!System.getProperty("os.name").lowercase().contains("win"))
        assertFalse(Notifier.tryCommand(listOf("false")))
    }
}
