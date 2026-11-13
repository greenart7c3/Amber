package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.Notifier
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recognizing a quickshell notification daemon that draws action buttons
 * (Omarchy's shell), so request notifications there carry native Approve /
 * Reject instead of Amber's popup.
 */
class NotifierDaemonTest {
    private val home = "/home/u"

    @Test
    fun parsesThePidReply() {
        assertEquals(2258L, Notifier.parsePidReply("(uint32 2258,)\n"))
        assertNull(Notifier.parsePidReply("Error: no such name"))
    }

    @Test
    fun picksTheConfigDirFromTheCommandLine() {
        assertEquals(File("/usr/share/omarchy/shell"), Notifier.quickshellConfigDir(listOf("quickshell", "-n", "-p", "/usr/share/omarchy/shell"), null, home))
        assertEquals(File("/opt/shell"), Notifier.quickshellConfigDir(listOf("quickshell", "--path=/opt/shell"), null, home))
        assertEquals(File("/opt/shell"), Notifier.quickshellConfigDir(listOf("qs", "--path", "/opt/shell"), null, home))
        assertEquals(File("/home/u/.config/quickshell/caelestia"), Notifier.quickshellConfigDir(listOf("qs", "-c", "caelestia"), null, home))
        assertEquals(File("/x/quickshell/noctalia"), Notifier.quickshellConfigDir(listOf("qs", "--config=noctalia"), "/x", home))
        assertEquals(File("/home/u/.config/quickshell"), Notifier.quickshellConfigDir(listOf("quickshell"), "", home))
    }

    @Test
    fun dismissesAnOmarchyCardByTitleAsOneArgument() {
        val dir = File("/usr/share/omarchy/shell")
        assertEquals(
            listOf(
                listOf("omarchy-shell", "-q", "notifications", "dismiss", "Nostr; rm -rf \$HOME"),
                listOf("qs", "ipc", "-p", "/usr/share/omarchy/shell", "call", "notifications", "dismiss", "Nostr; rm -rf \$HOME"),
            ),
            Notifier.omarchyDismissCommands(dir, "Nostr; rm -rf \$HOME"),
        )
    }

    @Test
    fun leavesTitlesThatWouldMatchTooMuchOrLookLikeAnOption() {
        val dir = File("/usr/share/omarchy/shell")
        // The shell matches by substring: "ab" is in most summaries.
        assertTrue(Notifier.omarchyDismissCommands(dir, "ab").isEmpty())
        assertTrue(Notifier.omarchyDismissCommands(dir, "").isEmpty())
        assertTrue(Notifier.omarchyDismissCommands(dir, "--pid").isEmpty())
        assertEquals(2, Notifier.omarchyDismissCommands(dir, "abc").size)
    }

    @Test
    fun recognizesOmarchyButtonsByItsComponent() {
        val dir = Files.createTempDirectory("qs").toFile()
        try {
            assertFalse(Notifier.drawsOmarchyButtons(dir))
            File(dir, "plugins/notifications").mkdirs()
            File(dir, "plugins/notifications/DeedButton.qml").writeText("Item {}")
            assertTrue(Notifier.drawsOmarchyButtons(dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
