package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import com.greenart7c3.nostrsigner.ui.theme.ButtonBorder

@Composable
fun CloseButton(onCancel: () -> Unit) {
    Button(
        onClick = {
            onCancel()
        },
        shape = ButtonBorder,
    ) {
        CloseIcon()
    }
}
