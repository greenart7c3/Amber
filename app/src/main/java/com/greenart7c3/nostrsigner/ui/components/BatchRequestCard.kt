package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.event_kind
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.localizedBlocking
import com.greenart7c3.nostrsigner.commonsui.show_details
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.greenart7c3.nostrsigner.service.isBlockedByAuthWhitelist
import com.greenart7c3.nostrsigner.ui.LocalSettingsStore
import com.vitorpamplona.quartz.nip01Core.core.Event
import org.jetbrains.compose.resources.stringResource

/**
 * One request of a batch, from an app (NIP-55) or a bunker client (NIP-46): what it asks
 * for ([label]), its content in short ([preview]), the raw [event] or [encryptedData] behind
 * Show Details, the account that signs it, and approve or deny.
 */
@Composable
fun BatchRequestCard(
    type: SignerType,
    label: String,
    preview: String,
    event: Event?,
    encryptedData: EncryptedDataKind?,
    signingAs: Account?,
    approved: Boolean,
    onApproveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsStore = LocalSettingsStore.current
    var showDetails by remember { mutableStateOf(false) }
    val typeName = type.toString()
    val hasDetails = (type == SignerType.SIGN_EVENT && event != null) ||
        ((typeName.contains("ENCRYPT") || typeName.contains("DECRYPT")) && encryptedData != null)

    Card(
        modifier.padding(4.dp),
        colors = CardDefaults.cardColors().copy(
            containerColor = MaterialTheme.colorScheme.background,
        ),
        border = BorderStroke(1.dp, Color.Gray),
    ) {
        Column(
            Modifier.padding(8.dp),
        ) {
            RequestSummary(
                label = label,
                preview = preview,
                onShowDetails = if (hasDetails) {
                    { showDetails = true }
                } else {
                    null
                },
            )

            signingAs?.let { SigningAs(it) }

            RequestApproval(
                blockedByAuthWhitelist = isBlockedByAuthWhitelist(event, settingsStore.settings.authWhitelist),
                approved = approved,
                onApproveChange = onApproveChange,
            )
        }
    }

    if (showDetails) {
        BatchRequestDetails(type = type, event = event, encryptedData = encryptedData, onDismiss = { showDetails = false })
    }
}

/**
 * What an event of [kind] is called in a batch, or its `alt` tag (NIP-31) when Amber has no
 * name for the kind and the event has one.
 */
fun eventKindLabel(
    kind: Int,
    tags: Array<Array<String>>,
): String {
    val kindName = Permission("sign_event", kind).localizedBlocking()
    if (kindName != getStringBlocking(Res.string.event_kind, kind.toString())) return kindName
    return tags.firstOrNull { it.size > 1 && it[0] == "alt" }?.getOrNull(1) ?: kindName
}

@Composable
private fun RequestSummary(
    label: String,
    preview: String,
    onShowDetails: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 8.dp),
        ) {
            Text(text = label)
            if (preview.isNotBlank()) {
                Text(
                    text = preview,
                    color = Color.Gray,
                    maxLines = 2,
                )
            }
            if (onShowDetails != null) {
                RawJsonButton(
                    onCLick = onShowDetails,
                    text = stringResource(Res.string.show_details),
                )
            }
        }
    }
}

@Composable
private fun BatchRequestDetails(
    type: SignerType,
    event: Event?,
    encryptedData: EncryptedDataKind?,
    onDismiss: () -> Unit,
) {
    if (type == SignerType.SIGN_EVENT) {
        EventDetailModal(
            event = event!!,
            onDismiss = onDismiss,
        )
    } else {
        EncryptDecryptDetailModal(
            type = type,
            encryptedData = encryptedData,
            onDismiss = onDismiss,
        )
    }
}
