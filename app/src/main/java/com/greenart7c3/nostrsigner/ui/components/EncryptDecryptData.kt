package com.greenart7c3.nostrsigner.ui.components

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.ui.RememberType
import kotlinx.coroutines.launch

@Composable
fun EncryptDecryptData(
    account: Account,
    modifier: Modifier,
    content: String,
    encryptedData: String,
    shouldRunOnAccept: Boolean?,
    packageName: String?,
    applicationName: String?,
    appName: String,
    type: SignerType,
    onAccept: (RememberType) -> Unit,
    onReject: (RememberType) -> Unit,
) {
    var showMore by androidx.compose.runtime.remember {
        mutableStateOf(false)
    }
    val clipboardManager = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var rememberType by remember {
        mutableStateOf(RememberType.NEVER)
    }

    Column(
        modifier,
    ) {
        ProfilePicture(account)

        val message =
            when (type) {
                SignerType.NIP44_ENCRYPT -> stringResource(R.string.encrypt_nip44)
                SignerType.NIP04_ENCRYPT -> stringResource(R.string.encrypt_nip04)
                SignerType.NIP44_DECRYPT -> stringResource(R.string.decrypt_nip44)
                SignerType.NIP04_DECRYPT -> stringResource(R.string.decrypt_nip04)
                SignerType.DECRYPT_ZAP_EVENT -> stringResource(R.string.decrypt_zap_event)
                else -> stringResource(R.string.encrypt_decrypt)
            }

        packageName?.let {
            Text(
                modifier = Modifier
                    .fillMaxWidth(),
                text = it,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(4.dp))
        }

        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(applicationName ?: appName)
                }
                append(" requests $message")
            },
            fontSize = 18.sp,
        )
        Spacer(Modifier.size(4.dp))
        if (!type.toString().contains("DECRYPT")) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                Column(Modifier.padding(6.dp)) {
                    Text(
                        "Event content",
                        fontWeight = FontWeight.Bold,
                    )

                    Text(
                        content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
        }
        RawJsonButton(
            onCLick = {
                showMore = !showMore
            },
            if (!showMore) stringResource(R.string.show_details) else stringResource(R.string.hide_details),
        )
        if (showMore) {
            RawJson(
                content,
                encryptedData,
                Modifier.height(200.dp),
                stringResource(R.string.encrypted_decrypted_data),
                type,
            ) {
                coroutineScope.launch {
                    clipboardManager.setClipEntry(
                        ClipEntry(
                            ClipData.newPlainText("", encryptedData),
                        ),
                    )

                    Toast.makeText(
                        context,
                        context.getString(R.string.data_copied_to_the_clipboard),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        RememberMyChoice(
            shouldRunOnAccept,
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
fun BunkerEncryptDecryptData(
    account: Account,
    modifier: Modifier,
    content: String,
    encryptedData: String,
    shouldRunOnAccept: Boolean?,
    appName: String,
    type: SignerType,
    onAccept: (RememberType) -> Unit,
    onReject: (RememberType) -> Unit,
) {
    var showMore by androidx.compose.runtime.remember {
        mutableStateOf(false)
    }
    val clipboardManager = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var rememberType by remember {
        mutableStateOf(RememberType.NEVER)
    }

    Column(
        modifier,
    ) {
        ProfilePicture(account)

        val message =
            when (type) {
                SignerType.NIP44_ENCRYPT -> stringResource(R.string.encrypt_nip44)
                SignerType.NIP04_ENCRYPT -> stringResource(R.string.encrypt_nip04)
                SignerType.NIP44_DECRYPT -> stringResource(R.string.decrypt_nip44)
                SignerType.NIP04_DECRYPT -> stringResource(R.string.decrypt_nip04)
                SignerType.DECRYPT_ZAP_EVENT -> stringResource(R.string.decrypt_zap_event)
                else -> stringResource(R.string.encrypt_decrypt)
            }

        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(appName)
                }
                append(" requests $message")
            },
            fontSize = 18.sp,
        )
        Spacer(Modifier.size(4.dp))
        if (!type.toString().contains("DECRYPT")) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                Column(Modifier.padding(6.dp)) {
                    Text(
                        "Event content",
                        fontWeight = FontWeight.Bold,
                    )

                    Text(
                        content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
        }
        RawJsonButton(
            onCLick = {
                showMore = !showMore
            },
            if (!showMore) stringResource(R.string.show_details) else stringResource(R.string.hide_details),
        )
        if (showMore) {
            RawJson(
                content,
                encryptedData,
                Modifier.height(200.dp),
                stringResource(R.string.encrypted_decrypted_data),
                type,
            ) {
                coroutineScope.launch {
                    clipboardManager.setClipEntry(
                        ClipEntry(
                            ClipData.newPlainText("", encryptedData),
                        ),
                    )

                    Toast.makeText(
                        context,
                        context.getString(R.string.data_copied_to_the_clipboard),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        RememberMyChoice(
            shouldRunOnAccept,
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
