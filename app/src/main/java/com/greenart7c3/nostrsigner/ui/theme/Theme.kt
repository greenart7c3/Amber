package com.greenart7c3.nostrsigner.ui.theme

import android.app.Activity
import android.os.Build
import android.util.Log
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
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import com.greenart7c3.nostrsigner.Amber

val Shapes =
    Shapes(
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(0.dp),
    )

val Size35dp = 35.dp

val ButtonBorder = RoundedCornerShape(20.dp)
val Size20Modifier = Modifier.size(20.dp)

// Amber design system — warm dark palette
private val AmberDarkBg0 = Color(0xFF0F0D0B)
private val AmberDarkBg1 = Color(0xFF17140F)
private val AmberDarkBg2 = Color(0xFF1F1A14)
private val AmberDarkBg3 = Color(0xFF2A2319)
private val AmberDarkBg4 = Color(0xFF3A3022)
private val AmberDarkLine = Color(0xFF2C2418)
private val AmberDarkLineStrong = Color(0xFF3E3320)
private val AmberDarkText = Color(0xFFF5ECD9)
private val AmberDarkTextDim = Color(0xFFB5A887)
private val AmberDarkTextFaint = Color(0xFF7A6F58)
private val AmberDarkAmber = Color(0xFFF5A524)
private val AmberDarkAmberFill = Color(0xFF2A1F0D)
private val AmberDarkAmberSoft = Color(0xFFC9851D)
private val AmberDarkDanger = Color(0xFFFF6B6B)
private val AmberDarkOnAmber = Color(0xFF1A1508)

// Amber design system — warm light palette
private val AmberLightBg0 = Color(0xFFFBF7EE)
private val AmberLightBg1 = Color(0xFFF6EFDF)
private val AmberLightBg2 = Color(0xFFFFF9EA)
private val AmberLightBg3 = Color(0xFFFFF4D8)
private val AmberLightBg4 = Color(0xFFF3E6BD)
private val AmberLightLine = Color(0xFFE8DCB8)
private val AmberLightLineStrong = Color(0xFFD4C28A)
private val AmberLightText = Color(0xFF1A1508)
private val AmberLightTextDim = Color(0xFF5C4E30)
private val AmberLightTextFaint = Color(0xFF8A7A55)
private val AmberLightAmber = Color(0xFFB87612)
private val AmberLightAmberFill = Color(0xFFFEF0C8)
private val AmberLightAmberSoft = Color(0xFFA0670D)
private val AmberLightDanger = Color(0xFFC5382F)

val primaryColor = AmberDarkAmber
val primaryVariant = AmberDarkAmberSoft
val secondaryColor = AmberDarkAmber
val orange = Color(0xFFFF6B00)

private val DarkColorPalette =
    darkColorScheme(
        primary = AmberDarkAmber,
        onPrimary = AmberDarkOnAmber,
        primaryContainer = AmberDarkAmberFill,
        onPrimaryContainer = AmberDarkAmber,
        secondary = AmberDarkAmberSoft,
        onSecondary = AmberDarkOnAmber,
        secondaryContainer = AmberDarkBg3,
        onSecondaryContainer = AmberDarkText,
        tertiary = AmberDarkAmber,
        onTertiary = AmberDarkOnAmber,
        background = AmberDarkBg1,
        onBackground = AmberDarkText,
        surface = AmberDarkBg2,
        onSurface = AmberDarkText,
        surfaceVariant = AmberDarkBg3,
        onSurfaceVariant = AmberDarkTextDim,
        surfaceContainer = AmberDarkBg2,
        surfaceContainerLow = AmberDarkBg1,
        surfaceContainerLowest = AmberDarkBg0,
        surfaceContainerHigh = AmberDarkBg3,
        surfaceContainerHighest = AmberDarkBg4,
        outline = AmberDarkLineStrong,
        outlineVariant = AmberDarkLine,
        error = AmberDarkDanger,
        onError = AmberDarkOnAmber,
    )

private val LightColorPalette =
    lightColorScheme(
        primary = AmberLightAmber,
        onPrimary = Color.White,
        primaryContainer = AmberLightAmberFill,
        onPrimaryContainer = AmberLightAmber,
        secondary = AmberLightAmberSoft,
        onSecondary = Color.White,
        secondaryContainer = AmberLightBg3,
        onSecondaryContainer = AmberLightText,
        tertiary = AmberLightAmber,
        onTertiary = Color.White,
        background = AmberLightBg1,
        onBackground = AmberLightText,
        surface = AmberLightBg2,
        onSurface = AmberLightText,
        surfaceVariant = AmberLightBg3,
        onSurfaceVariant = AmberLightTextDim,
        surfaceContainer = AmberLightBg2,
        surfaceContainerLow = AmberLightBg1,
        surfaceContainerLowest = AmberLightBg0,
        surfaceContainerHigh = AmberLightBg3,
        surfaceContainerHighest = AmberLightBg4,
        outline = AmberLightLineStrong,
        outlineVariant = AmberLightLine,
        error = AmberLightDanger,
        onError = Color.White,
    )

@Suppress("DEPRECATION")
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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                if (darkTheme) {
                    window.statusBarColor = colors.background.toArgb()
                } else {
                    window.statusBarColor = colors.surface.toArgb()
                }
                window.navigationBarColor = colors.surface.toArgb()
            }
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

fun Color.light(factor: Float = 0.5f) = this.copy(alpha = this.alpha * factor)

fun Color.Companion.fromHex(colorString: String) = try {
    Color("#$colorString".toColorInt())
} catch (e: Exception) {
    Log.e(Amber.TAG, "Failed to parse color: $colorString", e)
    Unspecified
}
