package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.localizedBlocking
import com.greenart7c3.nostrsigner.commonsui.tags
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import org.jetbrains.compose.resources.stringResource

/**
 * What an encrypt or decrypt request carries: an event (the sealed one, for a gift wrap),
 * a list of tags, or text (the text to encrypt, or the decrypted result).
 */
@Composable
fun EncryptDecryptDetailModal(
    type: SignerType,
    encryptedData: EncryptedDataKind?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copyToClipboard = rememberCopyToClipboard()
    DetailSheet(onDismiss = onDismiss, modifier = modifier) {
        when (encryptedData) {
            is EventEncryptedDataKind -> {
                val event = if (encryptedData.sealEncryptedDataKind is EventEncryptedDataKind) {
                    encryptedData.sealEncryptedDataKind.event.toEvent()
                } else {
                    encryptedData.event.toEvent()
                }
                EventSections(event = event, kindLabel = Permission("sign_event", event.kind).localizedBlocking())
            }
            is TagArrayEncryptedDataKind -> {
                TagsSection(
                    label = stringResource(Res.string.tags),
                    tags = encryptedData.tagArray,
                    onCopy = { copyToClipboard(tagsCopyText(encryptedData.tagArray)) },
                )
            }
            else -> {
                val content = if (type.name.contains("ENCRYPT") && encryptedData is ClearTextEncryptedDataKind) {
                    encryptedData.text
                } else {
                    encryptedData?.result ?: ""
                }
                Text(
                    content,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
        }
    }
}
