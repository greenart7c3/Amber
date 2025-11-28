package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
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
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import com.greenart7c3.nostrsigner.ui.RememberType
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent

@Composable
fun EncryptDecryptData(
    account: Account,
    modifier: Modifier,
    encryptedData: EncryptedDataKind?,
    shouldRunOnAccept: Boolean?,
    packageName: String?,
    applicationName: String?,
    appName: String,
    type: SignerType,
    onAccept: (RememberType) -> Unit,
    onReject: (RememberType) -> Unit,
) {
    var rememberType by remember {
        mutableStateOf(RememberType.NEVER)
    }

    Column(
        modifier,
    ) {
        ProfilePicture(account)

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

                if (type.name.contains("ENCRYPT")) {
                    when (encryptedData) {
                        is EventEncryptedDataKind -> {
                            val permission = Permission("sign_event", encryptedData.event.kind)
                            append(stringResource(R.string.wants_to_encrypt_with, permission.toLocalizedString(Amber.instance), type.name.split("_").first()))
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
                            append(stringResource(R.string.wants_to_read_from_encrypted_content, permission.toLocalizedString(Amber.instance), type.name.split("_").first()))
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
        Spacer(Modifier.size(4.dp))

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
                        val mappedTags = encryptedData.tagArray.mapNotNull { tag ->
                            if (tag.isEmpty()) return@mapNotNull null

                            TagItem(
                                name = tag.first(),
                                values = tag.drop(1),
                            )
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(top = 8.dp),
                        ) {
                            items(mappedTags) { item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                ) {
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp),
                                    )

                                    item.values.forEach { value ->
                                        Text(
                                            fontSize = 16.sp,
                                            text = value,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 2.dp),
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
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

        if (encryptedData !is TagArrayEncryptedDataKind) Spacer(modifier = Modifier.weight(1f))

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

data class TagItem(
    val name: String,
    val values: List<String>,
)

@Composable
fun BunkerEncryptDecryptData(
    account: Account,
    modifier: Modifier,
    encryptedData: EncryptedDataKind?,
    shouldRunOnAccept: Boolean?,
    appName: String,
    type: SignerType,
    onAccept: (RememberType) -> Unit,
    onReject: (RememberType) -> Unit,
) {
    var rememberType by remember {
        mutableStateOf(RememberType.NEVER)
    }

    Column(
        modifier,
    ) {
        ProfilePicture(account)

        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(appName)
                }
                if (type.name.contains("ENCRYPT")) {
                    when (encryptedData) {
                        is EventEncryptedDataKind -> {
                            val permission = Permission("sign_event", encryptedData.event.kind)
                            append(stringResource(R.string.wants_to_encrypt_with, permission.toLocalizedString(Amber.instance), type.name.split("_").first()))
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
                            append(stringResource(R.string.wants_to_read_from_encrypted_content, permission.toLocalizedString(Amber.instance), type.name.split("_").first()))
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
        Spacer(Modifier.size(4.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            Column(Modifier.padding(6.dp)) {
                Text(
                    stringResource(R.string.content).capitalize(Locale.current),
                    fontWeight = FontWeight.Bold,
                )

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
                    val content = if (type.name.contains("ENCRYPT") && encryptedData is ClearTextEncryptedDataKind) {
                        encryptedData.text
                    } else if (encryptedData is TagArrayEncryptedDataKind) {
                        encryptedData.tagArray.map { it.toList() }.toList().toString()
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

        Spacer(modifier = Modifier.weight(1f))

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
