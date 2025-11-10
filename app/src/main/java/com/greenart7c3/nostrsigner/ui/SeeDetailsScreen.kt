package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.service.BunkerRequestUtils
import com.greenart7c3.nostrsigner.service.MultiEventScreenIntents
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.ui.components.RememberMyChoice
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign

@Composable
fun SeeDetailsScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        var rememberType by remember { mutableStateOf(MultiEventScreenIntents.intents.firstOrNull()?.rememberType?.value ?: MultiEventScreenIntents.bunkerRequests.first().rememberType.value) }
        val type = if (MultiEventScreenIntents.intents.isNotEmpty()) {
            MultiEventScreenIntents.intents.first().type
        } else {
            BunkerRequestUtils.getTypeFromBunker(MultiEventScreenIntents.bunkerRequests.first().request)
        }
        val permission = if (type == SignerType.SIGN_EVENT) {
            val event = if (MultiEventScreenIntents.intents.isNotEmpty()) {
                MultiEventScreenIntents.intents.first().event!!
            } else {
                MultiEventScreenIntents.bunkerRequests.first().signedEvent!!
            }

            Permission("sign_event", event.kind)
        } else {
            Permission(type.toString().toLowerCase(Locale.current), null)
        }

        val message = if (type == SignerType.CONNECT) {
            stringResource(R.string.connect)
        } else {
            permission.toLocalizedString(context)
        }
        Text(
            stringResource(R.string.is_requiring_to_sign_these_events_related_to_permission, MultiEventScreenIntents.appName, message),
            Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
        )

        Row(
            Modifier.padding(vertical = 8.dp),
        ) {
            RememberMyChoice(
                alwaysShow = true,
                shouldRunAcceptOrReject = null,
                onAccept = {},
                onReject = {},
                onChanged = {
                    rememberType = it
                    MultiEventScreenIntents.intents.forEach { intent ->
                        intent.rememberType.value = rememberType
                    }
                },
                packageName = null,
            )
        }

        MultiEventScreenIntents.intents.forEach { intent ->
            Card(
                Modifier
                    .padding(4.dp),
                colors = CardDefaults.cardColors().copy(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                border = BorderStroke(1.dp, Color.Gray),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            intent.checked.value = !intent.checked.value
                        },
                ) {
                    Checkbox(
                        checked = intent.checked.value,
                        onCheckedChange = { _ ->
                            intent.checked.value = !intent.checked.value
                        },
                        colors = CheckboxDefaults.colors().copy(
                            uncheckedBorderColor = Color.Gray,
                        ),
                    )

                    val data = if (intent.type == SignerType.SIGN_EVENT) {
                        val event = intent.event!!
                        if (event.kind == 22242) AmberEvent.relay(event) else event.content
                    } else {
                        if (type.name.contains("ENCRYPT") && intent.encryptedData is ClearTextEncryptedDataKind) {
                            intent.encryptedData.text
                        } else if (intent.encryptedData is EventEncryptedDataKind) {
                            if (intent.encryptedData.sealEncryptedDataKind != null) {
                                if (intent.encryptedData.sealEncryptedDataKind is EventEncryptedDataKind) {
                                    intent.encryptedData.sealEncryptedDataKind.event.content
                                } else {
                                    intent.encryptedData.sealEncryptedDataKind.result
                                }
                            } else {
                                intent.encryptedData.event.content
                            }
                        } else {
                            intent.encryptedData?.result ?: ""
                        }
                    }

                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        text = data.ifBlank { message },
                        color = if (intent.checked.value) Color.Unspecified else Color.Gray,
                    )
                }
            }
        }

        MultiEventScreenIntents.bunkerRequests.forEach { bunkerRequest ->
            Card(
                Modifier
                    .padding(4.dp),
                colors = CardDefaults.cardColors().copy(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                border = BorderStroke(1.dp, Color.Gray),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            bunkerRequest.checked.value = !bunkerRequest.checked.value
                        },
                ) {
                    Checkbox(
                        checked = bunkerRequest.checked.value,
                        onCheckedChange = { _ ->
                            bunkerRequest.checked.value = !bunkerRequest.checked.value
                        },
                        colors = CheckboxDefaults.colors().copy(
                            uncheckedBorderColor = Color.Gray,
                        ),
                    )

                    val data = if (bunkerRequest.request is BunkerRequestSign) {
                        val event = bunkerRequest.signedEvent!!
                        if (event.kind == 22242) AmberEvent.relay(event) else event.content
                    } else {
                        if (type.name.contains("ENCRYPT") && bunkerRequest.encryptedData is ClearTextEncryptedDataKind) {
                            bunkerRequest.encryptedData.text
                        } else if (bunkerRequest.encryptedData is EventEncryptedDataKind) {
                            if (bunkerRequest.encryptedData.sealEncryptedDataKind != null) {
                                if (bunkerRequest.encryptedData.sealEncryptedDataKind is EventEncryptedDataKind) {
                                    bunkerRequest.encryptedData.sealEncryptedDataKind.event.content
                                } else {
                                    bunkerRequest.encryptedData.sealEncryptedDataKind.result
                                }
                            } else {
                                bunkerRequest.encryptedData.event.content
                            }
                        } else {
                            bunkerRequest.encryptedData?.result ?: BunkerRequestUtils.getDataFromBunker(bunkerRequest.request)
                        }
                    }

                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        text = data.ifBlank { message },
                        color = if (bunkerRequest.checked.value) Color.Unspecified else Color.Gray,
                    )
                }
            }
        }
    }
}
