package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.greenart7c3.nostrsigner.ui.theme.Size20Modifier

@Composable
fun CloseIcon() {
    Icon(
        Icons.Outlined.Close,
        contentDescription = "Cancel",
        modifier = Size20Modifier,
        tint = Color.Black,
    )
}
