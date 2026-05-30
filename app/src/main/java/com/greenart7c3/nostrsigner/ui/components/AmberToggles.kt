package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A segmented toggle that lays its options out as equal-width segments with an
 * animated indicator sliding to the selected option.
 *
 * When the segments would become too narrow to fit their labels (many options on a
 * small screen) the row falls back to a fixed minimum segment width and becomes
 * horizontally scrollable, so every option stays usable. In that case a thin
 * scrollbar is shown below the row to signal that it scrolls. Otherwise the
 * segments stretch to fill the available width.
 */
@Composable
fun <T> AmberToggles(
    selected: T,
    options: List<T>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (T) -> String,
) {
    val count = options.size.coerceAtLeast(1)
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val padding = 2.dp
    val minSegmentWidth = 44.dp
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        BoxWithConstraints {
            val available = maxWidth - padding * 2
            val equalWidth = available / count
            val scrollable = equalWidth < minSegmentWidth
            val segmentWidth = if (scrollable) minSegmentWidth else equalWidth

            // Keep the selected segment in view when the row is scrollable.
            LaunchedEffect(selectedIndex, scrollable, segmentWidth) {
                if (scrollable) {
                    val segmentPx = with(density) { segmentWidth.toPx() }
                    val viewportPx = with(density) { available.toPx() }
                    val target = (segmentPx * selectedIndex - (viewportPx - segmentPx) / 2f)
                        .toInt()
                        .coerceIn(0, scrollState.maxValue)
                    scrollState.animateScrollTo(target)
                }
            }

            val indicatorOffset by animateDpAsState(
                targetValue = segmentWidth * selectedIndex,
                animationSpec = tween(durationMillis = 250),
                label = "indicatorOffset",
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(padding)
                        .then(if (scrollable) Modifier.horizontalScroll(scrollState) else Modifier),
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = indicatorOffset)
                            .width(segmentWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )

                    Row(modifier = Modifier.fillMaxHeight()) {
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

                if (scrollable) {
                    val contentPx = with(density) { segmentWidth.toPx() } * count
                    val trackPx = with(density) { available.toPx() }
                    val thumbFraction = (trackPx / contentPx).coerceIn(0.1f, 1f)

                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(available)
                            .height(3.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(thumbFraction)
                                .fillMaxHeight()
                                .offset {
                                    val maxScroll = scrollState.maxValue.toFloat()
                                    val fraction = if (maxScroll > 0f) scrollState.value / maxScroll else 0f
                                    val travel = trackPx * (1f - thumbFraction)
                                    IntOffset((travel * fraction).roundToInt(), 0)
                                }
                                .clip(RoundedCornerShape(percent = 50))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                        )
                    }
                }
            }
        }
    }
}
