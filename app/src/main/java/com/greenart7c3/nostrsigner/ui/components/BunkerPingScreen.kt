package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.RememberType

@Composable
fun BunkerPingScreen(
    account: Account,
    modifier: Modifier,
    shouldRunOnAccept: Boolean?,
    appName: String,
    onAccept: (RememberType) -> Unit,
    onReject: (RememberType) -> Unit,
) {
    var rememberType by remember {
        mutableStateOf(RememberType.NEVER)
    }

    Column(
        modifier,
    ) {
        ProfilePicture(account)

        val message = stringResource(R.string.ping)

        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(appName)
                }
                append(" ${stringResource(R.string.requests)} $message")
            },
            fontSize = 18.sp,
        )
        Spacer(Modifier.size(4.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            Column(Modifier.padding(6.dp)) {
                Text(
                    stringResource(R.string.response),
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    "pong",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        RememberMyChoice(
            shouldRunOnAccept,
            null,
            true,
            onAccept,
            onReject,
        ) {
            rememberType = it
        }

        AcceptRejectButtons(
            onAccept = {
                onAccept(rememberType)
            },
            onReject = {
                onReject(rememberType)
            },
        )
    }
}
