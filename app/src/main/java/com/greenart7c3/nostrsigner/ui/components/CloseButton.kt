package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.material3.Button
import androidx.compose.runtime.Composable

@Composable
fun CloseButton(onCancel: () -> Unit) {
    Button(
        onClick = {
            onCancel()
        }
    ) {
        CloseIcon()
    }
}
