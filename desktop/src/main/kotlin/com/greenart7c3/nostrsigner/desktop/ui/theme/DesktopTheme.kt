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

/**
 * `primary`/`tertiary`/`primaryContainer`/`secondaryContainer` are all a light gold in both
 * schemes, so their paired `on*` roles need an explicit dark color — Material3's baseline
 * defaults (tuned for the default purple palette) don't have guaranteed contrast against a
 * custom light container and were left unset here before, making button/nav-rail text
 * unreadable.
 */
val inkColor = Color(0xFF3E2A0D)
val outlineColor = Color(0xFF8A6D3A)
val positiveColor = Color(0xFF2E7D32)
val negativeColor = Color(0xFFC62828)

private val DarkColorPalette = darkColorScheme(
    primary = primaryColor,
    onPrimary = inkColor,
    secondary = primaryVariant,
    onSecondary = Color.White,
    tertiary = secondaryColor,
    onTertiary = inkColor,
    primaryContainer = secondaryColor,
    onPrimaryContainer = inkColor,
    secondaryContainer = secondaryColor,
    onSecondaryContainer = inkColor,
)

private val surfaceColor = Color(0xFFFFDE9E)

private val LightColorPalette = lightColorScheme(
    primary = primaryColor,
    onPrimary = inkColor,
    secondary = primaryVariant,
    onSecondary = Color.White,
    tertiary = secondaryColor,
    onTertiary = inkColor,
    primaryContainer = secondaryColor,
    onPrimaryContainer = inkColor,
    secondaryContainer = secondaryColor,
    onSecondaryContainer = inkColor,
    surface = surfaceColor,
    surfaceContainer = surfaceColor,
    onSurface = inkColor,
    onSurfaceVariant = inkColor,
    background = surfaceColor,
    onBackground = inkColor,
    outline = outlineColor,
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
