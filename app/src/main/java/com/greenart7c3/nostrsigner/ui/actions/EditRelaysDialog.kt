package com.greenart7c3.nostrsigner.ui.actions

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.service.Nip11Retriever
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.CenterCircularProgressIndicator
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.Client
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.RelayPool
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.RelayUrlFormatter
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.signers.NostrSignerInternal
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DefaultRelaysScreen(
    modifier: Modifier,
    accountStateViewModel: AccountStateViewModel,
    account: Account,
    navController: NavController,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val textFieldRelay = remember {
        mutableStateOf(TextFieldValue(""))
    }
    val isLoading = remember {
        mutableStateOf(false)
    }
    val relays2 =
        remember {
            val localRelays = mutableStateListOf<RelaySetupInfo>()
            NostrSigner.getInstance().settings.defaultRelays.forEach {
                localRelays.add(
                    it.copy(),
                )
            }
            localRelays
        }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize(),
    ) {
        if (isLoading.value) {
            CenterCircularProgressIndicator(Modifier)
        } else {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize(),
            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    value = textFieldRelay.value.text,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    onValueChange = {
                        textFieldRelay.value = TextFieldValue(it)
                    },
                    keyboardActions = KeyboardActions(
                        onDone = {
                            scope.launch(Dispatchers.IO) {
                                onAddRelay(
                                    textFieldRelay,
                                    isLoading,
                                    relays2,
                                    scope,
                                    accountStateViewModel,
                                    account,
                                    context,
                                    onDone = {
                                        NostrSigner.getInstance().settings = NostrSigner.getInstance().settings.copy(
                                            defaultRelays = relays2,
                                        )
                                        LocalPreferences.saveSettingsToEncryptedStorage(NostrSigner.getInstance().settings)
                                    },
                                )
                            }
                        },
                    ),
                    label = {
                        Text("Relay")
                    },
                )

                AmberButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            onAddRelay(
                                textFieldRelay,
                                isLoading,
                                relays2,
                                scope,
                                accountStateViewModel,
                                account,
                                context,
                                onDone = {
                                    NostrSigner.getInstance().settings = NostrSigner.getInstance().settings.copy(
                                        defaultRelays = relays2,
                                    )
                                    LocalPreferences.saveSettingsToEncryptedStorage(NostrSigner.getInstance().settings)
                                },
                            )
                        }
                    },
                    content = {
                        Text(stringResource(R.string.add))
                    },
                )

                LazyColumn(
                    Modifier
                        .weight(1f),
                ) {
                    items(relays2.size) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                relays2[it].url,
                                Modifier
                                    .weight(0.9f)
                                    .padding(16.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(
                                onClick = {
                                    relays2.removeAt(it)
                                    NostrSigner.getInstance().settings = NostrSigner.getInstance().settings.copy(
                                        defaultRelays = relays2,
                                    )
                                    LocalPreferences.saveSettingsToEncryptedStorage(NostrSigner.getInstance().settings)
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
            }
        }
    }
}

suspend fun onAddRelay(
    textFieldRelay: MutableState<TextFieldValue>,
    isLoading: MutableState<Boolean>,
    relays2: SnapshotStateList<RelaySetupInfo>,
    scope: CoroutineScope,
    accountStateViewModel: AccountStateViewModel,
    account: Account,
    context: Context,
    onDone: () -> Unit,
) {
    val url = textFieldRelay.value.text
    if (url.isNotBlank() && url != "/") {
        isLoading.value = true
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
        if (relays2.any { it.url == addedWSS }) {
            textFieldRelay.value = TextFieldValue("")
            isLoading.value = false
            return
        }
        val httpsUrl = RelayUrlFormatter.getHttpsUrl(addedWSS)
        val retriever = Nip11Retriever()
        scope.launch(Dispatchers.IO) {
            retriever.loadRelayInfo(
                httpsUrl,
                addedWSS,
                forceProxy = account.useProxy,
                onInfo = { info ->
                    scope.launch(Dispatchers.IO) secondLaunch@{
                        if (info.limitation?.payment_required == true) {
                            accountStateViewModel.toast(
                                context.getString(R.string.relay),
                                context.getString(R.string.paid_relays_are_not_supported),
                            )
                            textFieldRelay.value = TextFieldValue("")
                            isLoading.value = false
                            return@secondLaunch
                        }

                        if (info.limitation?.auth_required == true) {
                            accountStateViewModel.toast(
                                context.getString(R.string.relay),
                                context.getString(R.string.auth_required_message),
                            )
                            textFieldRelay.value = TextFieldValue("")
                            isLoading.value = false
                            return@secondLaunch
                        }

                        val signer = NostrSignerInternal(KeyPair())
                        val encryptedContent = signer.signerSync.nip04Encrypt(
                            "Test bunker event",
                            signer.keyPair.pubKey.toHexKey(),
                        )
                        encryptedContent?.let {
                            val event = signer.signerSync.sign<Event>(
                                TimeUtils.now(),
                                24133,
                                arrayOf(arrayOf("p", signer.keyPair.pubKey.toHexKey())),
                                it,
                            )

                            event?.let { signedEvent ->
                                val relay = Relay(
                                    addedWSS,
                                    read = true,
                                    write = true,
                                    activeTypes = setOf(),
                                )
                                RelayPool.addRelay(
                                    relay,
                                )
                                relay.connect()
                                delay(2000)
                                val result = Client.sendAndWaitForResponse(
                                    signedEvent = signedEvent,
                                    relayList = listOf(RelaySetupInfo(addedWSS, read = true, write = true, setOf())),
                                )
                                RelayPool.getRelay(addedWSS)?.disconnect()
                                RelayPool.removeRelay(
                                    relay,
                                )

                                if (result) {
                                    relays2.add(
                                        RelaySetupInfo(
                                            addedWSS,
                                            read = true,
                                            write = true,
                                            feedTypes = COMMON_FEED_TYPES,
                                        ),
                                    )
                                    onDone()
                                } else {
                                    accountStateViewModel.toast(
                                        context.getString(R.string.relay),
                                        context.getString(R.string.failed_to_send_event),
                                    )
                                }
                                textFieldRelay.value = TextFieldValue("")
                            }
                            isLoading.value = false
                        }
                    }
                },
                onError = { dirtyUrl, errorCode, exceptionMessage ->
                    isLoading.value = false
                    val msg =
                        when (errorCode) {
                            Nip11Retriever.ErrorCode.FAIL_TO_ASSEMBLE_URL ->
                                context.getString(
                                    R.string.relay_information_document_error_assemble_url,
                                    dirtyUrl,
                                    exceptionMessage,
                                )

                            Nip11Retriever.ErrorCode.FAIL_TO_REACH_SERVER ->
                                context.getString(
                                    R.string.relay_information_document_error_assemble_url,
                                    dirtyUrl,
                                    exceptionMessage,
                                )

                            Nip11Retriever.ErrorCode.FAIL_TO_PARSE_RESULT ->
                                context.getString(
                                    R.string.relay_information_document_error_assemble_url,
                                    dirtyUrl,
                                    exceptionMessage,
                                )

                            Nip11Retriever.ErrorCode.FAIL_WITH_HTTP_STATUS ->
                                context.getString(
                                    R.string.relay_information_document_error_assemble_url,
                                    dirtyUrl,
                                    exceptionMessage,
                                )
                        }

                    accountStateViewModel.toast(
                        context.getString(R.string.unable_to_download_relay_document),
                        msg,
                    )
                    textFieldRelay.value = TextFieldValue("")
                },
            )
        }
    }
}

@Composable
fun RelayLogScreen(
    modifier: Modifier,
    url: String,
) {
    val context = LocalContext.current

    Surface(
        modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
        ) {
            val flows = LocalPreferences.allSavedAccounts(context).map {
                NostrSigner.getInstance().getDatabase(it.npub).applicationDao().getLogsByUrl(url)
            }.merge()

            val logs = flows.collectAsStateWithLifecycle(initialValue = emptyList())

            LazyColumn(
                Modifier.weight(1f),
            ) {
                itemsIndexed(logs.value) { _, log ->
                    ElevatedCard(
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

@Composable
fun ActiveRelaysScreen(
    modifier: Modifier,
    navController: NavController,
) {
    val relays2 =
        remember {
            mutableStateListOf<RelaySetupInfo>()
        }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            relays2.addAll(NostrSigner.getInstance().getSavedRelays())
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(),
        ) {
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
                                navController.navigate("RelayLogScreen/${Base64.getEncoder().encodeToString(relays2[it].url.toByteArray())}")
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
