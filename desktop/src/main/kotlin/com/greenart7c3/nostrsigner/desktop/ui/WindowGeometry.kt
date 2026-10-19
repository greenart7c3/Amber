package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.awt.GraphicsEnvironment

/** Preferred window size when nothing was saved yet. */
val DEFAULT_WINDOW_SIZE = DpSize(1100.dp, 780.dp)

/**
 * Smallest size the layout stays usable at (the compact layout, with the
 * navigation at the bottom). Also enforced as the window's minimum size.
 */
val MIN_WINDOW_SIZE = DpSize(360.dp, 420.dp)

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

/**
 * Width buckets the UI adapts to, after the Material window size classes:
 * [COMPACT] puts the navigation in a bottom bar, [MEDIUM] collapses the
 * sidebar into an icon rail and [EXPANDED] shows the full sidebar.
 */
enum class LayoutWidth {
    COMPACT,
    MEDIUM,
    EXPANDED,
    ;

    companion object {
        fun of(width: Dp): LayoutWidth = when {
            width < 600.dp -> COMPACT
            width < 960.dp -> MEDIUM
            else -> EXPANDED
        }
    }
}

/** The current window's content size, provided by [App]. */
val LocalWindowContentSize = staticCompositionLocalOf { DEFAULT_WINDOW_SIZE }

/** The width bucket of the current window, provided by [App]. */
val LocalLayoutWidth = staticCompositionLocalOf { LayoutWidth.EXPANDED }
