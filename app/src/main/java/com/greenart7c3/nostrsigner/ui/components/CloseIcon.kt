package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.PreviewsLightDark
import com.greenart7c3.nostrsigner.ui.theme.Size20Modifier

@Composable
fun CloseIcon(modifier: Modifier = Modifier) {
    Icon(
        Icons.Outlined.Close,
        contentDescription = "Cancel",
        modifier = modifier.then(Size20Modifier),
        tint = Color.Black,
    )
}

@PreviewsLightDark
@Composable
private fun CloseIconPreview() {
    AmberPreview {
        CloseIcon()
    }
}
