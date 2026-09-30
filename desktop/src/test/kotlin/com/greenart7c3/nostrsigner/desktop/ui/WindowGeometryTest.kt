package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowGeometryTest {
    @Test
    fun defaultSizeFitsLargeScreenUnchanged() {
        assertEquals(DEFAULT_WINDOW_SIZE, fitWindowSize(DEFAULT_WINDOW_SIZE, 1920, 1040))
    }

    @Test
    fun shrinksToUsableAreaAboveTaskbar() {
        // 1280x800 display with a 48px Windows taskbar: the 780dp default used
        // to hide the window's bottom (and the account switcher) under it.
        assertEquals(DpSize(1100.dp, 720.dp), fitWindowSize(DEFAULT_WINDOW_SIZE, 1280, 752))
    }

    @Test
    fun neverGoesBelowMinimumUsableSize() {
        assertEquals(DpSize(720.dp, 480.dp), fitWindowSize(DEFAULT_WINDOW_SIZE, 640, 400))
        assertEquals(DpSize(720.dp, 480.dp), fitWindowSize(DpSize(100.dp, 100.dp), 1920, 1040))
    }

    @Test
    fun unknownScreenKeepsDesiredSize() {
        assertEquals(DpSize(900.dp, 600.dp), fitWindowSize(DpSize(900.dp, 600.dp), null, null))
    }
}
