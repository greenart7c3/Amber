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
    fun `create does not throw whether or not a tray host exists`() {
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
        tray?.shutdown()
    }

    @Test
    fun `a gdbus GVariant true reply means a tray host is registered`() {
        assertTrue(NativeTray.parseSniHostReply("(<true>,)"))
        assertTrue(NativeTray.parseSniHostReply("(<TRUE>,)"))
    }

    @Test
    fun `a gdbus error or false reply is not mistaken for a tray host`() {
        assertFalse(NativeTray.parseSniHostReply("(<false>,)"))
        assertFalse(
            NativeTray.parseSniHostReply(
                "Error: GDBus.Error:... The name org.kde.StatusNotifierWatcher was not provided by any .service files",
            ),
        )
        assertFalse(NativeTray.parseSniHostReply(""))
    }

    @Test
    fun `awaitSniHost keeps probing until a bar that starts after Amber registers its host`() {
        val replies = ArrayDeque(listOf(false, false, true))
        assertTrue(NativeTray.awaitSniHost(attempts = 5, intervalMs = 0) { replies.removeFirst() })
        assertTrue("stops probing once a host is there", replies.isEmpty())
    }

    @Test
    fun `awaitSniHost gives up after the last attempt`() {
        var probes = 0
        assertFalse(NativeTray.awaitSniHost(attempts = 3, intervalMs = 0) { probes++ > 99 })
        assertEquals("gives up after the last attempt", 3, probes)
    }

    @Test
    fun `createBounded returns quickly without throwing instead of blocking forever`() {
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
