package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.R

@Composable
fun AcceptRejectButtons(
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
            onClick = onReject,
            shape = RoundedCornerShape(20),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    LocalDensity.current.density,
                    1f,
                ),
            ) {
                Text(
                    text = stringResource(R.string.reject),
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        AmberButton(
            Modifier.weight(1f),
            onClick = onAccept,
            text = stringResource(R.string.accept),
        )
    }
}
