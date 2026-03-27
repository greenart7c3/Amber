package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.PrivateZapEncryptedDataKind
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import com.greenart7c3.nostrsigner.ui.RememberType
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent

@Composable
fun EncryptDecryptData(
    modifier: Modifier,
    encryptedData: EncryptedDataKind?,
    shouldRunOnAccept: Boolean?,
    packageName: String?,
    type: SignerType,
    account: Account,
    defaultScope: DecryptTypeScope = DecryptTypeScope.ALL,
    onAccept: (RememberType, DecryptTypeScope) -> Unit,
    onReject: (RememberType, DecryptTypeScope) -> Unit,
) {
    var rememberType by remember {
        mutableStateOf(RememberType.NEVER)
    }
    var scope by remember { mutableStateOf(defaultScope) }
    val scopeIndex by remember(scope) { mutableIntStateOf(if (scope == DecryptTypeScope.SPECIFIC) 0 else 1) }
    val showScopeToggle = type != SignerType.DECRYPT_ZAP_EVENT

    Column(
        modifier,
    ) {
        LocalAppIcon(packageName)

        val text = if (type.name.contains("ENCRYPT")) {
            when (encryptedData) {
                is EventEncryptedDataKind -> {
                    val permission = Permission("sign_event", encryptedData.event.kind)
                    val kindTranslation = permission.toLocalizedString(Amber.instance)
                    val unknownKindString = stringResource(R.string.event_kind, encryptedData.event.kind.toString())
                    val altTag = encryptedData.event.tags.firstOrNull { it.size > 1 && it[0] == "alt" }?.getOrNull(1)
                    val displayTranslation = if (kindTranslation == unknownKindString && altTag != null) altTag else kindTranslation
                    stringResource(R.string.wants_to_encrypt_with, displayTranslation, type.name.split("_").first())
                }

                is TagArrayEncryptedDataKind -> {
                    stringResource(R.string.wants_to_encrypt_this_list_of_tags_with, type.name.split("_").first())
                }

                else -> stringResource(R.string.wants_to_encrypt_this_text_with, type.name.split("_").first())
            }
        } else {
            when (encryptedData) {
                is EventEncryptedDataKind -> {
                    val permission = Permission("sign_event", encryptedData.event.kind)
                    val kindTranslation = permission.toLocalizedString(Amber.instance)
                    val unknownKindString = stringResource(R.string.event_kind, encryptedData.event.kind.toString())
                    val altTag = encryptedData.event.tags.firstOrNull { it.size > 1 && it[0] == "alt" }?.getOrNull(1)
                    val displayTranslation = if (kindTranslation == unknownKindString && altTag != null) altTag else kindTranslation
                    stringResource(R.string.wants_to_read_from_encrypted_content, displayTranslation, type.name.split("_").first())
                }

                is TagArrayEncryptedDataKind -> {
                    stringResource(R.string.wants_to_read_this_list_of_tags_from_encrypted_content, type.name.split("_").first())
                }

                is PrivateZapEncryptedDataKind -> {
                    stringResource(R.string.wants_you_to, stringResource(R.string.decrypt_zap_event))
                }

                else -> stringResource(R.string.wants_to_read_this_text_from_encrypted_content, type.name.split("_").first())
            }
        }

        Text(
            text.trim().capitalize(Locale.current),
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
                    if (encryptedData.sealEncryptedDataKind != null) {
                        if (encryptedData.sealEncryptedDataKind is EventEncryptedDataKind) {
                            var showDetails by remember {
                                mutableStateOf(false)
                            }
                            RawJsonButton(
                                onCLick = {
                                    showDetails = !showDetails
                                },
                                stringResource(R.string.show_details),
                            )
                            if (showDetails) {
                                EventDetailModal(
                                    encryptedData.sealEncryptedDataKind.event.toEvent(),
                                    {
                                        showDetails = false
                                    },
                                )
                            }
                        }
                    } else {
                        var showDetails by remember {
                            mutableStateOf(false)
                        }
                        RawJsonButton(
                            onCLick = {
                                showDetails = !showDetails
                            },
                            stringResource(R.string.show_details),
                        )
                        if (showDetails) {
                            EventDetailModal(
                                encryptedData.event.toEvent(),
                                {
                                    showDetails = false
                                },
                            )
                        }
                    }
                } else {
                    if (encryptedData is TagArrayEncryptedDataKind) {
                        Spacer(Modifier.size(16.dp))
                        SigningAs(account)
                        EncryptedTagArraySection(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(top = 8.dp),
                            encryptedData.tagArray,
                        )
                    } else {
                        val content = if (type.name.contains("ENCRYPT") && encryptedData is ClearTextEncryptedDataKind) {
                            encryptedData.text
                        } else {
                            encryptedData?.result ?: ""
                        }
                        Text(
                            content,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        if (encryptedData !is TagArrayEncryptedDataKind) {
            Spacer(Modifier.size(16.dp))
            SigningAs(account)
            Spacer(modifier = Modifier.weight(1f))
        }

        if (showScopeToggle) {
            Spacer(Modifier.size(8.dp))
            LabeledBorderBox(
                label = stringResource(R.string.encryption_scope),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                AmberToggles(
                    selectedIndex = scopeIndex,
                    count = 2,
                    segmentWidth = 120.dp,
                    content = {
                        ToggleOption(
                            modifier = Modifier.width(120.dp),
                            text = stringResource(R.string.for_this_method_only),
                            isSelected = scope == DecryptTypeScope.SPECIFIC,
                            onClick = { scope = DecryptTypeScope.SPECIFIC },
                        )
                        ToggleOption(
                            modifier = Modifier.width(120.dp),
                            text = stringResource(R.string.for_all_methods),
                            isSelected = scope == DecryptTypeScope.ALL,
                            onClick = { scope = DecryptTypeScope.ALL },
                        )
                    },
                )
            }
            Spacer(Modifier.size(8.dp))
        }

        RememberMyChoice(
            shouldRunOnAccept,
            packageName,
            false,
            onAccept = { onAccept(it, scope) },
            onReject = { onReject(it, scope) },
        ) {
            rememberType = it
        }

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

data class TagItem(
    val name: String,
    val values: List<String>,
)

@Composable
fun BunkerEncryptDecryptData(
    modifier: Modifier,
    encryptedData: EncryptedDataKind?,
    shouldRunOnAccept: Boolean?,
    appName: String,
    type: SignerType,
    account: Account,
    defaultScope: DecryptTypeScope = DecryptTypeScope.ALL,
    onAccept: (RememberType, DecryptTypeScope) -> Unit,
    onReject: (RememberType, DecryptTypeScope) -> Unit,
) {
    var rememberType by remember {
        mutableStateOf(RememberType.NEVER)
    }
    var scope by remember { mutableStateOf(defaultScope) }
    val scopeIndex by remember(scope) { mutableIntStateOf(if (scope == DecryptTypeScope.SPECIFIC) 0 else 1) }
    val showScopeToggle = type != SignerType.DECRYPT_ZAP_EVENT

    Column(
        modifier,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(appName)
                }
                if (type.name.contains("ENCRYPT")) {
                    when (encryptedData) {
                        is EventEncryptedDataKind -> {
                            val permission = Permission("sign_event", encryptedData.event.kind)
                            val kindTranslation = permission.toLocalizedString(Amber.instance)
                            val unknownKindString = stringResource(R.string.event_kind, encryptedData.event.kind.toString())
                            val altTag = encryptedData.event.tags.firstOrNull { it.size > 1 && it[0] == "alt" }?.getOrNull(1)
                            val displayTranslation = if (kindTranslation == unknownKindString && altTag != null) altTag else kindTranslation
                            append(stringResource(R.string.wants_to_encrypt_with, displayTranslation, type.name.split("_").first()))
                        }

                        is TagArrayEncryptedDataKind -> {
                            append(stringResource(R.string.wants_to_encrypt_this_list_of_tags_with, type.name.split("_").first()))
                        }

                        else -> append(stringResource(R.string.wants_to_encrypt_this_text_with, type.name.split("_").first()))
                    }
                } else {
                    when (encryptedData) {
                        is EventEncryptedDataKind -> {
                            val permission = Permission("sign_event", encryptedData.event.kind)
                            val kindTranslation = permission.toLocalizedString(Amber.instance)
                            val unknownKindString = stringResource(R.string.event_kind, encryptedData.event.kind.toString())
                            val altTag = encryptedData.event.tags.firstOrNull { it.size > 1 && it[0] == "alt" }?.getOrNull(1)
                            val displayTranslation = if (kindTranslation == unknownKindString && altTag != null) altTag else kindTranslation
                            append(stringResource(R.string.wants_to_read_from_encrypted_content, displayTranslation, type.name.split("_").first()))
                        }

                        is TagArrayEncryptedDataKind -> {
                            append(stringResource(R.string.wants_to_read_this_list_of_tags_from_encrypted_content, type.name.split("_").first()))
                        }

                        else -> append(stringResource(R.string.wants_to_read_this_text_from_encrypted_content, type.name.split("_").first()))
                    }
                }
            },
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
                    if (encryptedData.sealEncryptedDataKind != null) {
                        if (encryptedData.sealEncryptedDataKind is EventEncryptedDataKind) {
                            val content = if (encryptedData.sealEncryptedDataKind.event.kind == 22242) encryptedData.sealEncryptedDataKind.event.relay() else encryptedData.sealEncryptedDataKind.event.content
                            if (encryptedData.event.kind == ContactListEvent.KIND) {
                                ContactListDetail(
                                    title = stringResource(R.string.following),
                                    text = "${encryptedData.event.verifiedFollowKeySet().size}",
                                )
                            } else {
                                Text(
                                    content,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                )
                            }
                        }
                    } else {
                        val content = if (encryptedData.event.kind == 22242) encryptedData.event.relay() else encryptedData.event.content
                        if (encryptedData.event.kind == ContactListEvent.KIND) {
                            ContactListDetail(
                                title = stringResource(R.string.following),
                                text = "${encryptedData.event.verifiedFollowKeySet().size}",
                            )
                        } else {
                            Text(
                                content,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                        }
                    }
                } else {
                    if (encryptedData is TagArrayEncryptedDataKind) {
                        Spacer(Modifier.size(16.dp))
                        SigningAs(account)
                        EncryptedTagArraySection(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(top = 8.dp),
                            encryptedData.tagArray,
                        )
                    } else {
                        val content = if (type.name.contains("ENCRYPT") && encryptedData is ClearTextEncryptedDataKind) {
                            encryptedData.text
                        } else {
                            encryptedData?.result ?: ""
                        }
                        Text(
                            content,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        if (encryptedData !is TagArrayEncryptedDataKind) {
            Spacer(Modifier.size(16.dp))
            SigningAs(account)
            Spacer(modifier = Modifier.weight(1f))
        }

        if (showScopeToggle) {
            Spacer(Modifier.size(8.dp))
            LabeledBorderBox(
                label = stringResource(R.string.encryption_scope),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                AmberToggles(
                    selectedIndex = scopeIndex,
                    count = 2,
                    segmentWidth = 120.dp,
                    content = {
                        ToggleOption(
                            modifier = Modifier.width(120.dp),
                            text = stringResource(R.string.for_this_method_only),
                            isSelected = scope == DecryptTypeScope.SPECIFIC,
                            onClick = { scope = DecryptTypeScope.SPECIFIC },
                        )
                        ToggleOption(
                            modifier = Modifier.width(120.dp),
                            text = stringResource(R.string.for_all_methods),
                            isSelected = scope == DecryptTypeScope.ALL,
                            onClick = { scope = DecryptTypeScope.ALL },
                        )
                    },
                )
            }
            Spacer(Modifier.size(8.dp))
        }

        RememberMyChoice(
            shouldRunOnAccept,
            null,
            true,
            onAccept = { onAccept(it, scope) },
            onReject = { onReject(it, scope) },
        ) {
            rememberType = it
        }

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
