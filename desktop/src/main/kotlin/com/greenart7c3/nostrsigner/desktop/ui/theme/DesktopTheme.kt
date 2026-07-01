package com.greenart7c3.nostrsigner.desktop.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Amber's warm color scheme, ported from `app/src/main/java/.../ui/theme/Theme.kt`. */
val primaryColor = Color(0xFFFFCA62)
val primaryVariant = Color(0xFFC8541A)
val secondaryColor = Color(0xFFFFCA62)
val orange = Color(0xFFFF6B00)

private val DarkColorPalette = darkColorScheme(
    primary = primaryColor,
    onPrimary = Color.White,
    secondary = primaryVariant,
    tertiary = secondaryColor,
    primaryContainer = secondaryColor,
    secondaryContainer = secondaryColor,
)

private val LightColorPalette = lightColorScheme(
    primary = primaryColor,
    secondary = primaryVariant,
    tertiary = secondaryColor,
    primaryContainer = secondaryColor,
    secondaryContainer = secondaryColor,
    surface = Color(0xFFFFDE9E),
    surfaceContainer = Color(0xFFFFDE9E),
)

val DesktopShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(0.dp),
)

/** Manual theme selection — Compose Desktop has no reliable `isSystemInDarkTheme()`, so SYSTEM just defaults to LIGHT. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

fun ThemeMode.resolveIsDark(): Boolean = this == ThemeMode.DARK

@Composable
fun DesktopTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorPalette else LightColorPalette,
        shapes = DesktopShapes,
        content = content,
    )
}
