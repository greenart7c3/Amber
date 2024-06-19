package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.ui.theme.ButtonBorder

@Composable
fun PostButton(
    modifier: Modifier = Modifier,
    isActive: Boolean,
    onPost: () -> Unit = {},
) {
    Button(
        enabled = isActive,
        modifier = modifier,
        onClick = {
            if (isActive) {
                onPost()
            }
        },
        shape = ButtonBorder,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(text = stringResource(R.string.save), color = Color.White)
    }
}
