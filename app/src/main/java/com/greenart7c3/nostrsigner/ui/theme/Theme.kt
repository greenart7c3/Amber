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
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

val Size35dp = 35.dp

val ButtonBorder = RoundedCornerShape(20.dp)
val Size20Modifier = Modifier.size(20.dp)

// Refined warm amber palette. Light.
val AmberInk = Color(0xFF1A1614)
val AmberInk2 = Color(0xFF47413A)
val AmberInk3 = Color(0xFF7A7269)
val AmberInk4 = Color(0xFFA89F92)
val AmberBg = Color(0xFFFBF7F0)
val AmberSurface = Color(0xFFFFFFFF)
val AmberSurface2 = Color(0xFFF5EFE3)
val AmberSurface3 = Color(0xFFEBE3D2)
val AmberLine = Color(0xFFE8DFCD)
val AmberLine2 = Color(0xFFD8CCB3)
val AmberBrand = Color(0xFFD97706)
val AmberBrandHi = Color(0xFFF59E0B)
val AmberBrandLo = Color(0xFFB45309)
val AmberWash = Color(0xFFFEF3C7)
val AmberWash2 = Color(0xFFFDE8B8)
val AmberDanger = Color(0xFFB91C1C)
val AmberDangerWash = Color(0xFFFEE2E2)

// Refined warm amber palette. Dark.
val AmberInkDark = Color(0xFFF7F0E2)
val AmberInk2Dark = Color(0xFFD9CFBD)
val AmberInk3Dark = Color(0xFFA39887)
val AmberInk4Dark = Color(0xFF6E6558)
val AmberBgDark = Color(0xFF141110)
val AmberSurfaceDark = Color(0xFF1D1917)
val AmberSurface2Dark = Color(0xFF27211D)
val AmberSurface3Dark = Color(0xFF322B25)
val AmberLineDark = Color(0xFF2E2721)
val AmberLine2Dark = Color(0xFF3B332C)
val AmberBrandDark = Color(0xFFF59E0B)
val AmberBrandHiDark = Color(0xFFFBBF24)
val AmberBrandLoDark = Color(0xFFD97706)
val AmberWashDark = Color(0xFF3A2810)
val AmberWash2Dark = Color(0xFF4A3316)
val AmberDangerDark = Color(0xFFF87171)
val AmberDangerWashDark = Color(0xFF3A1515)

// Kept for source compatibility with existing references elsewhere in the app.
val primaryColor = AmberBrand
val primaryVariant = AmberBrandLo
val secondaryColor = AmberBrandHi
val orange = AmberBrand

private val DarkColorPalette =
    darkColorScheme(
        primary = AmberBrandDark,
        onPrimary = AmberBgDark,
        secondary = AmberBrandLoDark,
        onSecondary = AmberInkDark,
        tertiary = AmberBrandHiDark,
        onTertiary = AmberBgDark,
        primaryContainer = AmberWashDark,
        onPrimaryContainer = AmberBrandHiDark,
        secondaryContainer = AmberWash2Dark,
        onSecondaryContainer = AmberInkDark,
        background = AmberBgDark,
        onBackground = AmberInkDark,
        surface = AmberSurfaceDark,
        onSurface = AmberInkDark,
        surfaceVariant = AmberSurface2Dark,
        onSurfaceVariant = AmberInk2Dark,
        surfaceContainer = AmberSurface2Dark,
        surfaceContainerHigh = AmberSurface3Dark,
        surfaceContainerHighest = AmberSurface3Dark,
        surfaceContainerLow = AmberSurfaceDark,
        surfaceContainerLowest = AmberBgDark,
        outline = AmberLine2Dark,
        outlineVariant = AmberLineDark,
        error = AmberDangerDark,
        onError = AmberBgDark,
        errorContainer = AmberDangerWashDark,
        onErrorContainer = AmberDangerDark,
    )

private val LightColorPalette =
    lightColorScheme(
        primary = AmberBrand,
        onPrimary = Color.White,
        secondary = AmberBrandLo,
        onSecondary = Color.White,
        tertiary = AmberBrandHi,
        onTertiary = AmberInk,
        primaryContainer = AmberWash,
        onPrimaryContainer = AmberBrandLo,
        secondaryContainer = AmberWash2,
        onSecondaryContainer = AmberBrandLo,
        background = AmberBg,
        onBackground = AmberInk,
        surface = AmberSurface,
        onSurface = AmberInk,
        surfaceVariant = AmberSurface2,
        onSurfaceVariant = AmberInk2,
        surfaceContainer = AmberSurface2,
        surfaceContainerHigh = AmberSurface3,
        surfaceContainerHighest = AmberSurface3,
        surfaceContainerLow = AmberBg,
        surfaceContainerLowest = AmberSurface,
        outline = AmberLine2,
        outlineVariant = AmberLine,
        error = AmberDanger,
        onError = Color.White,
        errorContainer = AmberDangerWash,
        onErrorContainer = AmberDanger,
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
                    window.statusBarColor = colors.background.toArgb()
                }
                window.navigationBarColor = colors.background.toArgb()
            }
            insetsController.isAppearanceLightNavigationBars = !darkTheme
            insetsController.isAppearanceLightStatusBars = !darkTheme
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
