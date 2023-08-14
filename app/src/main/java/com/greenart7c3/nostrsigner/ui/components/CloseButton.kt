package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.greenart7c3.nostrsigner.ui.theme.ButtonBorder

@Composable
fun CloseButton(onCancel: () -> Unit) {
    Button(
        onClick = {
            onCancel()
        },
        shape = ButtonBorder,
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = Color.Gray
            )
    ) {
        CloseIcon()
    }
}
