package com.greenart7c3.nostrsigner.ui

import android.content.ClipData
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
import com.greenart7c3.nostrsigner.ui.actions.onAddRelay
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.TitleExplainer
import java.util.UUID
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
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
    val context = LocalContext.current
    val relayClient = LocalRelayClient.current
    val accountRepository = LocalAccountRepository.current
    val secret = remember { mutableStateOf(UUID.randomUUID().toString()) }
    var name by remember { mutableStateOf(TextFieldValue(AnnotatedString(""))) }
    val clipboardManager = LocalClipboard.current

    val relays =
        remember {
            // The local relay rides along with the defaults, so clients on this
            // device can reach the bunker without any public relay.
            mutableStateListOf(*localBunkerRelay.newBunkerRelays().toTypedArray())
        }

    val textFieldRelay = remember {
        mutableStateOf(TextFieldValue(""))
    }
    val scope = rememberCoroutineScope()
    val isLoading = remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val deleteAfterItems =
        persistentListOf(
            TitleExplainer(stringResource(DeleteAfterType.NEVER.resourceId)),
            TitleExplainer(stringResource(DeleteAfterType.FIVE_MINUTES.resourceId)),
            TitleExplainer(stringResource(DeleteAfterType.TEN_MINUTES.resourceId)),
            TitleExplainer(stringResource(DeleteAfterType.ONE_HOUR.resourceId)),
            TitleExplainer(stringResource(DeleteAfterType.ONE_DAY.resourceId)),
            TitleExplainer(stringResource(DeleteAfterType.ONE_WEEK.resourceId)),
        )
    var deleteAfterIndex by remember { mutableIntStateOf(DeleteAfterType.NEVER.screenCode) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    if (isLoading.value) {
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

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
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

            Box {
                SettingsRow(
                    Res.string.delete_after,
                    null,
                    deleteAfterItems,
                    deleteAfterIndex,
                ) {
                    deleteAfterIndex = it
                }
            }

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
                    value = textFieldRelay.value.text,
                    keyboardOptions = KeyboardOptions(
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
                                    relays,
                                    scope,
                                    savedRelays = { accountRepository.getSavedRelays(account) },
                                    factory = relayClient.factory,
                                    context = context,
                                    onDone = {
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
                                        relays,
                                        scope,
                                        savedRelays = { accountRepository.getSavedRelays(account) },
                                        factory = relayClient.factory,
                                        context = context,
                                        onDone = {
                                        },
                                    )
                                }
                            },
                        ) {
                            Icon(
                                Icons.Default.Add,
                                null,
                            )
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            relays.forEachIndexed { index, relay ->
                RelayCard(
                    relay = relay.url,
                    onClick = {
                        relays.removeAt(index)
                    },
                )
            }

            val title = stringResource(Res.string.relays)
            val noRelaysMessage = stringResource(Res.string.no_relays_added)
            val noNameMessage = stringResource(Res.string.name_cannot_be_empty)

            AmberButton(
                modifier = Modifier.padding(top = 20.dp),
                text = stringResource(Res.string.create),
                onClick = {
                    if (relays.isEmpty()) {
                        ToastManager.toast(
                            title,
                            noRelaysMessage,
                        )

                        return@AmberButton
                    }

                    if (name.text.isBlank()) {
                        ToastManager.toast(
                            title,
                            noNameMessage,
                        )

                        return@AmberButton
                    }

                    val deleteAfter = deleteAfterToSeconds(parseDeleteAfterType(deleteAfterIndex))
                    viewModel.create(account, name.text, relays.toList(), secret.value, deleteAfter) {
                        navController.navigate("NewNsecBunkerCreated/${secret.value}")
                    }
                },
            )
        }
    }
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
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

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
}
