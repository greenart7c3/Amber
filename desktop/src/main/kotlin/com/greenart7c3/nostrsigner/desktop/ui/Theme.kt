package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore

val Shapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(0.dp),
)

val ButtonBorder = RoundedCornerShape(20.dp)

val primaryColor = Color(0xFFFFCA62)
val primaryVariant = Color(0xFFC8541A)
val secondaryColor = Color(0xFFFFCA62)
val orange = Color(0xFFFF6B00)

private val darkTextOnLightAccent = Color(0xFF201A0B)

private val DarkColorPalette = darkColorScheme(
    primary = primaryColor,
    onPrimary = darkTextOnLightAccent,
    secondary = primaryVariant,
    onSecondary = Color.White,
    tertiary = secondaryColor,
    onTertiary = darkTextOnLightAccent,
    primaryContainer = secondaryColor,
    onPrimaryContainer = darkTextOnLightAccent,
    secondaryContainer = secondaryColor,
    onSecondaryContainer = darkTextOnLightAccent,
)

private val LightColorPalette = lightColorScheme(
    primary = primaryColor,
    onPrimary = darkTextOnLightAccent,
    secondary = primaryVariant,
    tertiary = secondaryColor,
    onTertiary = darkTextOnLightAccent,
    primaryContainer = secondaryColor,
    onPrimaryContainer = darkTextOnLightAccent,
    secondaryContainer = secondaryColor,
    onSecondaryContainer = darkTextOnLightAccent,
    surface = Color(0xFFFFDE9E),
    surfaceContainer = Color(0xFFFFDE9E),
)

@Composable
fun NostrSignerTheme(content: @Composable () -> Unit) {
    val settings by SettingsStore.settings.collectAsState()
    val darkTheme = settings.darkTheme ?: isSystemInDarkTheme()
    val colors = if (darkTheme) DarkColorPalette else LightColorPalette

    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes,
        content = content,
    )
}
