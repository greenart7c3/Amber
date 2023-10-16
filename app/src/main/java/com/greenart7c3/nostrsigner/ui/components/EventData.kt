package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.service.toShortenHex

@Composable
fun EventData(
    shouldRunOnAccept: Boolean,
    remember: MutableState<Boolean>,
    packageName: String?,
    appName: String,
    event: AmberEvent,
    rawJson: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    var showMore by androidx.compose.runtime.remember {
        mutableStateOf(false)
    }
    val eventDescription = stringResource(R.string.event)

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
                val message = if (event.kind == 22242) {
                    stringResource(R.string.client_authentication)
                } else {
                    eventDescription
                }
                Text(
                    stringResource(R.string.wants_you_to_sign_a, message),
                    fontWeight = FontWeight.Bold
                )

                EventRow(
                    Icons.Default.CalendarMonth,
                    stringResource(R.string.kind),
                    "${event.kind}"
                )

                EventRow(
                    Icons.Default.AccessTime,
                    stringResource(R.string.created_at),
                    TimeUtils.format(event.createdAt)
                )

                EventRow(
                    Icons.Default.Person,
                    stringResource(R.string.signed_by),
                    event.pubKey.toShortenHex()
                )

                EventRow(
                    Icons.Default.ContentPaste,
                    stringResource(R.string.content),
                    event.content
                )

                if (event.kind == 22242) {
                    EventRow(
                        Icons.Default.Wifi,
                        stringResource(R.string.relay),
                        event.tags.firstOrNull { it.size > 1 && it[0] == "relay" }?.get(1)?.removePrefix("wss://")?.removePrefix("ws://") ?: ""
                    )
                }

                Divider(
                    modifier = Modifier.padding(top = 15.dp),
                    thickness = Dp.Hairline
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
            RawJson(rawJson, Modifier.weight(1f))
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
