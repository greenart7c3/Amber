package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.ButtonBorder
import com.greenart7c3.nostrsigner.ui.theme.PreviewsLightDark

@Composable
fun CloseButton(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = {
            onCancel()
        },
        shape = ButtonBorder,
        modifier = modifier,
    ) {
        CloseIcon()
    }
}

@PreviewsLightDark
@Composable
private fun CloseButtonPreview() {
    AmberPreview {
        CloseButton(onCancel = {})
    }
}
