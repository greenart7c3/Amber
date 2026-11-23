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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.paging.compose.collectAsLazyPagingItems
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.checkNotInMainThread
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.active_relays_connections_description
import com.greenart7c3.nostrsigner.commonsui.active_relays_default_description
import com.greenart7c3.nostrsigner.commonsui.add
import com.greenart7c3.nostrsigner.commonsui.could_not_connect_to_relay
import com.greenart7c3.nostrsigner.commonsui.default_relay_text
import com.greenart7c3.nostrsigner.commonsui.default_relays
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.insecure_relay_warning
import com.greenart7c3.nostrsigner.commonsui.log_repeated
import com.greenart7c3.nostrsigner.commonsui.manage_the_relays_used_for_communicating_with_external_applications
import com.greenart7c3.nostrsigner.commonsui.paste_from_clipboard
import com.greenart7c3.nostrsigner.commonsui.relay
import com.greenart7c3.nostrsigner.commonsui.relay_filter_failed
import com.greenart7c3.nostrsigner.commonsui.relays_used_by_connections
import com.greenart7c3.nostrsigner.commonsui.testing_relay
import com.greenart7c3.nostrsigner.commonsui.wss
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.TimeUtils as AmberTimeUtils
import com.greenart7c3.nostrsigner.models.defaultAppRelays
import com.greenart7c3.nostrsigner.okhttp.OkHttpWebSocket
import com.greenart7c3.nostrsigner.relays.AmberListenerSingleton
import com.greenart7c3.nostrsigner.service.PrivateAddress
import com.greenart7c3.nostrsigner.service.TrustScoreService
import com.greenart7c3.nostrsigner.ui.CenterCircularProgressIndicator
import com.greenart7c3.nostrsigner.ui.LocalAccountRepository
import com.greenart7c3.nostrsigner.ui.LocalRelayClient
import com.greenart7c3.nostrsigner.ui.LocalSettingsStore
import com.greenart7c3.nostrsigner.ui.LogsViewModel
import com.greenart7c3.nostrsigner.ui.RelayCard
import com.greenart7c3.nostrsigner.ui.ToastManager
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.SectionLabel
import com.greenart7c3.nostrsigner.ui.components.TrustScoreBadge
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.Base64
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun DefaultRelaysScreen(
    account: Account,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settingsStore = LocalSettingsStore.current
    val relayClient = LocalRelayClient.current
    val accountRepository = LocalAccountRepository.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboard.current
    val textFieldRelay = remember { mutableStateOf(TextFieldValue("")) }
    val isLoading = remember { mutableStateOf(false) }
    val relays2 = remember { mutableStateListOf(*settingsStore.settings.defaultRelays.toTypedArray()) }
    val trustScores = remember { mutableStateMapOf<String, Int?>() }
    val loadingScores = remember { mutableStateMapOf<String, Boolean>() }

    // Fetch trust scores for all relays
    LaunchedEffect(relays2.toList()) {
        if (TrustScoreService.isEnabled(settingsStore.settings)) {
            relays2.forEach { relay ->
                val url = relay.url
                if (!trustScores.containsKey(url)) {
                    loadingScores[url] = true
                    scope.launch(Dispatchers.IO) {
                        val score = TrustScoreService.getScore(url, settingsStore.settings)
                        trustScores[url] = score
                        loadingScores[url] = false
                    }
                }
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize(),
    ) {
        if (isLoading.value) {
            CenterCircularProgressIndicator(
                Modifier,
                text = stringResource(Res.string.testing_relay),
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
                            settingsStore.settings = settingsStore.settings.copy(
                                defaultRelays = relays2,
                            )
                            LocalPreferences.saveSettingsToEncryptedStorage(context, settingsStore.settings)
                            scope.launch(Dispatchers.IO) {
                                if (!BuildFlavorChecker.isOfflineFlavor()) {
                                    relayClient.checkForNewRelaysAndUpdateAllFilters()
                                    delay(2.seconds)
                                    relayClient.client.reconnect()
                                    isLoading.value = false
                                } else {
                                    isLoading.value = false
                                }
                            }
                        },
                        text = stringResource(Res.string.default_relay_text),
                    )
                }

                Text(
                    text = stringResource(Res.string.manage_the_relays_used_for_communicating_with_external_applications),
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
                                    savedRelays = { accountRepository.getSavedRelays(account) },
                                    factory = relayClient.factory,
                                    context = context,
                                    onDone = {
                                        settingsStore.settings = settingsStore.settings.copy(
                                            defaultRelays = relays2,
                                        )
                                        LocalPreferences.saveSettingsToEncryptedStorage(context, settingsStore.settings)
                                        scope.launch(Dispatchers.IO) {
                                            if (!BuildFlavorChecker.isOfflineFlavor()) {
                                                relayClient.checkForNewRelaysAndUpdateAllFilters()
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
                        Text(stringResource(Res.string.wss))
                    },
                    leadingIcon = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val text = clipboardManager.getClipEntry()?.clipData?.getItemAt(0)?.text?.toString()
                                    if (!text.isNullOrBlank()) {
                                        textFieldRelay.value = TextFieldValue(text)
                                    }
                                }
                            },
                        ) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = stringResource(Res.string.paste_from_clipboard),
                            )
                        }
                    },
                    trailingIcon = {
                        IconButton(
                            colors = IconButtonDefaults.iconButtonColors().copy(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    onAddRelay(
                                        textFieldRelay,
                                        isLoading,
                                        relays2,
                                        scope,
                                        savedRelays = { accountRepository.getSavedRelays(account) },
                                        factory = relayClient.factory,
                                        context = context,
                                        onDone = {
                                            settingsStore.settings = settingsStore.settings.copy(
                                                defaultRelays = relays2,
                                            )
                                            LocalPreferences.saveSettingsToEncryptedStorage(context, settingsStore.settings)
                                            scope.launch(Dispatchers.IO) {
                                                if (!BuildFlavorChecker.isOfflineFlavor()) {
                                                    relayClient.checkForNewRelaysAndUpdateAllFilters()
                                                    isLoading.value = false
                                                } else {
                                                    isLoading.value = false
                                                }
                                            }
                                        },
                                    )
                                }
                            },
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(Res.string.add),
                            )
                        }
                    },
                )

                // Defense-in-depth (GHSA-8844-q5vh-9j8f, I4): warn when the
                // user explicitly types a ws:// URL for a non-onion /
                // non-private-network relay, since NIP-46 traffic over
                // cleartext exposes metadata + ciphertext to network
                // observers. wss:// is the default; ws:// is auto-prefixed
                // only for .onion / private IPs in onAddRelay.
                val text = textFieldRelay.value.text
                val showInsecureWarning = text.startsWith("ws://") &&
                    !text.endsWith(".onion") &&
                    !text.endsWith(".onion/") &&
                    !PrivateAddress.isPrivate(text)
                if (showInsecureWarning) {
                    Text(
                        text = stringResource(Res.string.insecure_relay_warning),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp),
                        fontSize = 12.sp,
                    )
                }

                LazyColumn(
                    Modifier
                        .weight(1f),
                ) {
                    items(relays2.size) {
                        val relayUrl = relays2[it].url
                        RelayCard(
                            relay = relayUrl,
                            trustScore = trustScores[relayUrl],
                            isLoadingScore = loadingScores[relayUrl] == true,
                            showTrustScore = !TrustScoreService.isExcludedFromScoring(relayUrl),
                            onClick = {
                                isLoading.value = true
                                trustScores.remove(relayUrl)
                                loadingScores.remove(relayUrl)
                                relays2.removeAt(it)
                                settingsStore.settings = settingsStore.settings.copy(
                                    defaultRelays = relays2,
                                )
                                LocalPreferences.saveSettingsToEncryptedStorage(context, settingsStore.settings)
                                scope.launch(Dispatchers.IO) {
                                    if (!BuildFlavorChecker.isOfflineFlavor()) {
                                        relayClient.checkForNewRelaysAndUpdateAllFilters()
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
    // The account's saved relays: an added relay already among them needs no bunker check.
    savedRelays: suspend () -> Set<NormalizedRelayUrl>,
    factory: OkHttpWebSocket.Builder,
    context: Context,
    shouldCheckForBunker: Boolean = true,
    onDone: () -> Unit,
) {
    checkNotInMainThread()
    val url = textFieldRelay.value.text
    if (url.isBlank() || url == "/") return
    val addedWSS = relayUrlToAdd(url) ?: return
    if (relays2.any { it == addedWSS }) {
        textFieldRelay.value = TextFieldValue("")
        return
    }
    isLoading.value = true
    val add = {
        relays2.add(addedWSS)
        onDone()
    }

    scope.launch(Dispatchers.IO) {
        if (!shouldCheckForBunker) {
            add()
            textFieldRelay.value = TextFieldValue("")
            return@launch
        }
        // An added relay already among the account's saved relays needs no bunker check.
        if (addedWSS in savedRelays()) add() else probeBunkerRelay(addedWSS, factory, context, add)
        textFieldRelay.value = TextFieldValue("")
        isLoading.value = false
    }
}

/**
 * Adds [relay] once a NIP-46 request published to it comes back on a subscription;
 * when it cannot be reached or does not serve the request back, asks before adding it.
 */
private suspend fun probeBunkerRelay(
    relay: NormalizedRelayUrl,
    factory: OkHttpWebSocket.Builder,
    context: Context,
    add: () -> Unit,
) {
    val probe = BunkerRelayProbe(NostrClient(factory), relay, onCannotConnect = { offerToAdd(Res.string.could_not_connect_to_relay, add) })
    if (!probe.awaitConnection()) {
        offerToAdd(Res.string.could_not_connect_to_relay, add)
        probe.close()
        return
    }
    val published = probe.publish()
    val echoed = if (published) {
        AmberListenerSingleton.latestErrorMessages.clear()
        probe.awaitEcho()
    } else {
        // The failure is shown as it is, not as a filter problem.
        AmberListenerSingleton.showErrorMessage(context)
        true
    }
    probe.close()
    when {
        published && echoed -> add()
        !echoed -> offerToAdd(Res.string.relay_filter_failed, add)
    }
}

/** A toast saying [message] about the relay, whose Accept adds it anyway. */
private fun offerToAdd(
    message: StringResource,
    add: () -> Unit,
) {
    ToastManager.toast(getStringBlocking(Res.string.relay), getStringBlocking(message), onAccept = add, onReject = {})
}

/**
 * A throwaway NIP-46 request (kind 24133) on [relay] from a fresh key, and a
 * subscription to that key's requests, to see whether the relay serves them back.
 */
private class BunkerRelayProbe(
    private val client: NostrClient,
    private val relay: NormalizedRelayUrl,
    onCannotConnect: () -> Unit,
) {
    private val signer = NostrSignerInternal(KeyPair())
    private val encryptedContent = signer.signerSync.nip04Encrypt("Test bunker event", signer.keyPair.pubKey.toHexKey())
    private val subId = UUID.randomUUID().toString().substring(0, 4)

    // Re-signed after a failed publish; the echo must match the latest one.
    @Volatile private var event: Event = sign()

    @Volatile private var connected = false

    @Volatile private var echoed = false

    private val listener = object : RelayConnectionListener {
        override fun onSent(relay: IRelayClient, cmdStr: String, cmd: Command, success: Boolean) {
            if (!success) {
                AmberListenerSingleton.latestErrorMessages.add("Failed to send event. Try again.")
            }
            super.onSent(relay, cmdStr, cmd, success)
        }

        override fun onDisconnected(relay: IRelayClient) {
            AmberLog.d(Amber.TAG, "Disconnected from ${relay.url.url}")
            super.onDisconnected(relay)
        }

        override fun onConnected(relay: IRelayClient, pingMillis: Int, compressed: Boolean) {
            connected = true
            super.onConnected(relay, pingMillis, compressed)
        }

        override fun onCannotConnect(relay: IRelayClient, errorMessage: String) {
            onCannotConnect()
            super.onCannotConnect(relay, errorMessage)
        }

        override suspend fun onIncomingMessage(relay: IRelayClient, msgStr: String, msg: Message) {
            if (msg is EventMessage && msg.isEcho()) echoed = true
            if (msg is OkMessage && !msg.success) {
                AmberListenerSingleton.latestErrorMessages.add(msg.message)
            }
            super.onIncomingMessage(relay, msgStr, msg)
        }
    }

    init {
        client.addConnectionListener(listener)
        if (!client.isActive()) client.connect()
        val filter = Filter(kinds = listOf(NostrConnectEvent.KIND), tags = mapOf("p" to listOf(event.pubKey)))
        client.subscribe(subId, mapOf(relay to listOf(filter)))
    }

    /** The latest request, served back on our subscription. */
    private fun EventMessage.isEcho(): Boolean = subId == this@BunkerRelayProbe.subId && event.kind == NostrConnectEvent.KIND && event.id == this@BunkerRelayProbe.event.id

    private fun sign(): Event = signer.signerSync.sign(
        TimeUtils.now(),
        NostrConnectEvent.KIND,
        arrayOf(arrayOf("p", signer.keyPair.pubKey.toHexKey())),
        encryptedContent,
    )

    /** Whether the relay connects within 30 seconds. */
    suspend fun awaitConnection(): Boolean = withTimeoutOrNull(30.seconds) {
        while (!connected) delay(200.milliseconds)
        true
    } ?: false

    /** Publishes the request, re-signed after each failure, up to three times. */
    suspend fun publish(): Boolean {
        repeat(3) {
            if (client.publishAndConfirm(event, setOf(relay))) return true
            delay(1.seconds)
            event = sign()
        }
        return false
    }

    /** Whether the request comes back on the subscription within 10 seconds. */
    suspend fun awaitEcho(): Boolean {
        repeat(10) {
            if (echoed) return true
            delay(1.seconds)
        }
        return echoed
    }

    fun close() {
        client.unsubscribe(subId)
        client.disconnect()
        client.removeConnectionListener(listener)
    }
}

/**
 * The relay a typed address names: as given with a `ws://` or `wss://` scheme, otherwise
 * `ws://` for onion and private-network hosts (no TLS there) and `wss://` for the rest;
 * null when it is not a relay URL.
 */
fun relayUrlToAdd(url: String): NormalizedRelayUrl? {
    val withScheme = when {
        url.startsWith("wss://") || url.startsWith("ws://") -> url
        url.endsWith(".onion") || url.endsWith(".onion/") || PrivateAddress.isPrivate(url) -> "ws://$url"
        else -> "wss://$url"
    }
    return RelayUrlNormalizer.normalizeOrNull(withScheme)
}

@Composable
fun RelayLogScreen(
    paddingValues: PaddingValues,
    account: Account,
    url: String,
    modifier: Modifier = Modifier,
    viewModel: LogsViewModel = viewModel(factory = LogsViewModel.factory(LocalAccountRepository.current)),
) {
    val context = LocalContext.current
    val logs = remember(account.npub, url) { viewModel.logs(account.npub, url) }
    val lazyPagingItems = logs.collectAsLazyPagingItems()

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = paddingValues,
    ) {
        items(lazyPagingItems.itemCount) { index ->
            val log = lazyPagingItems[index]
            log?.let {
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
                            text = AmberTimeUtils.formatDateTime(context, log.time, withSeconds = true),
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
                            text = if (log.repeatCount > 1) stringResource(Res.string.log_repeated, log.message, log.repeatCount) else log.message,
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
}

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun ActiveRelaysScreen(
    navController: NavController,
    account: Account,
    modifier: Modifier = Modifier,
) {
    val settingsStore = LocalSettingsStore.current
    val accountRepository = LocalAccountRepository.current
    val scope = rememberCoroutineScope()
    // Relays configured as defaults — these are used for every new connection.
    val defaultRelays = remember { mutableStateListOf<NormalizedRelayUrl>() }
    // Relays that existing connections still use but that are not part of the
    // default set. Shown separately so it is clear they come from individual app
    // connections, not from the default relay settings.
    val connectionRelays = remember { mutableStateListOf<NormalizedRelayUrl>() }
    val trustScores = remember { mutableStateMapOf<String, Int?>() }
    val loadingScores = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val defaults = settingsStore.settings.defaultRelays
            val saved = accountRepository.getSavedRelays(account)
            defaultRelays.clear()
            defaultRelays.addAll(defaults.distinct())
            connectionRelays.clear()
            connectionRelays.addAll(saved.filterNot { it in defaults })
        }
    }

    // Fetch trust scores for every relay shown
    LaunchedEffect(defaultRelays.toList(), connectionRelays.toList()) {
        if (TrustScoreService.isEnabled(settingsStore.settings)) {
            (defaultRelays + connectionRelays).forEach { relay ->
                val url = relay.url
                if (!trustScores.containsKey(url)) {
                    loadingScores[url] = true
                    scope.launch(Dispatchers.IO) {
                        val score = TrustScoreService.getScore(url, settingsStore.settings)
                        trustScores[url] = score
                        loadingScores[url] = false
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize(),
    ) {
        SectionLabel(stringResource(Res.string.default_relays))
        Text(
            text = stringResource(Res.string.active_relays_default_description),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        defaultRelays.forEach { relay ->
            ActiveRelayRow(relay, trustScores, loadingScores, navController)
        }

        if (connectionRelays.isNotEmpty()) {
            SectionLabel(stringResource(Res.string.relays_used_by_connections))
            Text(
                text = stringResource(Res.string.active_relays_connections_description),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            connectionRelays.forEach { relay ->
                ActiveRelayRow(relay, trustScores, loadingScores, navController)
            }
        }
    }
}

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
private fun ActiveRelayRow(
    relay: NormalizedRelayUrl,
    trustScores: Map<String, Int?>,
    loadingScores: Map<String, Boolean>,
    navController: NavController,
) {
    val relayClient = LocalRelayClient.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
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
            val isConnected by remember(relay) {
                relayClient.client.connectedRelaysFlow().map { status ->
                    relay in status
                }
            }.collectAsStateWithLifecycle(relay in relayClient.client.connectedRelaysFlow().value)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = relay.url,
                    fontSize = 24.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isConnected) Color.Unspecified else Color.Gray,
                )
                if (!TrustScoreService.isExcludedFromScoring(relay.url)) {
                    TrustScoreBadge(
                        score = trustScores[relay.url],
                        isLoading = loadingScores[relay.url] == true,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    text = if (isConnected) "${relayClient.relayStats.get(relay).pingInMs}ms ping" else "Unavailable",
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
