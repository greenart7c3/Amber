package com.greenart7c3.nostrsigner.ui

import android.content.ClipData
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.copy_to_clipboard
import com.greenart7c3.nostrsigner.commonsui.create
import com.greenart7c3.nostrsigner.commonsui.create_nsecbunker_description
import com.greenart7c3.nostrsigner.commonsui.delete_after
import com.greenart7c3.nostrsigner.commonsui.kill_switch_message
import com.greenart7c3.nostrsigner.commonsui.name
import com.greenart7c3.nostrsigner.commonsui.name_cannot_be_empty
import com.greenart7c3.nostrsigner.commonsui.no_relays_added
import com.greenart7c3.nostrsigner.commonsui.paste_from_clipboard
import com.greenart7c3.nostrsigner.commonsui.reconnect
import com.greenart7c3.nostrsigner.commonsui.relays
import com.greenart7c3.nostrsigner.commonsui.testing_relay
import com.greenart7c3.nostrsigner.commonsui.use_this_url_in_your_app
import com.greenart7c3.nostrsigner.commonsui.wss
import com.greenart7c3.nostrsigner.commonsui.your_nsec_bunker_has_been_created
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.okhttp.OkHttpWebSocket
import com.greenart7c3.nostrsigner.ui.actions.onAddRelay
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.TitleExplainer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import java.util.UUID
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun NewNsecBunkerScreen(
    account: Account,
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: NewBunkerViewModel = viewModel(factory = NewBunkerViewModel.factory(LocalAccountRepository.current, LocalRelayClient.current)),
) {
    val localBunkerRelay = LocalBunkerRelay.current
    // The local relay rides along with the defaults, so clients on this
    // device can reach the bunker without any public relay.
    val form = remember { NewBunkerForm(localBunkerRelay.newBunkerRelays()) }
    val context = LocalContext.current
    val relayClient = LocalRelayClient.current
    val accountRepository = LocalAccountRepository.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    if (form.isLoading.value) {
        CenterCircularProgressIndicator(
            modifier = Modifier,
            text = stringResource(Res.string.testing_relay),
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize(),
        ) {
            Text(stringResource(Res.string.create_nsecbunker_description))

            BunkerNameField(name = form.name, onNameChange = { form.name = it }, focusRequester = focusRequester)

            DeleteAfterSetting(selectedIndex = form.deleteAfterIndex, onSelect = { form.deleteAfterIndex = it })

            RelayUrlField(
                text = form.relayText.value.text,
                onTextChange = { form.relayText.value = TextFieldValue(it) },
                onAdd = {
                    form.addRelay(scope, { accountRepository.getSavedRelays(account) }, relayClient.factory, context)
                },
            )

            BunkerRelayList(relays = form.relays, onRemove = { form.relays.removeAt(it) })

            CreateBunkerButton(
                hasRelays = form.relays.isNotEmpty(),
                hasName = form.name.text.isNotBlank(),
                onCreate = {
                    val deleteAfter = deleteAfterToSeconds(parseDeleteAfterType(form.deleteAfterIndex))
                    viewModel.create(account, form.name.text, form.relays.toList(), form.secret, deleteAfter) {
                        navController.navigate("NewNsecBunkerCreated/${form.secret}")
                    }
                },
            )
        }
    }
}

/** What the new bunker form holds while it is filled in. */
@Stable
private class NewBunkerForm(initialRelays: List<NormalizedRelayUrl>) {
    val secret = UUID.randomUUID().toString()
    var name by mutableStateOf(TextFieldValue(AnnotatedString("")))
    val relays = mutableStateListOf(*initialRelays.toTypedArray())
    var deleteAfterIndex by mutableIntStateOf(DeleteAfterType.NEVER.screenCode)

    /** The relay being typed, and whether it is being tested before it is added. */
    val relayText = mutableStateOf(TextFieldValue(""))
    val isLoading = mutableStateOf(false)

    /** Tests the typed relay and adds it to [relays]. */
    fun addRelay(
        scope: CoroutineScope,
        savedRelays: suspend () -> Set<NormalizedRelayUrl>,
        factory: OkHttpWebSocket.Builder,
        context: Context,
    ) {
        scope.launch(Dispatchers.IO) {
            onAddRelay(
                relayText,
                isLoading,
                relays,
                scope,
                savedRelays = savedRelays,
                factory = factory,
                context = context,
                onDone = {},
            )
        }
    }
}

/** How long until the bunker deletes itself. */
@Composable
internal fun DeleteAfterSetting(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val deleteAfterItems =
        persistentListOf(
            TitleExplainer(stringResource(DeleteAfterType.NEVER.resourceId)),
            TitleExplainer(stringResource(DeleteAfterType.FIVE_MINUTES.resourceId)),
            TitleExplainer(stringResource(DeleteAfterType.TEN_MINUTES.resourceId)),
            TitleExplainer(stringResource(DeleteAfterType.ONE_HOUR.resourceId)),
            TitleExplainer(stringResource(DeleteAfterType.ONE_DAY.resourceId)),
            TitleExplainer(stringResource(DeleteAfterType.ONE_WEEK.resourceId)),
        )

    Box(modifier) {
        SettingsRow(
            Res.string.delete_after,
            null,
            deleteAfterItems,
            selectedIndex,
            onSelect = onSelect,
        )
    }
}

/** The bunker's name; Next moves on to the following field. */
@Composable
private fun BunkerNameField(
    name: TextFieldValue,
    onNameChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(Res.string.name)) },
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = {
                focusManager.moveFocus(FocusDirection.Down)
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .padding(vertical = 20.dp),
    )
}

/** A relay URL, pasted or typed; Done and the add button both call [onAdd]. */
@Composable
private fun RelayUrlField(
    text: String,
    onTextChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth(),
            value = text,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
            ),
            onValueChange = onTextChange,
            keyboardActions = KeyboardActions(
                onDone = { onAdd() },
            ),
            label = {
                Text(stringResource(Res.string.wss))
            },
            leadingIcon = {
                IconButton(
                    onClick = {
                        scope.launch {
                            val text = clipboardManager.getClipEntry()?.clipData?.getItemAt(0)?.text?.toString()
                            if (!text.isNullOrBlank()) onTextChange(text)
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
                    onClick = onAdd,
                ) {
                    Icon(
                        Icons.Default.Add,
                        null,
                    )
                }
            },
        )
    }
}

/** The relays the bunker will listen on; tapping one removes it. */
@Composable
private fun BunkerRelayList(
    relays: List<NormalizedRelayUrl>,
    onRemove: (Int) -> Unit,
) {
    Column(Modifier.padding(top = 16.dp)) {
        relays.forEachIndexed { index, relay ->
            RelayCard(
                relay = relay.url,
                onClick = { onRemove(index) },
            )
        }
    }
}

/** Creates the bunker through [onCreate], or, when it has no relays or no name, says which is missing. */
@Composable
private fun CreateBunkerButton(
    hasRelays: Boolean,
    hasName: Boolean,
    onCreate: () -> Unit,
) {
    val title = stringResource(Res.string.relays)
    val noRelaysMessage = stringResource(Res.string.no_relays_added)
    val noNameMessage = stringResource(Res.string.name_cannot_be_empty)

    AmberButton(
        modifier = Modifier.padding(top = 20.dp),
        text = stringResource(Res.string.create),
        onClick = {
            when {
                !hasRelays -> ToastManager.toast(title, noRelaysMessage)
                !hasName -> ToastManager.toast(title, noNameMessage)
                else -> onCreate()
            }
        },
    )
}

@Composable
fun NewNsecBunkerCreatedScreen(
    account: Account,
    key: String,
    modifier: Modifier = Modifier,
    viewModel: NewBunkerViewModel = viewModel(factory = NewBunkerViewModel.factory(LocalAccountRepository.current, LocalRelayClient.current)),
) {
    val settingsStore = LocalSettingsStore.current
    val isLoading = remember(account.npub, key) { mutableStateOf(true) }
    var preparationFailed by remember(account.npub, key) { mutableStateOf(false) }
    var refreshAttempt by remember(account.npub, key) { mutableIntStateOf(0) }
    val killSwitch by settingsStore.settings.killSwitch.collectAsStateWithLifecycle()
    var bunkerUri by remember(account.npub, key) { mutableStateOf("") }

    LaunchedEffect(account.npub, key, refreshAttempt, killSwitch) {
        isLoading.value = true
        preparationFailed = false
        try {
            if (killSwitch) {
                preparationFailed = true
                return@LaunchedEffect
            }
            bunkerUri = viewModel.prepareUri(account, key)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            preparationFailed = true
        } finally {
            if (currentCoroutineContext().isActive) isLoading.value = false
        }
    }

    if (isLoading.value) {
        CenterCircularProgressIndicator(
            modifier = Modifier.fillMaxSize(),
        )
    } else if (preparationFailed) {
        Column(modifier = modifier.padding(16.dp)) {
            if (killSwitch) Text(stringResource(Res.string.kill_switch_message))
            AmberButton(
                text = stringResource(Res.string.reconnect),
                enabled = !killSwitch,
                onClick = { refreshAttempt++ },
            )
        }
    } else {
        BunkerCreated(bunkerUri = bunkerUri, modifier = modifier)
    }
}

/** The new bunker's URI, as text and as a QR code, with a button to copy it. */
@Composable
private fun BunkerCreated(
    bunkerUri: String,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize(),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(Res.string.your_nsec_bunker_has_been_created),
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(stringResource(Res.string.use_this_url_in_your_app))

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            bunkerUri,
            fontSize = 18.sp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        QrCodeDrawer(bunkerUri)

        Spacer(modifier = Modifier.height(8.dp))

        AmberButton(
            onClick = {
                scope.launch {
                    clipboardManager.setClipEntry(
                        ClipEntry(
                            ClipData.newPlainText("", bunkerUri),
                        ),
                    )
                }
            },
            text = stringResource(Res.string.copy_to_clipboard),
        )
    }
}
