package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.verticalScrollbar(
    state: ScrollState,
    topBarHeight: Dp = 56.dp,
    bottomBarHeight: Dp = 56.dp,
    scrollbarWidth: Dp = 6.dp,
    color: Color = Color.LightGray,
): Modifier {
    return this.then(
        Modifier.drawWithContent {
            drawContent()

            val totalHeight = this.size.height
            val availableHeight = totalHeight - topBarHeight.toPx() - bottomBarHeight.toPx() // Subtract app bar heights

            val viewHeight = availableHeight.toFloat()
            val contentHeight = state.maxValue + viewHeight

            // Calculate scrollbar height based on the visible portion of the content
            val scrollbarHeight = (viewHeight * (viewHeight / contentHeight)).coerceIn(10.dp.toPx()..viewHeight)
            val variableZone = viewHeight - scrollbarHeight
            val scrollbarYoffset = ((state.value.toFloat() / state.maxValue) * variableZone)

            // Draw the scrollbar
            drawRoundRect(
                cornerRadius = CornerRadius(scrollbarWidth.toPx() / 2, scrollbarWidth.toPx() / 2),
                color = color,
                topLeft = Offset(this.size.width - scrollbarWidth.toPx(), scrollbarYoffset),
                size = Size(scrollbarWidth.toPx(), scrollbarHeight),
                alpha = 1.0f,
            )
        },
    )
}
