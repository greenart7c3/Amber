package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.awt.GraphicsEnvironment

/** Preferred window size when nothing was saved yet. */
val DEFAULT_WINDOW_SIZE = DpSize(1100.dp, 780.dp)

/** Smallest size the layout (sidebar + content) stays usable at. */
private val MIN_WINDOW_SIZE = DpSize(720.dp, 480.dp)

/** Space kept free around the window so its edges and title bar stay reachable. */
private const val SCREEN_MARGIN_DP = 32

/**
 * Clamps [desired] to the usable screen area ([usableWidth] x [usableHeight],
 * in dp — i.e. AWT logical pixels, which already exclude the taskbar/dock), so
 * the window never opens with its bottom (and the account switcher) hidden
 * under the Windows taskbar on small or scaled displays.
 */
fun fitWindowSize(desired: DpSize, usableWidth: Int?, usableHeight: Int?): DpSize {
    if (usableWidth == null || usableHeight == null) return desired
    val maxWidth = (usableWidth - SCREEN_MARGIN_DP).coerceAtLeast(MIN_WINDOW_SIZE.width.value.toInt())
    val maxHeight = (usableHeight - SCREEN_MARGIN_DP).coerceAtLeast(MIN_WINDOW_SIZE.height.value.toInt())
    return DpSize(
        desired.width.value.toInt().coerceIn(MIN_WINDOW_SIZE.width.value.toInt(), maxWidth).dp,
        desired.height.value.toInt().coerceIn(MIN_WINDOW_SIZE.height.value.toInt(), maxHeight).dp,
    )
}

/** Initial window size: the saved one (or the default), fitted to the primary screen's usable area. */
fun initialWindowSize(savedWidth: Int?, savedHeight: Int?): DpSize {
    val desired = if (savedWidth != null && savedHeight != null) DpSize(savedWidth.dp, savedHeight.dp) else DEFAULT_WINDOW_SIZE
    val usable = runCatching { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }.getOrNull()
    return fitWindowSize(desired, usable?.width, usable?.height)
}
