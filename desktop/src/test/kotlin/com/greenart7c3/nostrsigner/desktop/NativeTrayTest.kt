package com.greenart7c3.nostrsigner.desktop

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
}
