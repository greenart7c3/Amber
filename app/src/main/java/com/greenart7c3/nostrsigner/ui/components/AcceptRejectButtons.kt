package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.accept
import com.greenart7c3.nostrsigner.commonsui.reject
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.ThemePreviews
import org.jetbrains.compose.resources.stringResource

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
        AmberButton(
            Modifier.weight(1f),
            onClick = onReject,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF6B00),
            ),
            text = stringResource(Res.string.reject),
        )

        AmberButton(
            Modifier.weight(1f),
            onClick = onAccept,
            text = stringResource(Res.string.accept),
        )
    }
}

@ThemePreviews
@Composable
fun AcceptRejectButtonsPreview() {
    AmberPreview {
        AcceptRejectButtons(
            onAccept = {},
            onReject = {},
        )
    }
}
