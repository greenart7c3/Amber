package com.greenart7c3.nostrsigner.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

/** A colored circular initial, standing in for a connected app's/account's icon. */
@Composable
fun LetterAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val color = remember(name) { avatarColorFor(name) }
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

private fun avatarColorFor(seed: String): Color {
    val hue = (seed.hashCode().absoluteValue % 360).toFloat()
    return Color.hsv(hue, 0.55f, 0.75f)
}
