package com.greenart7c3.nostrsigner.ui.components

import android.content.ClipData
import android.content.Intent
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
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
import androidx.core.net.toUri
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.ui.RememberType
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventData(
    modifier: Modifier,
    shouldAcceptOrReject: Boolean?,
    packageName: String?,
    event: Event,
    account: Account,
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
        val kindTranslation = permission.toLocalizedString(context)
        val text = stringResource(R.string.wants_you_to_sign_a, kindTranslation)
        Text(
            text.capitalize(Locale.current),
            fontSize = 18.sp,
        )
        if (kindTranslation == stringResource(R.string.event_kind, event.kind.toString())) {
            ReportMissingEventKindButton(account, event.kind)
        }
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
    account: Account,
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
        val kindTranslation = permission.toLocalizedString(context)
        val text = stringResource(R.string.wants_you_to_sign_a, kindTranslation)
        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(appName)
                }
                append(" $text")
            },
            fontSize = 18.sp,
        )
        if (kindTranslation == stringResource(R.string.event_kind, event.kind.toString())) {
            ReportMissingEventKindButton(account, event.kind)
        }
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

@Composable
fun ReportMissingEventKindButton(account: Account, kind: Int) {
    val clipboardManager = LocalClipboard.current
    AmberButton(
        onClick = {
            val text = "Missing event kind translation: $kind"
            if (BuildFlavorChecker.isOfflineFlavor()) {
                Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                    clipboardManager.setClipEntry(
                        ClipEntry(
                            ClipData.newPlainText("", text),
                        ),
                    )
                    val intent = Intent(Intent.ACTION_VIEW)
                    val npub = Hex.decode("7579076d9aff0a4cfdefa7e2045f2486c7e5d8bc63bfc6b45397233e1bbfcb19").toNpub()
                    intent.data = "nostr:$npub".toUri()
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    Amber.instance.startActivity(intent)
                }
            } else {
                Amber.instance.applicationIOScope.launch {
                    val client = NostrClient(Amber.instance.factory, Amber.instance.applicationIOScope)
                    client.connect()
                    val template = ChatMessageEvent.build(
                        msg = text,
                        to = listOf(PTag("7579076d9aff0a4cfdefa7e2045f2486c7e5d8bc63bfc6b45397233e1bbfcb19")),
                        createdAt = System.currentTimeMillis() / 1000,
                    ) {
                        val tenDaysInSeconds = 10L * 86_400
                        expiration(TimeUtils.now() + tenDaysInSeconds)
                    }
                    val signedEvents = account.createMessageNIP17(template)
                    signedEvents.wraps.forEach { wrap ->
                        client.send(
                            event = wrap,
                            relayList = setOf(
                                NormalizedRelayUrl(url = "wss://inbox.nostr.wine"),
                                NormalizedRelayUrl(url = "wss://nostr.land"),
                            ),
                        )
                    }
                    delay(10000)
                    client.disconnect()
                }
            }
        },
        text = stringResource(R.string.report_missing_event_kind_translation),
    )
}
