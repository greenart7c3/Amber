package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.ui.theme.ButtonBorder

@Composable
fun AcceptRejectButtons(
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(10.dp),
        Arrangement.Center
    ) {
        Button(
            shape = ButtonBorder,
            onClick = onReject,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF5A5554)
            )
        ) {
            Text(stringResource(R.string.reject))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            Modifier.weight(0.8f)
        ) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                shape = ButtonBorder,
                onClick = onAccept
            ) {
                Text(stringResource(R.string.accept))
            }
        }
    }
}
