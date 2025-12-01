package com.greenart7c3.nostrsigner.ui.actions

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.checkNotInMainThread
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.TimeUtils.formatLongToCustomDateTimeWithSeconds
import com.greenart7c3.nostrsigner.models.defaultAppRelays
import com.greenart7c3.nostrsigner.relays.AmberListenerSingleton
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.CenterCircularProgressIndicator
import com.greenart7c3.nostrsigner.ui.RelayCard
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.sendAndWaitForResponse
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DefaultRelaysScreen(
    modifier: Modifier,
    accountStateViewModel: AccountStateViewModel,
    account: Account,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val textFieldRelay = remember { mutableStateOf(TextFieldValue("")) }
    val isLoading = remember { mutableStateOf(false) }
    val relays2 = remember { mutableStateListOf(*Amber.instance.settings.defaultRelays.toTypedArray()) }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize(),
    ) {
        if (isLoading.value) {
            CenterCircularProgressIndicator(
                Modifier,
                text = stringResource(R.string.testing_relay),
            )
        } else {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.End,
                ) {
                    AmberButton(
                        onClick = {
                            relays2.clear()
                            relays2.addAll(defaultAppRelays)
                            Amber.instance.settings = Amber.instance.settings.copy(
                                defaultRelays = relays2,
                            )
                            LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
                            scope.launch(Dispatchers.IO) {
                                @Suppress("KotlinConstantConditions")
                                if (BuildConfig.FLAVOR != "offline") {
                                    Amber.instance.checkForNewRelaysAndUpdateAllFilters()
                                    delay(2000)
                                    Amber.instance.client.reconnect()
                                    isLoading.value = false
                                } else {
                                    isLoading.value = false
                                }
                            }
                        },
                        text = stringResource(R.string.default_relay_text),
                    )
                }

                Text(
                    text = stringResource(R.string.manage_the_relays_used_for_communicating_with_external_applications),
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth(),
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
                                        Amber.instance.settings = Amber.instance.settings.copy(
                                            defaultRelays = relays2,
                                        )
                                        LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
                                        scope.launch(Dispatchers.IO) {
                                            @Suppress("KotlinConstantConditions")
                                            if (BuildConfig.FLAVOR != "offline") {
                                                Amber.instance.checkForNewRelaysAndUpdateAllFilters()
                                                isLoading.value = false
                                            } else {
                                                isLoading.value = false
                                            }
                                        }
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
                        .fillMaxWidth(),
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
                                    isLoading.value = true
                                    Amber.instance.settings = Amber.instance.settings.copy(
                                        defaultRelays = relays2,
                                    )
                                    LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
                                    scope.launch(Dispatchers.IO) {
                                        @Suppress("KotlinConstantConditions")
                                        if (BuildConfig.FLAVOR != "offline") {
                                            Amber.instance.checkForNewRelaysAndUpdateAllFilters()
                                            isLoading.value = false
                                        } else {
                                            isLoading.value = false
                                        }
                                    }
                                },
                            )
                        }
                    },
                    text = stringResource(R.string.add),
                )

                LazyColumn(
                    Modifier
                        .weight(1f),
                ) {
                    items(relays2.size) {
                        RelayCard(
                            relay = relays2[it].url,
                            onClick = {
                                isLoading.value = true
                                relays2.removeAt(it)
                                Amber.instance.settings = Amber.instance.settings.copy(
                                    defaultRelays = relays2,
                                )
                                LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
                                scope.launch(Dispatchers.IO) {
                                    @Suppress("KotlinConstantConditions")
                                    if (BuildConfig.FLAVOR != "offline") {
                                        Amber.instance.checkForNewRelaysAndUpdateAllFilters()
                                        isLoading.value = false
                                    } else {
                                        isLoading.value = false
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

fun onAddRelay(
    textFieldRelay: MutableState<TextFieldValue>,
    isLoading: MutableState<Boolean>,
    relays2: SnapshotStateList<NormalizedRelayUrl>,
    scope: CoroutineScope,
    accountStateViewModel: AccountStateViewModel,
    account: Account,
    context: Context,
    shouldCheckForBunker: Boolean = true,
    onDone: () -> Unit,
) {
    checkNotInMainThread()
    val url = textFieldRelay.value.text
    if (url.isNotBlank() && url != "/") {
        isLoading.value = true
        val isPrivateIp = Amber.instance.isPrivateIp(url)
        val addedWSS =
            if (!url.startsWith("wss://") && !url.startsWith("ws://")) {
                if (url.endsWith(".onion") || url.endsWith(".onion/") || isPrivateIp) {
                    RelayUrlNormalizer.normalizeOrNull("ws://$url") ?: return
                } else {
                    RelayUrlNormalizer.normalizeOrNull("wss://$url") ?: return
                }
            } else {
                RelayUrlNormalizer.normalizeOrNull(url) ?: return
            }

        if (relays2.any { it == addedWSS }) {
            textFieldRelay.value = TextFieldValue("")
            isLoading.value = false
            return
        }

        scope.launch(Dispatchers.IO) secondLaunch@{
            if (shouldCheckForBunker) {
                val relays = Amber.instance.getSavedRelays(account)
                if (addedWSS in relays) {
                    relays2.add(addedWSS)
                    onDone()
                    textFieldRelay.value = TextFieldValue("")
                    isLoading.value = false
                    return@secondLaunch
                }

                val signer = NostrSignerInternal(KeyPair())
                val encryptedContent = signer.signerSync.nip04Encrypt(
                    "Test bunker event",
                    signer.keyPair.pubKey.toHexKey(),
                )

                val signedEvent = signer.signerSync.sign<Event>(
                    TimeUtils.now(),
                    NostrConnectEvent.KIND,
                    arrayOf(arrayOf("p", signer.keyPair.pubKey.toHexKey())),
                    encryptedContent,
                )

                val filters = listOf(
                    Filter(
                        kinds = listOf(NostrConnectEvent.KIND),
                        tags = mapOf("p" to listOf(signedEvent.pubKey)),
                    ),
                )

                var filterResult = false
                val ncSub = UUID.randomUUID().toString().substring(0, 4)

                val listener = object : IRelayClientListener {
                    override fun onCannotConnect(relay: IRelayClient, errorMessage: String) {
                        accountStateViewModel.toast(
                            context.getString(R.string.relay),
                            context.getString(R.string.could_not_connect_to_relay),
                            onAccept = {
                                relays2.add(addedWSS)
                                onDone()
                            },
                            onReject = {},
                        )
                        super.onCannotConnect(relay, errorMessage)
                    }

                    override fun onIncomingMessage(relay: IRelayClient, msgStr: String, msg: Message) {
                        if (msg is EventMessage) {
                            if (ncSub == msg.subId && msg.event.kind == NostrConnectEvent.KIND && msg.event.id == signedEvent.id) {
                                filterResult = true
                            }
                        }
                        super.onIncomingMessage(relay, msgStr, msg)
                    }
                }

                Amber.instance.client.subscribe(listener)

                if (!Amber.instance.client.isActive()) {
                    Amber.instance.client.connect()
                }

                Amber.instance.client.openReqSubscription(
                    ncSub,
                    mapOf(addedWSS to filters),
                )

                val result = Amber.instance.client.sendAndWaitForResponse(
                    event = signedEvent,
                    relayList = setOf(addedWSS),
                )

                if (result) {
                    var count = 0
                    while (!filterResult && count < 10) {
                        delay(1000)
                        count++
                    }
                } else {
                    AmberListenerSingleton.showErrorMessage()
                    filterResult = true
                }

                Amber.instance.client.close(ncSub)
                Amber.instance.client.unsubscribe(listener)
                Amber.instance.client.reconnect()

                if (result && filterResult) {
                    relays2.add(addedWSS)
                    onDone()
                } else if (!filterResult) {
                    accountStateViewModel.toast(
                        context.getString(R.string.relay),
                        context.getString(R.string.relay_filter_failed),
                        onAccept = {
                            relays2.add(addedWSS)
                            onDone()
                        },
                        onReject = {},
                    )
                }

                textFieldRelay.value = TextFieldValue("")
                isLoading.value = false
            } else {
                relays2.add(addedWSS)
                onDone()
                textFieldRelay.value = TextFieldValue("")
            }
        }
    }
}

@Composable
fun RelayLogScreen(
    paddingValues: PaddingValues,
    url: String,
) {
    val context = LocalContext.current

    val flows = LocalPreferences.allSavedAccounts(context).map {
        Amber.instance.getLogDatabase(it.npub).dao().getLogsByUrl(url)
    }.merge()

    val logs = flows.collectAsStateWithLifecycle(initialValue = emptyList())

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = paddingValues,
    ) {
        itemsIndexed(logs.value) { _, log ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        modifier = Modifier.padding(top = 16.dp),
                        text = formatLongToCustomDateTimeWithSeconds(log.time),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = log.type,
                        fontSize = 20.sp,
                    )
                    Text(
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                        text = log.message,
                        fontSize = 20.sp,
                    )

                    Spacer(Modifier.weight(1f))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun ActiveRelaysScreen(
    modifier: Modifier,
    navController: NavController,
    account: Account,
) {
    val relays2 =
        remember {
            mutableStateListOf<NormalizedRelayUrl>()
        }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            relays2.addAll(Amber.instance.getSavedRelays(account))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize(),
    ) {
        relays2.forEach { relay ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp)
                    .clickable {
                        navController.navigate(
                            "RelayLogScreen/${
                                Base64
                                    .getEncoder()
                                    .encodeToString(relay.url.toByteArray())
                            }",
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                ) {
                    val isConnected by Amber.instance.client.relayStatusFlow().map { status ->
                        relay in status.connected
                    }.collectAsStateWithLifecycle(relay in Amber.instance.client.relayStatusFlow().value.connected)

                    Text(
                        modifier = Modifier.padding(top = 16.dp),
                        text = relay.url,
                        fontSize = 24.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isConnected) Color.Unspecified else Color.Gray,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                            text = if (isConnected) "${Amber.instance.relayStats.get(relay).pingInMs}ms ping" else "Unavailable",
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isConnected) Color.Unspecified else Color.Gray,
                        )
                    }

                    Spacer(Modifier.weight(1f))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
