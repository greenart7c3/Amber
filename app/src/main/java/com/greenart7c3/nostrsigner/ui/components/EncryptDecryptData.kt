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
import com.greenart7c3.nostrsigner.models.SignerType

@Composable
fun EncryptDecryptData(
    shouldRunOnAccept: Boolean,
    remember: MutableState<Boolean>,
    packageName: String?,
    appName: String,
    type: SignerType,
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
            Column(Modifier.padding(6.dp)) {
                val message = when (type) {
                    SignerType.NIP44_ENCRYPT -> "encrypt nip44"
                    SignerType.NIP04_ENCRYPT -> "encrypt nip04"
                    SignerType.NIP44_DECRYPT -> "decrypt nip44"
                    SignerType.NIP04_DECRYPT -> "decrypt nip04"
                    SignerType.DECRYPT_ZAP_EVENT -> "decrypt zap event"
                    else -> "encrypt/decrypt"
                }
                Text(
                    "wants you to $message data",
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
