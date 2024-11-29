package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.R

@Composable
fun AcceptRejectButtons(
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth(),
        Arrangement.Center,
    ) {
        AmberButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onAccept,
            text = stringResource(R.string.accept),
        )

        Spacer(modifier = Modifier.width(20.dp))
        AmberButton(
            onClick = onReject,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF6B00),
            ),
            text = stringResource(R.string.reject),
        )
    }
}
