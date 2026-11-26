package com.greenart7c3.nostrsigner.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.add
import com.greenart7c3.nostrsigner.commonsui.manage_the_relays_used_to_fetch_your_profile_data
import com.greenart7c3.nostrsigner.commonsui.paste_from_clipboard
import com.greenart7c3.nostrsigner.commonsui.profile_fetch_interval
import com.greenart7c3.nostrsigner.commonsui.profile_fetch_interval_description
import com.greenart7c3.nostrsigner.commonsui.testing_relay
import com.greenart7c3.nostrsigner.commonsui.wss
import com.greenart7c3.nostrsigner.data.RelayClient
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.ProfileFetchInterval
import com.greenart7c3.nostrsigner.service.TrustScoreService
import com.greenart7c3.nostrsigner.ui.actions.onAddRelay
import com.greenart7c3.nostrsigner.ui.components.TitleExplainer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun DefaultProfileRelaysScreen(
    account: Account,
    modifier: Modifier = Modifier,
) {
    val settingsStore = LocalSettingsStore.current
    val relayClient = LocalRelayClient.current
    val accountRepository = LocalAccountRepository.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboard.current
    val state = remember {
        DefaultProfileRelaysState(
            settingsStore.settings.defaultProfileRelays,
            settingsStore.settings.profileFetchInterval,
        )
    }
    val actions = DefaultProfileRelaysActions(
        state = state,
        savedRelays = { accountRepository.getSavedRelays(account) },
        settingsStore = settingsStore,
        relayClient = relayClient,
        context = context,
        scope = scope,
    )

    LaunchedEffect(state.relays.toList()) {
        actions.fetchMissingTrustScores()
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize(),
    ) {
        if (state.isLoading.value) {
            CenterCircularProgressIndicator(
                Modifier,
                text = stringResource(Res.string.testing_relay),
            )
        } else {
            DefaultProfileRelaysContent(
                state = state,
                actions = actions,
                onPaste = {
                    scope.launch {
                        val text = clipboardManager.getClipEntry()?.clipData?.getItemAt(0)?.text?.toString()
                        if (!text.isNullOrBlank()) {
                            state.textFieldRelay.value = TextFieldValue(text)
                        }
                    }
                },
            )
        }
    }
}

private class DefaultProfileRelaysState(
    initialRelays: List<NormalizedRelayUrl>,
    initialFetchInterval: ProfileFetchInterval,
) {
    val textFieldRelay = mutableStateOf(TextFieldValue(""))
    val isLoading = mutableStateOf(false)
    val relays = mutableStateListOf(*initialRelays.toTypedArray())
    val trustScores = mutableStateMapOf<String, Int?>()
    val loadingScores = mutableStateMapOf<String, Boolean>()
    var fetchInterval by mutableStateOf(initialFetchInterval)
}

private class DefaultProfileRelaysActions(
    private val state: DefaultProfileRelaysState,
    private val savedRelays: suspend () -> Set<NormalizedRelayUrl>,
    private val settingsStore: SettingsStore,
    private val relayClient: RelayClient,
    private val context: Context,
    private val scope: CoroutineScope,
) {
    fun fetchMissingTrustScores() {
        if (!TrustScoreService.isEnabled(settingsStore.settings)) return
        state.relays.forEach { relay ->
            val url = relay.url
            if (!state.trustScores.containsKey(url)) {
                state.loadingScores[url] = true
                scope.launch(Dispatchers.IO) {
                    val score = TrustScoreService.getScore(url, settingsStore.settings)
                    state.trustScores[url] = score
                    state.loadingScores[url] = false
                }
            }
        }
    }

    fun selectFetchInterval(selected: ProfileFetchInterval) {
        state.fetchInterval = selected
        scope.launch(Dispatchers.IO) {
            LocalPreferences.updateProfileFetchInterval(context, selected)
        }
    }

    fun setRelayText(text: String) {
        state.textFieldRelay.value = TextFieldValue(text)
    }

    fun addRelay() {
        scope.launch(Dispatchers.IO) {
            onAddRelay(
                state.textFieldRelay,
                state.isLoading,
                state.relays,
                scope,
                savedRelays = savedRelays,
                factory = relayClient.factory,
                context = context,
                shouldCheckForBunker = false,
                onDone = ::saveRelays,
            )
        }
    }

    fun removeRelay(index: Int) {
        state.isLoading.value = true
        state.relays.removeAt(index)
        saveRelays()
    }

    private fun saveRelays() {
        settingsStore.settings = settingsStore.settings.copy(
            defaultProfileRelays = state.relays,
        )
        LocalPreferences.saveSettingsToEncryptedStorage(context, settingsStore.settings)
        scope.launch(Dispatchers.IO) {
            if (!BuildFlavorChecker.isOfflineFlavor()) {
                relayClient.checkForNewRelaysAndUpdateAllFilters()
                state.isLoading.value = false
            } else {
                state.isLoading.value = false
            }
        }
    }
}

@Composable
private fun DefaultProfileRelaysContent(
    state: DefaultProfileRelaysState,
    actions: DefaultProfileRelaysActions,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize(),
    ) {
        Text(
            text = stringResource(Res.string.manage_the_relays_used_to_fetch_your_profile_data),
            modifier = Modifier.padding(bottom = 8.dp),
        )

        val fetchIntervalOptions = ProfileFetchInterval.entries.map {
            TitleExplainer(stringResource(it.resourceId))
        }.toImmutableList()

        SettingsRow(
            name = Res.string.profile_fetch_interval,
            description = Res.string.profile_fetch_interval_description,
            selectedItems = fetchIntervalOptions,
            selectedIndex = ProfileFetchInterval.entries.indexOf(state.fetchInterval),
            onSelect = { index ->
                actions.selectFetchInterval(ProfileFetchInterval.entries[index])
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        RelayUrlField(
            relayText = state.textFieldRelay.value.text,
            onRelayTextChange = actions::setRelayText,
            onAddRelay = actions::addRelay,
            onPaste = onPaste,
        )

        ProfileRelayList(
            relays = state.relays,
            trustScores = state.trustScores,
            loadingScores = state.loadingScores,
            onRemoveRelay = actions::removeRelay,
            modifier = Modifier
                .weight(1f),
        )
    }
}

@Composable
private fun RelayUrlField(
    relayText: String,
    onRelayTextChange: (String) -> Unit,
    onAddRelay: () -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        modifier = modifier
            .fillMaxWidth(),
        value = relayText,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        onValueChange = onRelayTextChange,
        keyboardActions = KeyboardActions(
            onDone = { onAddRelay() },
        ),
        label = {
            Text(stringResource(Res.string.wss))
        },
        leadingIcon = {
            IconButton(
                onClick = onPaste,
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
                onClick = onAddRelay,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(Res.string.add),
                )
            }
        },
    )
}

@Composable
private fun ProfileRelayList(
    relays: List<NormalizedRelayUrl>,
    trustScores: Map<String, Int?>,
    loadingScores: Map<String, Boolean>,
    onRemoveRelay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier,
    ) {
        items(relays.size) {
            RelayCard(
                trustScore = trustScores[relays[it].url],
                isLoadingScore = loadingScores[relays[it].url] == true,
                showTrustScore = hasMeaningfulTrustBadge(relays[it].url),
                relay = relays[it].url,
                onClick = {
                    onRemoveRelay(it)
                },
            )
        }
    }
}
