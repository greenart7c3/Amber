package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.add
import com.greenart7c3.nostrsigner.commonsui.close_application
import com.greenart7c3.nostrsigner.commonsui.delete
import com.greenart7c3.nostrsigner.commonsui.delete_application
import com.greenart7c3.nostrsigner.commonsui.edit_configuration_description
import com.greenart7c3.nostrsigner.commonsui.name
import com.greenart7c3.nostrsigner.commonsui.reset_bunker
import com.greenart7c3.nostrsigner.commonsui.testing_relay
import com.greenart7c3.nostrsigner.commonsui.update
import com.greenart7c3.nostrsigner.commonsui.wss
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.TrustScoreService
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.actions.onAddRelay
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.TrustScoreBadge
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.orange
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.collections.set
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
fun EditConfigurationScreen(
    key: String,
    account: Account,
    onNav: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditConfigurationViewModel = viewModel(
        factory = EditConfigurationViewModel.factory(LocalContext.current.applicationContext, LocalAccountRepository.current, LocalRelayClient.current),
    ),
) {
    val settingsStore = LocalSettingsStore.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val relayClient = LocalRelayClient.current
    val accountRepository = LocalAccountRepository.current
    val application by viewModel.configuration.collectAsStateWithLifecycle()
    val state = remember { EditConfigurationState() }

    LaunchedEffect(Unit) {
        state.show(viewModel.load(account.npub, key))

        if (TrustScoreService.isEnabled(settingsStore.settings)) {
            state.loadTrustScores(scope, settingsStore)
        }

        state.isLoading.value = false
    }

    if (state.isLoading.value) {
        CenterCircularProgressIndicator(
            modifier,
            if (state.textFieldRelay.value.text.isNotBlank()) {
                stringResource(Res.string.testing_relay)
            } else {
                null
            },
        )
    } else {
        EditConfigurationForm(
            state = state,
            showRelays = application?.showRelays == true,
            onAddRelay = {
                scope.launch(Dispatchers.IO) {
                    onAddRelay(
                        state.textFieldRelay,
                        state.isLoading,
                        state.relays,
                        scope,
                        savedRelays = { accountRepository.getSavedRelays(account) },
                        factory = relayClient.factory,
                        context = context,
                        onDone = {},
                    )
                }
            },
            onUpdate = { viewModel.update(account.npub, state.name.text, state.relays.toList(), state.closeApp) { onNav(Route.Applications.route) } },
            onResetBunker = { viewModel.resetBunker(account.npub) { onNav(Route.Applications.route) } },
            onDelete = { viewModel.delete(account.npub) { onNav(Route.Applications.route) } },
            modifier = modifier,
        )
    }
}

@Stable
private class EditConfigurationState {
    var name by mutableStateOf(TextFieldValue(AnnotatedString("")))
    val relays = mutableStateListOf<NormalizedRelayUrl>()
    val textFieldRelay = mutableStateOf(TextFieldValue(AnnotatedString("")))
    val isLoading = mutableStateOf(true)
    var closeApp by mutableStateOf(false)
    val trustScores = mutableStateMapOf<String, Int?>()
    val loadingScores = mutableStateMapOf<String, Boolean>()

    fun show(loaded: AppConfiguration?) {
        name = TextFieldValue(AnnotatedString(loaded?.name?.ifBlank { loaded.key.toShortenHex() } ?: ""))
        closeApp = loaded?.closeApplication != false

        loaded?.relays?.forEach {
            relays.add(
                it.copy(),
            )
        }
    }

    fun loadTrustScores(scope: CoroutineScope, settingsStore: SettingsStore) {
        relays.forEach { relay ->
            val url = relay.url
            if (!trustScores.containsKey(url)) {
                loadingScores[url] = true
                scope.launch(Dispatchers.IO) {
                    val score = TrustScoreService.getScore(url, settingsStore.settings)
                    withContext(Dispatchers.Main) {
                        trustScores[url] = score
                        loadingScores[url] = false
                    }
                }
            }
        }
    }
}

@Composable
private fun EditConfigurationForm(
    state: EditConfigurationState,
    showRelays: Boolean,
    onAddRelay: () -> Unit,
    onUpdate: () -> Unit,
    onResetBunker: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize(),
    ) {
        Text(stringResource(Res.string.edit_configuration_description))

        Spacer(modifier = Modifier.height(16.dp))

        NameField(
            value = state.name,
            onValueChange = { state.name = it },
        )

        CloseApplicationRow(
            checked = state.closeApp,
            onToggle = { state.closeApp = !state.closeApp },
            onCheckedChange = { state.closeApp = it },
        )

        Spacer(modifier = Modifier.height(16.dp))
        if (showRelays) {
            RelayInputField(
                value = state.textFieldRelay.value.text,
                onValueChange = { state.textFieldRelay.value = TextFieldValue(it) },
                onAdd = onAddRelay,
            )

            Spacer(modifier = Modifier.height(16.dp))

            state.relays.forEachIndexed { index, relay ->
                EditableRelayCard(
                    url = relay.url,
                    score = state.trustScores[relay.url],
                    isLoadingScore = state.loadingScores[relay.url] == true,
                    onRemove = { state.relays.removeAt(index) },
                )
            }
        } else {
            state.relays.forEach { relay ->
                RelayCard(url = relay.url, textColor = Color.LightGray)
            }
        }

        ConfigurationButtons(
            onUpdate = onUpdate,
            onResetBunker = onResetBunker,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun NameField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next,
        ),
        onValueChange = onValueChange,
        label = { Text(stringResource(Res.string.name)) },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun CloseApplicationRow(
    checked: Boolean,
    onToggle: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(vertical = 8.dp)
            .clickable(onClick = onToggle),
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(Res.string.close_application),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun RelayInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        modifier = modifier
            .fillMaxWidth(),
        value = value,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        onValueChange = onValueChange,
        keyboardActions = KeyboardActions(
            onDone = { onAdd() },
        ),
        label = {
            Text(stringResource(Res.string.wss))
        },
        trailingIcon = {
            IconButton(
                colors = IconButtonDefaults.iconButtonColors().copy(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                onClick = onAdd,
                content = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(Res.string.add),
                    )
                },
            )
        },
    )
}

@Composable
private fun EditableRelayCard(
    url: String,
    score: Int?,
    isLoadingScore: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RelayCard(url = url, modifier = modifier) {
        if (!TrustScoreService.isExcludedFromScoring(url)) {
            TrustScoreBadge(
                score = score,
                isLoading = isLoadingScore,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        IconButton(
            onClick = onRemove,
        ) {
            Icon(
                ImageVector.vectorResource(R.drawable.delete),
                stringResource(Res.string.delete),
            )
        }
    }
}

@Composable
private fun RelayCard(
    url: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Card(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        border = BorderStroke(1.dp, Color.LightGray),
        colors = CardDefaults.cardColors().copy(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                url,
                Modifier
                    .weight(0.9f)
                    .padding(8.dp)
                    .padding(start = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = textColor,
            )
            trailing()
        }
    }
}

@Composable
private fun ColumnScope.ConfigurationButtons(
    onUpdate: () -> Unit,
    onResetBunker: () -> Unit,
    onDelete: () -> Unit,
) {
    AmberButton(
        modifier = Modifier
            .padding(vertical = 20.dp),
        onClick = onUpdate,
        text = stringResource(Res.string.update),
    )

    AmberButton(
        modifier = Modifier
            .padding(top = 20.dp),
        colors = ButtonDefaults.buttonColors().copy(
            containerColor = orange,
        ),
        onClick = onResetBunker,
        text = stringResource(Res.string.reset_bunker),
        textColor = Color.White,
    )

    AmberButton(
        modifier = Modifier
            .padding(top = 20.dp),
        colors = ButtonDefaults.buttonColors().copy(
            containerColor = orange,
        ),
        onClick = onDelete,
        text = stringResource(Res.string.delete_application),
        textColor = Color.White,
    )
}
