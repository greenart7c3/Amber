package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.ui.theme.ButtonBorder

@Composable
fun PostButton(modifier: Modifier = Modifier, onPost: () -> Unit = {}, isActive: Boolean) {
    Button(
        modifier = modifier,
        onClick = {
            if (isActive) {
                onPost()
            }
        },
        shape = ButtonBorder,
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text = "Save", color = Color.White)
    }
}
