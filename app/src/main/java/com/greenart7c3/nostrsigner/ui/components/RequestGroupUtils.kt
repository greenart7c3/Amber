package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.approve
import com.greenart7c3.nostrsigner.commonsui.connect
import com.greenart7c3.nostrsigner.commonsui.decrypt_zap_event
import com.greenart7c3.nostrsigner.commonsui.deny
import com.greenart7c3.nostrsigner.commonsui.encrypt_this_list_of_tags_with
import com.greenart7c3.nostrsigner.commonsui.encrypt_this_text_with
import com.greenart7c3.nostrsigner.commonsui.encrypt_with
import com.greenart7c3.nostrsigner.commonsui.encryption_scope
import com.greenart7c3.nostrsigner.commonsui.for_all_kinds
import com.greenart7c3.nostrsigner.commonsui.for_all_methods
import com.greenart7c3.nostrsigner.commonsui.for_all_relays
import com.greenart7c3.nostrsigner.commonsui.for_this_kind_only
import com.greenart7c3.nostrsigner.commonsui.for_this_method_only
import com.greenart7c3.nostrsigner.commonsui.for_this_relay_only
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.localizedBlocking
import com.greenart7c3.nostrsigner.commonsui.read_from_encrypted_content
import com.greenart7c3.nostrsigner.commonsui.read_this_list_of_tags_from_encrypted_content
import com.greenart7c3.nostrsigner.commonsui.read_this_text_from_encrypted_content
import com.greenart7c3.nostrsigner.commonsui.relay_auth_scope
import com.greenart7c3.nostrsigner.commonsui.remember_my_choice_for
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.PrivateZapEncryptedDataKind
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import com.greenart7c3.nostrsigner.models.encryptDecryptSignerTypes
import com.greenart7c3.nostrsigner.ui.RememberType
import org.jetbrains.compose.resources.stringResource

enum class RequestPayloadShape {
    EVENT,
    TAG_ARRAY,
    CLEAR_TEXT,
    PRIVATE_ZAP,
}

data class RequestGroupKey(
    val type: SignerType,
    val kind: Int?,
    val payload: RequestPayloadShape?,
)

fun requestGroupKey(
    type: SignerType,
    eventKind: Int?,
    encryptedData: EncryptedDataKind?,
    nip44v3Kind: Int?,
): RequestGroupKey = when (type) {
    SignerType.SIGN_EVENT -> RequestGroupKey(type, eventKind, null)
    SignerType.NIP44_V3_ENCRYPT, SignerType.NIP44_V3_DECRYPT -> RequestGroupKey(type, nip44v3Kind, null)
    in encryptDecryptSignerTypes -> when (encryptedData) {
        is EventEncryptedDataKind -> RequestGroupKey(type, encryptedData.event.kind, RequestPayloadShape.EVENT)
        is TagArrayEncryptedDataKind -> RequestGroupKey(type, null, RequestPayloadShape.TAG_ARRAY)
        is ClearTextEncryptedDataKind -> RequestGroupKey(type, null, RequestPayloadShape.CLEAR_TEXT)
        is PrivateZapEncryptedDataKind -> RequestGroupKey(type, null, RequestPayloadShape.PRIVATE_ZAP)
        else -> RequestGroupKey(type, null, null)
    }
    else -> RequestGroupKey(type, null, null)
}

fun <T> groupRequests(
    items: List<T>,
    keyOf: (T) -> RequestGroupKey,
): List<Pair<RequestGroupKey, List<T>>> = items.groupBy(keyOf).toList()
    .sortedWith(
        compareBy(
            { it.first.type.ordinal },
            { it.first.payload?.ordinal ?: -1 },
            { it.first.kind ?: -1 },
        ),
    )

fun RequestGroupKey.toLabel(): String {
    val isEncrypt = type.name.contains("ENCRYPT")
    val nip = type.name.split("_").first()
    return when {
        type == SignerType.CONNECT -> getStringBlocking(Res.string.connect)
        type == SignerType.SIGN_EVENT -> Permission("sign_event", kind).localizedBlocking()
        payload == RequestPayloadShape.EVENT -> {
            val kindLabel = Permission("sign_event", kind).localizedBlocking()
            if (isEncrypt) {
                getStringBlocking(Res.string.encrypt_with, kindLabel, nip)
            } else {
                getStringBlocking(Res.string.read_from_encrypted_content, kindLabel, nip)
            }
        }
        payload == RequestPayloadShape.TAG_ARRAY -> if (isEncrypt) {
            getStringBlocking(Res.string.encrypt_this_list_of_tags_with, nip)
        } else {
            getStringBlocking(Res.string.read_this_list_of_tags_from_encrypted_content, nip)
        }
        payload == RequestPayloadShape.CLEAR_TEXT -> if (isEncrypt) {
            getStringBlocking(Res.string.encrypt_this_text_with, nip)
        } else {
            getStringBlocking(Res.string.read_this_text_from_encrypted_content, nip)
        }
        payload == RequestPayloadShape.PRIVATE_ZAP -> getStringBlocking(Res.string.decrypt_zap_event).replaceFirstChar { it.uppercase() }
        (type == SignerType.NIP44_V3_ENCRYPT || type == SignerType.NIP44_V3_DECRYPT) && kind != null -> {
            val kindLabel = Permission("sign_event", kind).localizedBlocking()
            if (isEncrypt) {
                getStringBlocking(Res.string.encrypt_with, kindLabel, nip)
            } else {
                getStringBlocking(Res.string.read_from_encrypted_content, kindLabel, nip)
            }
        }
        else -> Permission(type.name.lowercase(), null).localizedBlocking()
    }
}

enum class RequestGroupScopeKind {
    NONE,
    RELAY_AUTH,
    ENCRYPTION_METHOD,
    NIP44_V3_KIND,
}

fun RequestGroupKey.scopeKind(): RequestGroupScopeKind = when (type) {
    SignerType.SIGN_EVENT if kind == 22242 -> RequestGroupScopeKind.RELAY_AUTH
    SignerType.NIP44_V3_ENCRYPT, SignerType.NIP44_V3_DECRYPT -> RequestGroupScopeKind.NIP44_V3_KIND
    SignerType.DECRYPT_ZAP_EVENT -> RequestGroupScopeKind.NONE
    in encryptDecryptSignerTypes -> RequestGroupScopeKind.ENCRYPTION_METHOD
    else -> RequestGroupScopeKind.NONE
}

// Mirrors the single-event defaults: EncryptDecryptData defaults to ALL, Nip44v3ApprovalData to SPECIFIC
fun defaultDecryptTypeScope(type: SignerType): DecryptTypeScope = if (type == SignerType.NIP44_V3_ENCRYPT || type == SignerType.NIP44_V3_DECRYPT) {
    DecryptTypeScope.SPECIFIC
} else {
    DecryptTypeScope.ALL
}

// CONNECT / GET_PUBLIC_KEY approvals never persist a permission from the grouped flow
fun RequestGroupKey.hasGroupOptions(): Boolean = type != SignerType.CONNECT && type != SignerType.GET_PUBLIC_KEY

@Composable
fun RequestGroupOptions(
    groupKey: RequestGroupKey,
    rememberType: RememberType,
    onRememberTypeChanged: (RememberType) -> Unit,
    relayAuthScope: RelayAuthScope,
    onRelayAuthScopeChanged: (RelayAuthScope) -> Unit,
    decryptTypeScope: DecryptTypeScope,
    onDecryptTypeScopeChanged: (DecryptTypeScope) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        when (groupKey.scopeKind()) {
            RequestGroupScopeKind.RELAY_AUTH -> {
                LabeledBorderBox(
                    label = stringResource(Res.string.relay_auth_scope),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    AmberToggles(
                        selected = relayAuthScope,
                        options = listOf(RelayAuthScope.SPECIFIC, RelayAuthScope.ALL),
                        onSelected = onRelayAuthScopeChanged,
                        label = {
                            stringResource(
                                when (it) {
                                    RelayAuthScope.SPECIFIC -> Res.string.for_this_relay_only
                                    RelayAuthScope.ALL -> Res.string.for_all_relays
                                },
                            )
                        },
                    )
                }
            }
            RequestGroupScopeKind.ENCRYPTION_METHOD -> {
                LabeledBorderBox(
                    label = stringResource(Res.string.encryption_scope),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    AmberToggles(
                        selected = decryptTypeScope,
                        options = listOf(DecryptTypeScope.SPECIFIC, DecryptTypeScope.ALL),
                        onSelected = onDecryptTypeScopeChanged,
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
            RequestGroupScopeKind.NIP44_V3_KIND -> {
                if (groupKey.kind != null) {
                    LabeledBorderBox(
                        label = stringResource(Res.string.encryption_scope),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        AmberToggles(
                            selected = decryptTypeScope,
                            options = listOf(DecryptTypeScope.SPECIFIC, DecryptTypeScope.ALL),
                            onSelected = onDecryptTypeScopeChanged,
                            label = {
                                stringResource(
                                    when (it) {
                                        DecryptTypeScope.SPECIFIC -> Res.string.for_this_kind_only
                                        DecryptTypeScope.ALL -> Res.string.for_all_kinds
                                    },
                                )
                            },
                        )
                    }
                }
            }
            RequestGroupScopeKind.NONE -> {}
        }

        RememberMyChoiceToggles(
            selected = rememberType,
            onSelected = onRememberTypeChanged,
            label = stringResource(Res.string.remember_my_choice_for),
        )
    }
}

@Composable
fun RequestGroupHeader(
    label: String,
    count: Int,
    approved: Boolean,
    expanded: Boolean,
    onApproveChanged: (Boolean) -> Unit,
    onExpandToggle: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "group header chevron rotation",
    )
    Column(
        Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandToggle() },
        ) {
            Text(
                text = "$label ($count)",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .rotate(rotation),
            )
        }

        AmberToggles(
            selected = approved,
            options = listOf(true, false),
            onSelected = onApproveChanged,
            label = {
                if (it) stringResource(Res.string.approve) else stringResource(Res.string.deny)
            },
            indicatorColor = {
                if (it) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            },
            selectedTextColor = {
                if (it) Color.Black else MaterialTheme.colorScheme.onError
            },
        )
    }
}
