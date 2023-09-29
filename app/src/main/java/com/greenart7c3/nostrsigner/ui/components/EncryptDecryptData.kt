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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.SignerType

@Composable
fun EncryptDecryptData(
    shouldRunOnAccept: Boolean,
    remember: MutableState<Boolean>,
    packageName: String?,
    appName: String,
    type: SignerType,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onCopy: () -> String
) {
    var showMore by androidx.compose.runtime.remember {
        mutableStateOf(false)
    }

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
                    SignerType.NIP44_ENCRYPT -> stringResource(R.string.encrypt_nip44)
                    SignerType.NIP04_ENCRYPT -> stringResource(R.string.encrypt_nip04)
                    SignerType.NIP44_DECRYPT -> stringResource(R.string.decrypt_nip44)
                    SignerType.NIP04_DECRYPT -> stringResource(R.string.decrypt_nip04)
                    SignerType.DECRYPT_ZAP_EVENT -> stringResource(R.string.decrypt_zap_event)
                    else -> stringResource(R.string.encrypt_decrypt)
                }
                Text(
                    stringResource(R.string.wants_you_to_data, message),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        RawJsonButton(
            onCLick = {
                showMore = !showMore
            },
            if (!showMore) stringResource(R.string.show_details) else stringResource(R.string.hide_details)
        )
        if (showMore) {
            RawJson(onCopy(), Modifier.weight(1f), stringResource(R.string.encrypted_decrypted_data))
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

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
