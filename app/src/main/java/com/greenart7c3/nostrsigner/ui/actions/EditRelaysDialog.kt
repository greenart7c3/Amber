package com.greenart7c3.nostrsigner.ui.actions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.ui.components.CloseButton
import com.greenart7c3.nostrsigner.ui.components.PostButton
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.RelaySetupInfo

@Composable
fun EditRelaysDialog(
    applicationData: ApplicationEntity,
    onClose: () -> Unit,
    onPost: (SnapshotStateList<RelaySetupInfo>) -> Unit,
) {
    var textFieldRelay by remember {
        mutableStateOf(TextFieldValue(""))
    }
    val relays2 =
        remember {
            val localRelays = mutableStateListOf<RelaySetupInfo>()
            applicationData.relays.forEach {
                localRelays.add(
                    it.copy(),
                )
            }
            localRelays
        }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CloseButton {
                        onClose()
                    }
                    PostButton(
                        isActive = !applicationData.isConnected,
                        onPost = {
                            onPost(relays2)
                        },
                    )
                }
                LazyColumn(
                    Modifier
                        .fillMaxHeight(0.9f)
                        .fillMaxWidth(),
                ) {
                    items(relays2.size) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                relays2[it].url,
                                Modifier
                                    .weight(0.9f)
                                    .padding(8.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(
                                onClick = {
                                    relays2.removeAt(it)
                                },
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    stringResource(R.string.delete),
                                )
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        enabled = !applicationData.isConnected,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(horizontal = 16.dp),
                        value = textFieldRelay.text,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                        ),
                        onValueChange = {
                            textFieldRelay = TextFieldValue(it)
                        },
                        label = {
                            Text("Relay")
                        },
                    )
                    IconButton(
                        onClick = {
                            if (applicationData.isConnected) return@IconButton
                            val url = textFieldRelay.text
                            if (url.isNotBlank() && url != "/") {
                                var addedWSS =
                                    if (!url.startsWith("wss://") && !url.startsWith("ws://")) {
                                        if (url.endsWith(".onion") || url.endsWith(".onion/")) {
                                            "ws://$url"
                                        } else {
                                            "wss://$url"
                                        }
                                    } else {
                                        url
                                    }
                                if (url.endsWith("/")) addedWSS = addedWSS.dropLast(1)
                                relays2.add(
                                    RelaySetupInfo(
                                        addedWSS,
                                        read = true,
                                        write = true,
                                        feedTypes = COMMON_FEED_TYPES,
                                    ),
                                )
                                textFieldRelay = TextFieldValue("")
                            }
                        },
                    ) {
                        Icon(
                            Icons.Default.Add,
                            null,
                        )
                    }
                }
            }
        }
    }
}
