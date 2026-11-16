package com.greenart7c3.nostrsigner.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tray builder must never throw and must degrade to null when no tray
 * backend exists (as in a headless CI environment) — the app relies on that
 * to fall back to background/quit behavior.
 */
class NativeTrayTest {
    @Test
    fun createNeverThrowsAndReturnsNullWhenNoBackend() {
        val tray = NativeTray.create(
            iconStream = { null },
            tooltip = "Amber",
            openLabel = "Open",
            lockLabel = "Lock",
            quitLabel = "Quit",
            onToggle = {},
            onLock = {},
            onQuit = {},
        )
        // Either null (headless / no tray host) or a real tray; both are fine —
        // the contract under test is "does not throw". Clean up if created.
        tray?.shutdown()
    }

    @Test
    fun sniHostReplyParsing() {
        // gdbus returns GVariant text like "(<true>,)" when a tray host
        // (waybar's tray module, GNOME Shell, …) is registered.
        assertTrue(NativeTray.parseSniHostReply("(<true>,)"))
        assertTrue(NativeTray.parseSniHostReply("(<TRUE>,)"))
        // Errors ("The name … was not provided by any .service files") and
        // an unregistered host must not be mistaken for a host.
        assertFalse(NativeTray.parseSniHostReply("(<false>,)"))
        assertFalse(
            NativeTray.parseSniHostReply(
                "Error: GDBus.Error:... The name org.kde.StatusNotifierWatcher was not provided by any .service files",
            ),
        )
        assertFalse(NativeTray.parseSniHostReply(""))
    }

    @Test
    fun awaitSniHostKeepsProbingUntilTheBarRegistersOne() {
        // At login the bar's tray host can come up after Amber.
        val replies = ArrayDeque(listOf(false, false, true))
        assertTrue(NativeTray.awaitSniHost(attempts = 5, intervalMs = 0) { replies.removeFirst() })
        assertTrue("stops probing once a host is there", replies.isEmpty())

        var probes = 0
        assertFalse(NativeTray.awaitSniHost(attempts = 3, intervalMs = 0) { probes++ > 99 })
        assertEquals("gives up after the last attempt", 3, probes)
    }

    @Test
    fun createBoundedGivesUpInsteadOfBlockingForever() {
        // No SNI host exists under Gradle test workers, so the probe path
        // returns null immediately — the contract under test is "returns at
        // all, quickly, without throwing" (the pre-fix hang lived in create()).
        val start = System.nanoTime()
        val tray = NativeTray.createBounded(
            timeoutMs = 1_000,
            iconStream = { null },
            tooltip = "Amber",
            openLabel = "Open",
            lockLabel = "Lock",
            quitLabel = "Quit",
            onToggle = {},
            onLock = {},
            onQuit = {},
        )
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        tray?.shutdown()
        assertTrue("createBounded must return fast, took ${elapsedMs}ms", elapsedMs < 5_000)
    }
}
