package com.greenart7c3.nostrsigner.ui.actions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.ui.components.CloseButton
import com.greenart7c3.nostrsigner.ui.components.PostButton
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.RelayPool
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

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

@Composable
fun EditDefaultRelaysDialog(
    onClose: () -> Unit,
    onPost: (SnapshotStateList<RelaySetupInfo>) -> Unit,
) {
    val context = LocalContext.current
    var textFieldRelay by remember {
        mutableStateOf(TextFieldValue(""))
    }
    val relays2 =
        remember {
            val localRelays = mutableStateListOf<RelaySetupInfo>()
            LocalPreferences.getDefaultRelays(context).forEach {
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
                        isActive = relays2.isNotEmpty(),
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
                            val url = textFieldRelay.text
                            if (url.isNotBlank() && url != "/") {
                                var addedWSS =
                                    if (!url.startsWith("wss://") && !url.startsWith("ws://")) {
                                        // TODO: How to identify relays on the local network?
                                        val isLocalHost = url.contains("127.0.0.1") || url.contains("localhost")
                                        if (url.endsWith(".onion") || url.endsWith(".onion/") || isLocalHost) {
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

@Composable
fun RelayLogDialog(
    url: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(10.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CloseButton(
                        onCancel = onClose,
                    )
                }

                val flows = LocalPreferences.allSavedAccounts(context).map {
                    NostrSigner.getInstance().getDatabase(it.npub).applicationDao().getLogsByUrl(url)
                }.merge()

                val logs = flows.collectAsStateWithLifecycle(initialValue = emptyList())

                LazyColumn(
                    Modifier.weight(1f),
                ) {
                    itemsIndexed(logs.value) { _, log ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .padding(6.dp),
                        ) {
                            Column(Modifier.padding(6.dp)) {
                                Text(
                                    buildAnnotatedString {
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append("Date: ")
                                        }
                                        append(TimeUtils.convertLongToDateTime(log.time))
                                    },
                                )
                                Text(
                                    buildAnnotatedString {
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append("URL: ")
                                        }
                                        append(log.url)
                                    },
                                )
                                Text(
                                    buildAnnotatedString {
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append("Type: ")
                                        }
                                        append(log.type)
                                    },
                                )
                                Text(
                                    buildAnnotatedString {
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append("Message: ")
                                        }
                                        append(log.message)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveRelaysDialog(
    onClose: () -> Unit,
) {
    val relays2 =
        remember {
            mutableStateListOf<RelaySetupInfo>()
        }

    var relayLogUrl by remember {
        mutableStateOf("")
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            relays2.addAll(NostrSigner.getInstance().getSavedRelays())
        }
    }

    if (relayLogUrl.isNotBlank()) {
        RelayLogDialog(
            url = relayLogUrl,
            onClose = {
                relayLogUrl = ""
            },
        )
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
                }
                LazyColumn(
                    Modifier
                        .fillMaxHeight(0.9f)
                        .fillMaxWidth(),
                ) {
                    items(relays2.size) {
                        Row(
                            Modifier
                                .padding(6.dp)
                                .clickable {
                                    relayLogUrl = relays2[it].url
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Circle,
                                contentDescription = "Active Relay",
                                tint = if (RelayPool.getRelay(relays2[it].url)?.isConnected() == true) Color.Green else Color.Red,
                            )
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
                        }
                    }
                }
            }
        }
    }
}
