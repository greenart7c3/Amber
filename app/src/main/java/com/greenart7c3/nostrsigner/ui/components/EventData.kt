package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.verticalScrollbar
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent

@Composable
fun EventData(
    account: Account,
    paddingValues: PaddingValues,
    shouldAcceptOrReject: Boolean?,
    packageName: String?,
    appName: String,
    applicationName: String?,
    event: Event,
    rawJson: String,
    type: SignerType,
    onAccept: (RememberType) -> Unit,
    onReject: (RememberType) -> Unit,
) {
    var showMore by androidx.compose.runtime.remember {
        mutableStateOf(false)
    }
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var rememberType by remember {
        mutableStateOf(RememberType.NEVER)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScrollbar(scrollState)
            .verticalScroll(scrollState)
            .padding(paddingValues),
    ) {
        ProfilePicture(account)

        val permission = Permission("sign_event", event.kind)
        val text = stringResource(R.string.wants_you_to_sign_a, permission.toLocalizedString(context))
        packageName?.let {
            Text(
                modifier = Modifier
                    .fillMaxWidth(),
                text = it,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(4.dp))
        }
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
            key("event-data-card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        if (event is ContactListEvent) {
                            ContactListDetail(
                                title = stringResource(R.string.following),
                                text = "${event.verifiedFollowKeySet().size}",
                            )
                            ContactListDetail(
                                title = stringResource(R.string.communities),
                                text = "${event.verifiedFollowAddressSet().size}",
                            )
                            ContactListDetail(
                                title = stringResource(R.string.hashtags),
                                text = "${event.countFollowTags()}",
                            )
                            ContactListDetail(
                                title = stringResource(R.string.relays_text),
                                text = "${event.relays()?.keys?.size ?: 0}",
                            )
                        } else {
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
            }
        }

        RawJsonButton(
            onCLick = {
                showMore = !showMore
            },
            if (!showMore) stringResource(R.string.show_details) else stringResource(R.string.hide_details),
        )
        if (showMore) {
            RawJson(rawJson, "", Modifier.height(200.dp), type = type)
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        RememberMyChoice(
            shouldAcceptOrReject,
            packageName,
            false,
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

@Composable
fun ContactListDetail(title: String, text: String) {
    Row(
        modifier = Modifier.padding(horizontal = 6.dp),
    ) {
        Text(
            title,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text,
        )
    }
}
