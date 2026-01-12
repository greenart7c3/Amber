package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.verticalScrollbar
import com.vitorpamplona.quartz.nip01Core.core.Event

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailModal(
    event: Event,
    permission: Permission,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    ModalBottomSheet(
        sheetGesturesEnabled = false,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = false,
            shouldDismissOnClickOutside = false,
        ),
        sheetMaxWidth = Int.MAX_VALUE.dp,
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        Scaffold(
            bottomBar = {
                Box(
                    Modifier.background(MaterialTheme.colorScheme.primary),
                ) {
                    IconRow(
                        center = true,
                        title = stringResource(R.string.go_back),
                        icon = ImageVector.vectorResource(R.drawable.back),
                        onClick = onDismiss,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        ) {
            val scrollState = rememberScrollState()
            Column(
                Modifier
                    .padding(it)
                    .verticalScrollbar(scrollState)
                    .verticalScroll(scrollState),
            ) {
                EventSection(
                    stringResource(R.string.kind),
                    "${event.kind} - ${permission.toLocalizedString(LocalContext.current)}",
                )
                EventSection(
                    stringResource(R.string.pubkey),
                    event.pubKey.toShortenHex(),
                )
                EventSection(
                    stringResource(R.string.date),
                    TimeUtils.formatLongToCustomDateTimeWithSeconds(event.createdAt),
                )
                if (event.content.isNotEmpty()) {
                    EventSection(
                        stringResource(R.string.content),
                        event.content,
                    )
                }
                if (event.tags.isNotEmpty()) {
                    TagsSection(
                        label = stringResource(R.string.tags),
                        tags = event.tags,
                        onCopy = {
                        },
                    )
                }
            }
        }
    }
}

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
                permission = permission,
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
    account: Account,
    modifier: Modifier,
    shouldAcceptOrReject: Boolean?,
    appName: String,
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
            if (!showMore) stringResource(R.string.show_details) else stringResource(R.string.hide_details),
        )
        if (showMore) {
            EventDetailModal(
                event = event,
                permission = permission,
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
