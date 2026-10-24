package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.models.BunkerClientMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A client chooses its own name (nostrconnect `name`, connect metadata, the
 * NIP-55 `appName`); it must not fake extra lines or reversed text on an
 * approval sheet or notification.
 */
class AppNameSanitizerTest {
    @Test
    fun `a client-chosen name cannot fake lines or direction`() {
        // Line breaks and tabs become single spaces: no fake second line.
        assertEquals("Wallet Amber: approve to continue", sanitizeAppName("Wallet\nAmber:\tapprove  to continue"))
        // Bidi overrides and zero-width characters are dropped.
        assertEquals("evilgpj.exe", sanitizeAppName("evil‮gpj.exe​"))
        assertEquals("ab", sanitizeAppName("a\u0000\u0007b"))
        assertEquals("a b", sanitizeAppName("a b"))
        // Ordinary names, including non-Latin ones, are kept as they are.
        assertEquals("Amethyst", sanitizeAppName("  Amethyst  "))
        assertEquals("ノストラ 🟣", sanitizeAppName("ノストラ 🟣"))
    }

    @Test
    fun `a long name is cut`() {
        val cut = sanitizeAppName("x".repeat(200))
        assertEquals(APP_NAME_MAX, cut.codePointCount(0, cut.length))
        assertTrue(cut.endsWith("…"))
        // Cutting never splits a surrogate pair.
        val emoji = sanitizeAppName("🟣".repeat(100))
        assertEquals(APP_NAME_MAX, emoji.codePointCount(0, emoji.length))
    }

    @Test
    fun `connect metadata names are sanitized when parsed`() {
        val metadata = BunkerClientMetadata.parseOrNull("""{"name":"Wallet\n‮Amber"}""")
        assertEquals("Wallet Amber", metadata?.name)
    }
}
