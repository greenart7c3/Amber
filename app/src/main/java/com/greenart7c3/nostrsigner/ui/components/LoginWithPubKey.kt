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
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.encoders.toNpub

@Composable
fun LoginWithPubKey(
    shouldRunOnAccept: Boolean,
    remember: MutableState<Boolean>,
    packageName: String?,
    appName: String,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    account: Account
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
            Column(
                Modifier
                    .padding(6.dp)
            ) {
                Text(
                    stringResource(R.string.wants_to_read_your_public_key),
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
            RawJson(account.keyPair.pubKey.toNpub(), Modifier.weight(1f), stringResource(R.string.my_public_key))
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
