package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.event_kind
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.localizedBlocking
import com.greenart7c3.nostrsigner.commonsui.report_missing_event_kind_translation
import com.greenart7c3.nostrsigner.commonsui.show_details
import com.greenart7c3.nostrsigner.commonsui.wants_you_to_sign_a
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.LocalPendingReports
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.ThemePreviews
import com.greenart7c3.nostrsigner.ui.theme.previewAccount
import com.vitorpamplona.quartz.nip01Core.core.Event
import org.jetbrains.compose.resources.stringResource

@Composable
fun EventData(
    shouldAcceptOrReject: Boolean?,
    packageName: String?,
    event: Event,
    account: Account,
    onAccept: (RememberType) -> Unit,
    onReject: (RememberType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LocalAppIcon(packageName)

        val text = stringResource(Res.string.wants_you_to_sign_a, eventKindLabel(event.kind, event.tags))
        Text(
            text.capitalize(Locale.current),
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (isUnnamedKind(event)) {
            val appInfo = packageName?.let { rememberAppDisplayInfo(it) }
            ReportMissingEventKindButton(event.kind, appInfo?.name)
        }

        SignEventActions(
            event = event,
            account = account,
            shouldAcceptOrReject = shouldAcceptOrReject,
            packageName = packageName,
            alwaysShowRemember = false,
            onAccept = onAccept,
            onReject = onReject,
        )
    }
}

/** Amber has no name for [event]'s kind, and the event has no alt tag to name it either. */
private fun isUnnamedKind(event: Event): Boolean {
    val unnamed = Permission("sign_event", event.kind).localizedBlocking() == getStringBlocking(Res.string.event_kind, event.kind.toString())
    return unnamed && event.tags.none { it.size > 1 && it[0] == "alt" }
}

/**
 * The rest of a sign screen: Show Details, the signing account, the remember choice
 * ([alwaysShowRemember] for a bunker client) and accept or reject.
 */
@Composable
private fun ColumnScope.SignEventActions(
    event: Event,
    account: Account,
    shouldAcceptOrReject: Boolean?,
    packageName: String?,
    alwaysShowRemember: Boolean,
    onAccept: (RememberType) -> Unit,
    onReject: (RememberType) -> Unit,
) {
    var showMore by remember {
        mutableStateOf(false)
    }
    var rememberType by remember {
        mutableStateOf(RememberType.NEVER)
    }

    Spacer(Modifier.size(4.dp))

    RawJsonButton(
        onCLick = {
            showMore = !showMore
        },
        stringResource(Res.string.show_details),
    )

    Spacer(Modifier.size(16.dp))

    SigningAs(account)

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
        shouldRunAcceptOrReject = shouldAcceptOrReject,
        packageName = packageName,
        alwaysShow = alwaysShowRemember,
        onAccept = onAccept,
        onReject = onReject,
        onChange = { rememberType = it },
    )

    AcceptRejectButtons(
        onAccept = {
            onAccept(rememberType)
        },
        onReject = {
            onReject(rememberType)
        },
    )
}

@Composable
fun BunkerEventData(
    shouldAcceptOrReject: Boolean?,
    appName: String,
    event: Event,
    account: Account,
    onAccept: (RememberType) -> Unit,
    onReject: (RememberType) -> Unit,
    modifier: Modifier = Modifier,
    iconUrl: String = "",
) {
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.size(16.dp))
        RemoteAppIcon(iconUrl, appName)
        val text = stringResource(Res.string.wants_you_to_sign_a, eventKindLabel(event.kind, event.tags))
        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(appName)
                }
                append(" $text")
            },
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (isUnnamedKind(event)) {
            ReportMissingEventKindButton(event.kind, appName)
        }

        SignEventActions(
            event = event,
            account = account,
            shouldAcceptOrReject = shouldAcceptOrReject,
            packageName = null,
            alwaysShowRemember = true,
            onAccept = onAccept,
            onReject = onReject,
        )
    }
}

@Composable
fun ContactListDetail(title: String, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 6.dp),
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

@Composable
fun ReportMissingEventKindButton(kind: Int, appName: String? = null) {
    val pendingReports = LocalPendingReports.current
    AmberButton(
        onClick = {
            val text = if (!appName.isNullOrBlank()) {
                "Missing event kind translation: $kind (app: $appName)"
            } else {
                "Missing event kind translation: $kind"
            }
            pendingReports.pendingTranslationReport.value = text
        },
        text = stringResource(Res.string.report_missing_event_kind_translation),
    )
}
private fun previewEvent() = Event(
    id = "0".repeat(64),
    pubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
    createdAt = 1735689600,
    kind = 1,
    tags = arrayOf(arrayOf("t", "amber")),
    content = "Hello Nostr!",
    sig = "",
)

@ThemePreviews
@Composable
private fun EventDataPreview() {
    AmberPreview {
        EventData(
            modifier = Modifier
                .fillMaxWidth()
                .height(650.dp)
                .padding(16.dp),
            shouldAcceptOrReject = null,
            packageName = null,
            event = previewEvent(),
            account = previewAccount(),
            onAccept = {},
            onReject = {},
        )
    }
}

@ThemePreviews
@Composable
private fun BunkerEventDataPreview() {
    AmberPreview {
        BunkerEventData(
            modifier = Modifier
                .fillMaxWidth()
                .height(650.dp)
                .padding(16.dp),
            shouldAcceptOrReject = null,
            appName = "Amethyst",
            event = previewEvent(),
            account = previewAccount(),
            onAccept = {},
            onReject = {},
        )
    }
}
