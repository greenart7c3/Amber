package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AppImageIntegration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppImageIntegrationTest {
    @Test
    fun desktopEntryLaunchesTheAppImageAndMatchesTheWindow() {
        val entry = AppImageIntegration.desktopEntry("/home/u/Apps/Amber.AppImage", "/home/u/.local/share/amber/amber.png")
        assertTrue(entry.startsWith("[Desktop Entry]"))
        assertTrue(entry.contains("Exec=/home/u/Apps/Amber.AppImage %u"))
        assertTrue(entry.contains("Icon=/home/u/.local/share/amber/amber.png"))
        assertTrue(entry.contains("StartupWMClass=${AppImageIntegration.WINDOW_CLASS}"))
        // Shown in the app launcher, unlike the nostrconnect:// handler entry.
        assertTrue(!entry.contains("NoDisplay"))
    }

    @Test
    fun execTargetRoundTripsQuotedPaths() {
        listOf(
            "/home/u/Amber.AppImage",
            "/home/u/My Apps/Amber.AppImage",
            "/home/u/a\"b\\c/Amber.AppImage",
        ).forEach { path ->
            assertEquals(path, AppImageIntegration.execTarget(AppImageIntegration.desktopEntry(path, "/icon.png")))
        }
    }

    @Test
    fun execTargetIsNullWithoutAnExecLine() {
        assertNull(AppImageIntegration.execTarget("[Desktop Entry]\nName=Amber\n"))
    }
}
