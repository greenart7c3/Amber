package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.ui.RememberType

@Composable
fun BunkerGetPubKeyScreen(
    modifier: Modifier,
    applicationName: String,
    onAccept: (List<Permission>?, Int, Boolean?, RememberType) -> Unit,
    onReject: (RememberType) -> Unit,
) {
    var rememberType by remember { mutableStateOf(RememberType.NEVER) }

    Column(
        modifier,
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = applicationName,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Text(
            stringResource(R.string.would_like_your_permission_to_read_your_public_key_and_sign_events_on_your_behalf),
        )

        Spacer(modifier = Modifier.weight(1f))

        RememberMyChoice(
            alwaysShow = true,
            shouldRunAcceptOrReject = null,
            onAccept = {},
            onReject = onReject,
            onChanged = {
                rememberType = it
            },
            packageName = null,
        )

        AcceptRejectButtons(
            onAccept = {
                onAccept(null, 1, null, rememberType)
            },
            onReject = {
                onReject(rememberType)
            },
        )
    }
}
