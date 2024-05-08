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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.vitorpamplona.quartz.events.Event

@Composable
fun EventData(
    shouldRunOnAccept: Boolean,
    remember: MutableState<Boolean>,
    packageName: String?,
    appName: String,
    applicationName: String?,
    event: Event,
    rawJson: String,
    type: SignerType,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    var showMore by androidx.compose.runtime.remember {
        mutableStateOf(false)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(8.dp),
    ) {
        val permission = Permission("sign_event", event.kind)
        val text = "wants you to sign a $permission"
        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(applicationName ?: appName)
                }
                append(" $text")
            },
            fontSize = 18.sp,
        )
        Spacer(Modifier.size(4.dp))

        val content = if (event.kind == 22242) AmberEvent.relay(event) else event.content
        if (content.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Event content",
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
        }
        RawJsonButton(
            onCLick = {
                showMore = !showMore
            },
            if (!showMore) stringResource(R.string.show_details) else stringResource(R.string.hide_details),
        )
        if (showMore) {
            RawJson(rawJson, "", Modifier.weight(1f), type = type)
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        RememberMyChoice(
            shouldRunOnAccept,
            remember.value,
            packageName,
            onAccept,
        ) {
            remember.value = !remember.value
        }

        AcceptRejectButtons(
            onAccept,
            onReject,
        )
    }
}
