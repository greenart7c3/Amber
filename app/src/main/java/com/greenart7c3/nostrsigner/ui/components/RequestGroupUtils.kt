package com.greenart7c3.nostrsigner.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.PrivateZapEncryptedDataKind
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import com.greenart7c3.nostrsigner.models.encryptDecryptSignerTypes

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
): RequestGroupKey = when {
    type == SignerType.SIGN_EVENT -> RequestGroupKey(type, eventKind, null)
    type == SignerType.NIP44_V3_ENCRYPT || type == SignerType.NIP44_V3_DECRYPT -> RequestGroupKey(type, nip44v3Kind, null)
    type in encryptDecryptSignerTypes -> when (encryptedData) {
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

fun RequestGroupKey.toLabel(context: Context): String {
    val isEncrypt = type.name.contains("ENCRYPT")
    val nip = type.name.split("_").first()
    return when {
        type == SignerType.CONNECT -> context.getString(R.string.connect)
        type == SignerType.SIGN_EVENT -> Permission("sign_event", kind).toLocalizedString(context)
        payload == RequestPayloadShape.EVENT -> {
            val kindLabel = Permission("sign_event", kind).toLocalizedString(context)
            if (isEncrypt) {
                context.getString(R.string.encrypt_with, kindLabel, nip)
            } else {
                context.getString(R.string.read_from_encrypted_content, kindLabel, nip)
            }
        }
        payload == RequestPayloadShape.TAG_ARRAY -> if (isEncrypt) {
            context.getString(R.string.encrypt_this_list_of_tags_with, nip)
        } else {
            context.getString(R.string.read_this_list_of_tags_from_encrypted_content, nip)
        }
        payload == RequestPayloadShape.CLEAR_TEXT -> if (isEncrypt) {
            context.getString(R.string.encrypt_this_text_with, nip)
        } else {
            context.getString(R.string.read_this_text_from_encrypted_content, nip)
        }
        payload == RequestPayloadShape.PRIVATE_ZAP -> context.getString(R.string.decrypt_zap_event).replaceFirstChar { it.uppercase() }
        (type == SignerType.NIP44_V3_ENCRYPT || type == SignerType.NIP44_V3_DECRYPT) && kind != null -> {
            val kindLabel = Permission("sign_event", kind).toLocalizedString(context)
            if (isEncrypt) {
                context.getString(R.string.encrypt_with, kindLabel, nip)
            } else {
                context.getString(R.string.read_from_encrypted_content, kindLabel, nip)
            }
        }
        else -> Permission(type.name.lowercase(), null).toLocalizedString(context)
    }
}

@Composable
fun RequestGroupHeader(
    label: String,
    count: Int,
    state: ToggleableState,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
    ) {
        TriStateCheckbox(
            state = state,
            onClick = onToggle,
        )
        Text(
            text = "$label ($count)",
            style = MaterialTheme.typography.titleSmall,
        )
    }
}
