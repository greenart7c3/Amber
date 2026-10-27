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
        assertEquals(MIN_WINDOW_SIZE, fitWindowSize(DEFAULT_WINDOW_SIZE, 320, 400))
        assertEquals(MIN_WINDOW_SIZE, fitWindowSize(DpSize(100.dp, 100.dp), 1920, 1040))
    }

    @Test
    fun smallScreenGetsSmallWindow() {
        assertEquals(DpSize(608.dp, 568.dp), fitWindowSize(DEFAULT_WINDOW_SIZE, 640, 600))
    }

    @Test
    fun layoutWidthBreakpoints() {
        assertEquals(LayoutWidth.COMPACT, LayoutWidth.of(MIN_WINDOW_SIZE.width))
        assertEquals(LayoutWidth.COMPACT, LayoutWidth.of(599.dp))
        assertEquals(LayoutWidth.MEDIUM, LayoutWidth.of(600.dp))
        assertEquals(LayoutWidth.MEDIUM, LayoutWidth.of(959.dp))
        assertEquals(LayoutWidth.EXPANDED, LayoutWidth.of(960.dp))
        assertEquals(LayoutWidth.EXPANDED, LayoutWidth.of(DEFAULT_WINDOW_SIZE.width))
    }

    @Test
    fun unknownScreenKeepsDesiredSize() {
        assertEquals(DpSize(900.dp, 600.dp), fitWindowSize(DpSize(900.dp, 600.dp), null, null))
    }
}
