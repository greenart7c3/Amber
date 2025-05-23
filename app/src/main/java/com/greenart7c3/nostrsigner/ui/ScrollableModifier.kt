package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.verticalScrollbar(
    state: ScrollState,
    scrollbarWidth: Dp = 6.dp,
    color: Color = Color.LightGray,
): Modifier {
    return this.then(
        Modifier.drawWithContent {
            drawContent()

            if (state.maxValue > 0) {
                val width = size.width
                val height = size.height

                val scrollFraction = state.value.toFloat() / state.maxValue.toFloat()
                val scrollbarHeight = (height * height / (state.maxValue + height).toFloat())
                    .coerceIn(10.dp.toPx()..height)

                val scrollableHeight = height - scrollbarHeight
                val scrollbarY = scrollableHeight * scrollFraction

                drawRoundRect(
                    color = color,
                    topLeft = Offset(
                        x = width - scrollbarWidth.toPx(),
                        y = scrollbarY,
                    ),
                    size = Size(
                        width = scrollbarWidth.toPx(),
                        height = scrollbarHeight,
                    ),
                    cornerRadius = CornerRadius(scrollbarWidth.toPx() / 2),
                )
            }
        },
    )
}
