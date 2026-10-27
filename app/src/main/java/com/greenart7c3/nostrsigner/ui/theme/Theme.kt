package com.greenart7c3.nostrsigner.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.models.ThemeMode

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
val orange = Color(0xFFFF6B00)
val connectedColorLight = Color(0xFF1B5E20)
val connectedColorDark = Color(0xFF81C784)

private val DarkColorPalette =
    darkColorScheme(
        primary = primaryColor,
        onPrimary = Color.Black,
        secondary = primaryVariant,
        tertiary = secondaryColor,
        primaryContainer = secondaryColor,
        onPrimaryContainer = Color.Black,
        secondaryContainer = secondaryColor,
        onSecondaryContainer = Color.Black,
    )

// Pure black at every elevation for AMOLED screens. Surface tint is transparent so
// tonal elevation can't lift cards and sheets to gray; they are told apart by an
// [amoledOutline] border instead (see [amoledBorder]).
private val amoledOutline = Color(0xFF3D3D3D)

private val AmoledColorPalette =
    DarkColorPalette.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceVariant = Color.Black,
        surfaceTint = Color.Transparent,
        surfaceDim = Color.Black,
        surfaceBright = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerHigh = Color.Black,
        surfaceContainerHighest = Color.Black,
        outlineVariant = amoledOutline,
    )

private val LightColorPalette =
    lightColorScheme(
        primary = primaryColor,
        onPrimary = Color.Black,
        secondary = primaryVariant,
        tertiary = secondaryColor,
        primaryContainer = secondaryColor,
        secondaryContainer = secondaryColor,
        surface = Color(0xFFFFDE9E),
        surfaceContainer = Color(0xFFFFDE9E),
    )

private val LocalDarkTheme = staticCompositionLocalOf { false }
private val LocalAmoledTheme = staticCompositionLocalOf { false }

/** Whether Amber's own theme is dark, which can differ from the system setting. */
@Composable
@ReadOnlyComposable
fun isAmberDarkTheme(): Boolean = LocalDarkTheme.current

/**
 * A border for neutral containers (cards, dialogs, sheets) under the AMOLED theme,
 * where they are as black as the screen behind them; null in the other themes.
 */
@Composable
@ReadOnlyComposable
fun amoledBorder(): BorderStroke? = if (LocalAmoledTheme.current) BorderStroke(1.dp, amoledOutline) else null

/** Draws [border] (from [amoledBorder]) around the content, or nothing when null. */
fun Modifier.outlineIfAny(border: BorderStroke?, shape: Shape): Modifier = if (border == null) this else this.border(border, shape)

@Suppress("DEPRECATION")
@Composable
fun NostrSignerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val amoled = themeMode == ThemeMode.AMOLED
    val colors = when {
        amoled -> AmoledColorPalette
        darkTheme -> DarkColorPalette
        else -> LightColorPalette
    }
    val typography = if (darkTheme) TypographyDark else Typography

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAmoledTheme provides amoled,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = typography,
            shapes = Shapes,
            content = content,
        )
    }

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
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

fun Color.Companion.fromHex(colorString: String) = try {
    Color("#$colorString".toColorInt())
} catch (e: Exception) {
    AmberLog.e(Amber.TAG, "Failed to parse color: $colorString", e)
    Unspecified
}
