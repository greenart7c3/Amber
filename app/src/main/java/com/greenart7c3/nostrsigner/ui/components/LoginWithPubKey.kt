package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LoginWithPubKey(
    shouldRunOnAccept: Boolean,
    remember: MutableState<Boolean>,
    packageName: String?,
    appName: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        AppTitle(appName)
        Spacer(Modifier.size(4.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column(
                Modifier
                    .padding(6.dp)
            ) {
                Text(
                    "wants to read your public key",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        RememberMyChoice(
            shouldRunOnAccept,
            remember,
            packageName,
            onAccept
        )

        AcceptRejectButtons(
            onAccept,
            onReject
        )
    }
}
