package com.greenart7c3.nostrsigner.ui.components

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.verticalScrollbar
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun copyToClipboard(clipboard: Clipboard, text: String) {
    Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
        clipboard.setClipEntry(
            ClipEntry(
                ClipData.newPlainText("Tags", text),
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailModal(
    event: Event,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val permission = Permission(
        "sign_event",
        event.kind,
    )
    val context = LocalContext.current
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
                    {
                        copyToClipboard(clipboard, "${event.kind} - ${permission.toLocalizedString(context)}")
                    },
                )
                EventSection(
                    stringResource(R.string.pubkey),
                    event.pubKey.toShortenHex(),
                    {
                        copyToClipboard(clipboard, event.pubKey)
                    },
                )
                EventSection(
                    stringResource(R.string.date),
                    TimeUtils.formatLongToCustomDateTimeWithSeconds(event.createdAt),
                    {
                        copyToClipboard(clipboard, "${event.createdAt}")
                    },
                )
                if (event.content.isNotEmpty()) {
                    EventSection(
                        stringResource(R.string.content),
                        event.content,
                        {
                            copyToClipboard(clipboard, event.content)
                        },
                    )
                }
                if (event.tags.isNotEmpty()) {
                    TagsSection(
                        label = stringResource(R.string.tags),
                        tags = event.tags,
                        onCopy = {
                            copyToClipboard(
                                clipboard,
                                event.tags.joinToString(separator = ", ") { "[${it.joinToString(separator = ", ") { tag -> "\"${tag}\"" }}]" },
                            )
                        },
                    )
                }
            }
        }
    }
}
