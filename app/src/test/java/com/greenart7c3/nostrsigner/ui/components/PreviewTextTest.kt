package com.greenart7c3.nostrsigner.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewTextTest {
    @Test
    fun `truncateForPreview keeps strings within limit unchanged`() {
        val exact = "a".repeat(MAX_PREVIEW_CHARS)
        assertEquals("short", truncateForPreview("short"))
        assertEquals(exact, truncateForPreview(exact))
    }

    @Test
    fun `truncateForPreview truncates oversized strings with ellipsis`() {
        val oversized = "x".repeat(MAX_PREVIEW_CHARS + 50)
        val preview = truncateForPreview(oversized)

        assertEquals(MAX_PREVIEW_CHARS + 1, preview.length)
        assertTrue(preview.endsWith("…"))
    }
}
