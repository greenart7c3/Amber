package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.UriLaunch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
        // A quote is escaped for the quoting (\") and that backslash again
        // at the string level (\\"), as the Desktop Entry spec reads it back.
        assertEquals(
            "Exec=\"/opt/a\\\\\"b/Amber\" %u",
            UriLaunch.desktopEntry("/opt/a\"b/Amber").lineSequence().first { it.startsWith("Exec=") },
        )
    }

    @Test
    fun desktopEntryEscapesFieldCodesAndShellCharacters() {
        // A literal % would be read as a field code; $ and ` are escaped in quotes.
        assertEquals("/opt/100%%/Amber", UriLaunch.quoteForDesktopEntry("/opt/100%/Amber"))
        // In the file: quote, the escaped char preceded by two backslashes (quoting + string level).
        assertEquals($$"\"/opt/\\\\$HOME/Amber\"", UriLaunch.quoteForDesktopEntry($$"/opt/$HOME/Amber"))
        assertEquals("\"/opt/\\\\`id\\\\`/Amber\"", UriLaunch.quoteForDesktopEntry("/opt/`id`/Amber"))
        // A literal backslash needs four in the file.
        assertEquals("\"C:\\\\\\\\Amber\"", UriLaunch.quoteForDesktopEntry("C:\\Amber"))
    }

    @Test
    fun aLineBreakCannotEnterADesktopEntry() {
        assertThrows(IllegalArgumentException::class.java) { UriLaunch.quoteForDesktopEntry("/opt/Amber\nMimeType=evil") }
        assertThrows(IllegalArgumentException::class.java) { UriLaunch.escapeDesktopEntryString("/icon.png\rX=1") }
    }

    @Test
    fun quotingRoundTrips() {
        listOf(
            "/opt/Amber/bin/Amber",
            "/opt/My Apps/Amber",
            "/opt/a\"b/Amber",
            "/opt/100% done/Amber",
            $$"/opt/$HOME/`id`/Amber",
            "/opt/back\\slash/Amber",
            "/opt/it's (new) & <odd> ~ | ; * ? #/Amber",
        ).forEach { assertEquals(it, UriLaunch.unquoteDesktopEntryArg(UriLaunch.quoteForDesktopEntry(it))) }
    }
}
