package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * A segmented toggle that lays its options out as equal-width segments filling the
 * available width, with an animated indicator sliding to the selected option. The
 * segments shrink to fit so the control works for any number of options.
 */
@Composable
fun <T> AmberToggles(
    selected: T,
    options: List<T>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (T) -> String,
) {
    val count = options.size
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val padding = 2.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
        ) {
            val segmentWidth = maxWidth / count

            val indicatorOffset by animateDpAsState(
                targetValue = segmentWidth * selectedIndex,
                animationSpec = tween(durationMillis = 250),
                label = "indicatorOffset",
            )

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )

            Row(modifier = Modifier.fillMaxSize()) {
                options.forEach { option ->
                    ToggleOption(
                        modifier = Modifier.width(segmentWidth),
                        text = label(option),
                        isSelected = option == selected,
                        onClick = { onSelected(option) },
                    )
                }
            }
        }
    }
}
