package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.ThemePreviews

/**
 * Centered avatar header for a request screen. Shows the remote client icon from
 * [imageUrl] when present, otherwise a letter placeholder built from [name].
 */
@Composable
fun RemoteAppIcon(
    imageUrl: String?,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppAvatar(
            iconUrl = imageUrl,
            name = name,
            size = size,
        )
    }
}

@ThemePreviews
@Composable
fun RemoteAppIconPreview() {
    AmberPreview {
        RemoteAppIcon(
            imageUrl = null,
            name = "Amethyst",
        )
    }
}
