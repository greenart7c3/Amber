package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.ui.theme.ButtonBorder

@Composable
fun RawJsonButton(
    onCLick: () -> Unit,
    text: String,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(
            shape = ButtonBorder,
            onClick = onCLick,
        ) {
            Text(text)
        }
    }
}
