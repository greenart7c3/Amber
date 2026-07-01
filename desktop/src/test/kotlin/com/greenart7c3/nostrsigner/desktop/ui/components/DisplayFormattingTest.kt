package com.greenart7c3.nostrsigner.desktop.ui.components

import com.greenart7c3.nostrsigner.shared.BunkerMethod
import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayFormattingTest {
    @Test
    fun shortenHexKeepsShortStringsAsIs() {
        assertEquals("abc", "abc".shortenHex())
    }

    @Test
    fun shortenHexTruncatesLongStrings() {
        val hex = "deadbeef00112233445566778899aabbccddeeff"
        assertEquals("deadbeef...ccddeeff", hex.shortenHex())
    }

    @Test
    fun signEventDescriptionIncludesKind() {
        assertEquals("Sign event (kind 1)", bunkerMethodDescription(BunkerMethod.SIGN_EVENT, 1))
        assertEquals("Sign event", bunkerMethodDescription(BunkerMethod.SIGN_EVENT, null))
    }

    @Test
    fun relativeTimeBuckets() {
        val now = 1_000_000L
        assertEquals("just now", relativeTimeFromNow(now - 5, now))
        assertEquals("5m ago", relativeTimeFromNow(now - 300, now))
        assertEquals("2h ago", relativeTimeFromNow(now - 7200, now))
        assertEquals("3d ago", relativeTimeFromNow(now - 259200, now))
    }
}
