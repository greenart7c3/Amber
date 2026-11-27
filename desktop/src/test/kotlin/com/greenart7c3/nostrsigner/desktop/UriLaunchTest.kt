package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.DesktopEntryEscaping
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
        assertEquals(
            "a path with a space gets the desktop-entry double quotes",
            "Exec=\"/opt/My Apps/Amber\" %u",
            UriLaunch.desktopEntry("/opt/My Apps/Amber").lineSequence().first { it.startsWith("Exec=") },
        )
        assertEquals(
            "a quote is escaped for the quoting and that backslash again at the string level, as the Desktop Entry spec reads it back",
            "Exec=\"/opt/a\\\\\"b/Amber\" %u",
            UriLaunch.desktopEntry("/opt/a\"b/Amber").lineSequence().first { it.startsWith("Exec=") },
        )
    }

    @Test
    fun desktopEntryEscapesFieldCodesAndShellCharacters() {
        assertEquals("a literal % would be read as a field code", "/opt/100%%/Amber", DesktopEntryEscaping.quoteForDesktopEntry("/opt/100%/Amber"))
        val quotedShellCharacter = "a shell character is quoted and preceded by two backslashes, for the quoting and the string level"
        assertEquals(quotedShellCharacter, $$"\"/opt/\\\\$HOME/Amber\"", DesktopEntryEscaping.quoteForDesktopEntry($$"/opt/$HOME/Amber"))
        assertEquals(quotedShellCharacter, "\"/opt/\\\\`id\\\\`/Amber\"", DesktopEntryEscaping.quoteForDesktopEntry("/opt/`id`/Amber"))
        assertEquals("a literal backslash needs four in the file", "\"C:\\\\\\\\Amber\"", DesktopEntryEscaping.quoteForDesktopEntry("C:\\Amber"))
    }

    @Test
    fun aLineBreakCannotEnterADesktopEntry() {
        assertThrows(IllegalArgumentException::class.java) { DesktopEntryEscaping.quoteForDesktopEntry("/opt/Amber\nMimeType=evil") }
        assertThrows(IllegalArgumentException::class.java) { DesktopEntryEscaping.escapeDesktopEntryString("/icon.png\rX=1") }
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
        ).forEach { assertEquals(it, DesktopEntryEscaping.unquoteDesktopEntryArg(DesktopEntryEscaping.quoteForDesktopEntry(it))) }
    }
}
