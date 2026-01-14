package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.ui.RememberType
import com.vitorpamplona.quartz.nip01Core.core.Event

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventData(
    modifier: Modifier,
    shouldAcceptOrReject: Boolean?,
    packageName: String?,
    event: Event,
    onAccept: (RememberType) -> Unit,
    onReject: (RememberType) -> Unit,
) {
    var showMore by androidx.compose.runtime.remember {
        mutableStateOf(false)
    }
    val context = LocalContext.current
    var rememberType by remember {
        mutableStateOf(RememberType.NEVER)
    }

    Column(
        modifier,
    ) {
        LocalAppIcon(packageName)

        val permission = Permission("sign_event", event.kind)
        val text = stringResource(R.string.wants_you_to_sign_a, permission.toLocalizedString(context))
        Text(
            text.capitalize(Locale.current),
            fontSize = 18.sp,
        )
        Spacer(Modifier.size(4.dp))

        RawJsonButton(
            onCLick = {
                showMore = !showMore
            },
            stringResource(R.string.show_details),
        )
        if (showMore) {
            EventDetailModal(
                event = event,
                onDismiss = {
                    showMore = false
                },
            )
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
fun BunkerEventData(
    modifier: Modifier,
    shouldAcceptOrReject: Boolean?,
    appName: String,
    event: Event,
    onAccept: (RememberType) -> Unit,
    onReject: (RememberType) -> Unit,
) {
    var showMore by androidx.compose.runtime.remember {
        mutableStateOf(false)
    }
    val context = LocalContext.current
    var rememberType by remember {
        mutableStateOf(RememberType.NEVER)
    }

    Column(
        modifier,
    ) {
        val permission = Permission("sign_event", event.kind)
        val text = stringResource(R.string.wants_you_to_sign_a, permission.toLocalizedString(context))
        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(appName)
                }
                append(" $text")
            },
            fontSize = 18.sp,
        )
        Spacer(Modifier.size(4.dp))

        RawJsonButton(
            onCLick = {
                showMore = !showMore
            },
            stringResource(R.string.show_details),
        )
        if (showMore) {
            EventDetailModal(
                event = event,
                onDismiss = {
                    showMore = false
                },
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        RememberMyChoice(
            shouldAcceptOrReject,
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
