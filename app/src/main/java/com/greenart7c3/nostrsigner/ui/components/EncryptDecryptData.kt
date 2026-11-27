package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.decrypt_zap_event
import com.greenart7c3.nostrsigner.commonsui.encryption_scope
import com.greenart7c3.nostrsigner.commonsui.event_kind
import com.greenart7c3.nostrsigner.commonsui.following
import com.greenart7c3.nostrsigner.commonsui.for_all_methods
import com.greenart7c3.nostrsigner.commonsui.for_this_method_only
import com.greenart7c3.nostrsigner.commonsui.localizedBlocking
import com.greenart7c3.nostrsigner.commonsui.show_details
import com.greenart7c3.nostrsigner.commonsui.wants_to_encrypt_this_list_of_tags_with
import com.greenart7c3.nostrsigner.commonsui.wants_to_encrypt_this_text_with
import com.greenart7c3.nostrsigner.commonsui.wants_to_encrypt_with
import com.greenart7c3.nostrsigner.commonsui.wants_to_read_from_encrypted_content
import com.greenart7c3.nostrsigner.commonsui.wants_to_read_this_list_of_tags_from_encrypted_content
import com.greenart7c3.nostrsigner.commonsui.wants_to_read_this_text_from_encrypted_content
import com.greenart7c3.nostrsigner.commonsui.wants_you_to
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.PrivateZapEncryptedDataKind
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.PreviewsLightDark
import com.greenart7c3.nostrsigner.ui.theme.previewAccount
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import org.jetbrains.compose.resources.stringResource

@Composable
fun EncryptDecryptData(
    encryptedData: EncryptedDataKind?,
    shouldRunOnAccept: Boolean?,
    packageName: String?,
    type: SignerType,
    account: Account,
    onAccept: (RememberType, DecryptTypeScope) -> Unit,
    onReject: (RememberType, DecryptTypeScope) -> Unit,
    modifier: Modifier = Modifier,
    defaultScope: DecryptTypeScope = DecryptTypeScope.ALL,
) {
    var rememberType by remember {
        mutableStateOf(RememberType.NEVER)
    }
    var scope by remember { mutableStateOf(defaultScope) }

    Column(
        modifier,
    ) {
        LocalAppIcon(packageName)

        Text(
            encryptDecryptPrompt(type, encryptedData).trim().capitalize(Locale.current),
            fontSize = 18.sp,
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors().copy(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        ) {
            Column {
                if (encryptedData is EventEncryptedDataKind) {
                    requestEvent(encryptedData)?.let { EventDetailsButton(it) }
                } else {
                    TagArrayOrText(type, encryptedData, account)
                }
            }
        }

        if (encryptedData !is TagArrayEncryptedDataKind) {
            Spacer(Modifier.size(16.dp))
            SigningAs(account)
            Spacer(modifier = Modifier.weight(1f))
        }

        if (type != SignerType.DECRYPT_ZAP_EVENT) {
            Spacer(Modifier.size(8.dp))
            EncryptionScopeToggle(scope, onSelect = { scope = it })
            Spacer(Modifier.size(8.dp))
        }

        RememberMyChoice(
            shouldRunOnAccept,
            packageName,
            onAccept = { onAccept(it, scope) },
            onReject = { onReject(it, scope) },
            onChange = { rememberType = it },
            alwaysShow = false,
        )

        AcceptRejectButtons(
            onAccept = {
                onAccept(rememberType, scope)
            },
            onReject = {
                onReject(rememberType, scope)
            },
        )
    }
}

/**
 * Scope for an encrypt/decrypt permission.
 * [SPECIFIC] means the permission applies only to this specific content type (e.g., clear text, tag array, event).
 * [ALL] means the permission applies to all content types for this NIP (e.g., NIP-04 or NIP-44 in full).
 */
enum class DecryptTypeScope {
    SPECIFIC,
    ALL,
}

@Composable
fun BunkerEncryptDecryptData(
    encryptedData: EncryptedDataKind?,
    shouldRunOnAccept: Boolean?,
    appName: String,
    type: SignerType,
    account: Account,
    onAccept: (RememberType, DecryptTypeScope) -> Unit,
    onReject: (RememberType, DecryptTypeScope) -> Unit,
    modifier: Modifier = Modifier,
    defaultScope: DecryptTypeScope = DecryptTypeScope.ALL,
    iconUrl: String = "",
) {
    var rememberType by remember {
        mutableStateOf(RememberType.NEVER)
    }
    var scope by remember { mutableStateOf(defaultScope) }

    Column(
        modifier,
    ) {
        BunkerRequestTitle(iconUrl, appName, type, encryptedData)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors().copy(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        ) {
            Column {
                if (encryptedData is EventEncryptedDataKind) {
                    requestEvent(encryptedData)?.let { BunkerEventContent(shown = it, outer = encryptedData.event) }
                } else {
                    TagArrayOrText(type, encryptedData, account)
                }
            }
        }

        if (encryptedData !is TagArrayEncryptedDataKind) {
            Spacer(Modifier.size(16.dp))
            SigningAs(account)
            Spacer(modifier = Modifier.weight(1f))
        }

        if (type != SignerType.DECRYPT_ZAP_EVENT) {
            Spacer(Modifier.size(8.dp))
            EncryptionScopeToggle(scope, onSelect = { scope = it })
            Spacer(Modifier.size(8.dp))
        }

        RememberMyChoice(
            shouldRunOnAccept,
            null,
            onAccept = { onAccept(it, scope) },
            onReject = { onReject(it, scope) },
            onChange = { rememberType = it },
            alwaysShow = true,
        )

        AcceptRejectButtons(
            onAccept = {
                onAccept(rememberType, scope)
            },
            onReject = {
                onReject(rememberType, scope)
            },
        )
    }
}

@Composable
private fun BunkerRequestTitle(
    iconUrl: String,
    appName: String,
    type: SignerType,
    encryptedData: EncryptedDataKind?,
) {
    Column {
        RemoteAppIcon(iconUrl, appName)
        val prompt = encryptDecryptPrompt(type, encryptedData)
        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(appName)
                }
                if (!type.isEncrypt() && encryptedData is PrivateZapEncryptedDataKind) append(" ")
                append(prompt)
            },
            fontSize = 18.sp,
        )
    }
}

private fun SignerType.isEncrypt(): Boolean = name.contains("ENCRYPT")

private fun SignerType.nipName(): String = name.split("_").first()

private fun requestEvent(encryptedData: EventEncryptedDataKind): AmberEvent? {
    val seal = encryptedData.sealEncryptedDataKind ?: return encryptedData.event
    return (seal as? EventEncryptedDataKind)?.event
}

@Composable
private fun eventKindLabel(event: AmberEvent): String {
    val kindTranslation = Permission("sign_event", event.kind).localizedBlocking()
    val unknownKindString = stringResource(Res.string.event_kind, event.kind.toString())
    val altTag = event.tags.firstOrNull { it.size > 1 && it[0] == "alt" }?.getOrNull(1)
    return if (kindTranslation == unknownKindString && altTag != null) altTag else kindTranslation
}

@Composable
private fun encryptDecryptPrompt(
    type: SignerType,
    encryptedData: EncryptedDataKind?,
): String = if (type.isEncrypt()) {
    when (encryptedData) {
        is EventEncryptedDataKind -> stringResource(Res.string.wants_to_encrypt_with, eventKindLabel(encryptedData.event), type.nipName())
        is TagArrayEncryptedDataKind -> stringResource(Res.string.wants_to_encrypt_this_list_of_tags_with, type.nipName())
        else -> stringResource(Res.string.wants_to_encrypt_this_text_with, type.nipName())
    }
} else {
    when (encryptedData) {
        is EventEncryptedDataKind -> stringResource(Res.string.wants_to_read_from_encrypted_content, eventKindLabel(encryptedData.event), type.nipName())
        is TagArrayEncryptedDataKind -> stringResource(Res.string.wants_to_read_this_list_of_tags_from_encrypted_content, type.nipName())
        is PrivateZapEncryptedDataKind -> stringResource(Res.string.wants_you_to, stringResource(Res.string.decrypt_zap_event))
        else -> stringResource(Res.string.wants_to_read_this_text_from_encrypted_content, type.nipName())
    }
}

@Composable
private fun EventDetailsButton(event: AmberEvent) {
    var showDetails by remember { mutableStateOf(false) }
    RawJsonButton(
        onCLick = { showDetails = !showDetails },
        stringResource(Res.string.show_details),
    )
    if (showDetails) {
        EventDetailModal(event = event.toEvent(), onDismiss = { showDetails = false })
    }
}

@Composable
private fun BunkerEventContent(
    shown: AmberEvent,
    outer: AmberEvent,
) {
    if (outer.kind == ContactListEvent.KIND) {
        ContactListDetail(
            title = stringResource(Res.string.following),
            text = "${outer.verifiedFollowKeySet().size}",
        )
    } else {
        Text(
            truncateForPreview(if (shown.kind == 22242) shown.relay() else shown.content),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

@Composable
private fun ColumnScope.TagArrayOrText(
    type: SignerType,
    encryptedData: EncryptedDataKind?,
    account: Account,
) {
    if (encryptedData is TagArrayEncryptedDataKind) {
        Spacer(Modifier.size(16.dp))
        SigningAs(account)
        EncryptedTagArraySection(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 8.dp),
            tags = encryptedData.tagArray,
        )
    } else {
        Text(
            truncateForPreview(plainCardText(type, encryptedData)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

@Composable
private fun EncryptionScopeToggle(
    scope: DecryptTypeScope,
    onSelect: (DecryptTypeScope) -> Unit,
) {
    LabeledBorderBox(
        label = stringResource(Res.string.encryption_scope),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        AmberToggles(
            selected = scope,
            options = listOf(DecryptTypeScope.SPECIFIC, DecryptTypeScope.ALL),
            onSelect = onSelect,
            label = {
                stringResource(
                    when (it) {
                        DecryptTypeScope.SPECIFIC -> Res.string.for_this_method_only
                        DecryptTypeScope.ALL -> Res.string.for_all_methods
                    },
                )
            },
        )
    }
}

internal fun plainCardText(type: SignerType, encryptedData: EncryptedDataKind?): String = when {
    type.name.contains("ENCRYPT") && encryptedData is ClearTextEncryptedDataKind -> encryptedData.text
    encryptedData is PrivateZapEncryptedDataKind -> encryptedData.message
    else -> encryptedData?.result ?: ""
}

@PreviewsLightDark
@Composable
private fun EncryptDecryptDataPreview() {
    AmberPreview {
        EncryptDecryptData(
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp)
                .padding(16.dp),
            encryptedData = ClearTextEncryptedDataKind("Hello Nostr!", "Hello Nostr!"),
            shouldRunOnAccept = null,
            packageName = null,
            type = SignerType.NIP44_DECRYPT,
            account = previewAccount(),
            onAccept = { _, _ -> },
            onReject = { _, _ -> },
        )
    }
}

@PreviewsLightDark
@Composable
private fun BunkerEncryptDecryptDataPreview() {
    AmberPreview {
        BunkerEncryptDecryptData(
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp)
                .padding(16.dp),
            encryptedData = ClearTextEncryptedDataKind("Hello Nostr!", "Hello Nostr!"),
            shouldRunOnAccept = null,
            appName = "Amethyst",
            type = SignerType.NIP44_ENCRYPT,
            account = previewAccount(),
            onAccept = { _, _ -> },
            onReject = { _, _ -> },
        )
    }
}
