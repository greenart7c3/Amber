package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.UriLaunch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UriLaunchTest {
    @Test
    fun extractsNostrConnectUriFromArgs() {
        val uri = "nostrconnect://abc?relay=wss%3A%2F%2Frelay.example.com"
        assertEquals(uri, UriLaunch.extract(arrayOf(uri)))
        assertEquals(uri, UriLaunch.extract(arrayOf("--some-flag", uri)))
        assertEquals(uri, UriLaunch.extract(arrayOf("bunker://other", uri)))
        assertNull(UriLaunch.extract(arrayOf("bunker://only")))
        assertNull(UriLaunch.extract(emptyArray()))
    }

    @Test
    fun desktopEntryDeclaresTheSchemeHandler() {
        val entry = UriLaunch.desktopEntry("/opt/Amber/bin/Amber")
        assertTrue(entry.startsWith("[Desktop Entry]"))
        assertTrue(entry.contains("Exec=/opt/Amber/bin/Amber %u"))
        assertTrue(entry.contains("MimeType=x-scheme-handler/nostrconnect;"))
        assertTrue(entry.contains("NoDisplay=true"))
    }

    @Test
    fun desktopEntryQuotesPathsSafely() {
        // Paths without separators stay bare; spaces and quotes get the
        // desktop-entry double-quote treatment.
        assertEquals(
            "Exec=\"/opt/My Apps/Amber\" %u",
            UriLaunch.desktopEntry("/opt/My Apps/Amber").lineSequence().first { it.startsWith("Exec=") },
        )
        assertEquals(
            "Exec=\"/opt/a\\\"b/Amber\" %u",
            UriLaunch.desktopEntry("/opt/a\"b/Amber").lineSequence().first { it.startsWith("Exec=") },
        )
    }
}
