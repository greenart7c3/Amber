package com.greenart7c3.nostrsigner.ui.theme

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * Multipreview annotation that renders a composable in both the light and
 * dark variants of [NostrSignerTheme].
 */
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ThemePreviews

/**
 * Shared wrapper for component previews: applies [NostrSignerTheme] and paints
 * the theme background behind the content, so previews look like the real app
 * in both themes.
 *
 * Previews must only compose components that don't reach global state
 * ([com.greenart7c3.nostrsigner.Amber.instance] or an
 * [com.greenart7c3.nostrsigner.models.Account]) during composition — the
 * Application singleton doesn't exist in the preview renderer.
 */
@Composable
fun AmberPreview(content: @Composable () -> Unit) {
    NostrSignerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}
