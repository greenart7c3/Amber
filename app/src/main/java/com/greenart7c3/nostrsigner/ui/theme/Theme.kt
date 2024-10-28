package com.greenart7c3.nostrsigner.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.resolveDefaults

val Shapes =
    Shapes(
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(0.dp),
    )

val Size35dp = 35.dp

val ButtonBorder = RoundedCornerShape(20.dp)
val Size20Modifier = Modifier.size(20.dp)

val primaryColor = Color(0xFFFFCA62)
val primaryVariant = Color(0xFFC8541A)
val secondaryColor = Color(0xFFFFCA62)

val RichTextDefaults = RichTextStyle().resolveDefaults()

private val DarkColorPalette =
    darkColorScheme(
        primary = primaryColor,
        onPrimary = Color.White,
        secondary = primaryVariant,
        tertiary = secondaryColor,
        primaryContainer = secondaryColor,
        secondaryContainer = secondaryColor,
    )

private val LightColorPalette =
    lightColorScheme(
        primary = primaryColor,
        secondary = primaryVariant,
        tertiary = secondaryColor,
        primaryContainer = secondaryColor,
        secondaryContainer = secondaryColor,
        surface = Color(0xFFFFDE9E),
        surfaceContainer = Color(0xFFFFDE9E),
    )

@Composable
fun NostrSignerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColorPalette else LightColorPalette
    val typography = if (darkTheme) TypographyDark else Typography

    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = Shapes,
        content = content,
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            if (darkTheme) {
                window.statusBarColor = colors.background.toArgb()
            } else {
                window.statusBarColor = colors.surface.toArgb()
            }
            window.navigationBarColor = colors.surface.toArgb()
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }
}
