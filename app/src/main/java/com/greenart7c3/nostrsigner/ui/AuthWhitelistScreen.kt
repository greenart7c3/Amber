package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.add
import com.greenart7c3.nostrsigner.commonsui.add_relay_to_whitelist
import com.greenart7c3.nostrsigner.commonsui.auth_whitelist_description
import com.greenart7c3.nostrsigner.commonsui.paste_from_clipboard
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.service.RelayUrlUtils
import com.greenart7c3.nostrsigner.service.TrustScoreService
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun AuthWhitelistScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settingsStore = LocalSettingsStore.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboard.current
    val textFieldRelay = remember { mutableStateOf(TextFieldValue("")) }
    val whitelist = remember {
        mutableStateListOf(*settingsStore.settings.authWhitelist.toTypedArray())
    }
    val trustScores = remember { mutableStateMapOf<String, Int?>() }
    val loadingScores = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(whitelist.toList()) {
        loadMissingTrustScores(whitelist, trustScores, loadingScores, settingsStore, scope)
    }

    fun addEntry() {
        val input = textFieldRelay.value.text.trim()
        val url = RelayUrlUtils.extractHostAndPort(input)
        if (url.isNotBlank() && url !in whitelist) {
            whitelist.add(url)
            settingsStore.settings = settingsStore.settings.copy(authWhitelist = whitelist.toList())
            LocalPreferences.saveSettingsToEncryptedStorage(context, settingsStore.settings)
            textFieldRelay.value = TextFieldValue("")
        }
    }

    AuthWhitelistContent(
        value = textFieldRelay.value.text,
        onValueChange = {
            textFieldRelay.value = TextFieldValue(it)
        },
        onPaste = {
            scope.launch {
                val text = clipboardManager.getClipEntry()?.clipData?.getItemAt(0)?.text?.toString()
                if (!text.isNullOrBlank()) {
                    textFieldRelay.value = TextFieldValue(text)
                }
            }
        },
        onAdd = { addEntry() },
        whitelist = whitelist,
        trustScores = trustScores,
        loadingScores = loadingScores,
        onRemove = { index ->
            whitelist.removeAt(index)
            settingsStore.settings = settingsStore.settings.copy(
                authWhitelist = whitelist.toList(),
            )
            scope.launch(Dispatchers.IO) {
                LocalPreferences.saveSettingsToEncryptedStorage(context, settingsStore.settings)
            }
        },
        modifier = modifier,
    )
}

private fun loadMissingTrustScores(
    whitelist: List<String>,
    trustScores: MutableMap<String, Int?>,
    loadingScores: MutableMap<String, Boolean>,
    settingsStore: SettingsStore,
    scope: CoroutineScope,
) {
    if (TrustScoreService.isEnabled(settingsStore.settings)) {
        whitelist.forEach { hostname ->
            if (!trustScores.containsKey(hostname)) {
                loadingScores[hostname] = true
                scope.launch(Dispatchers.IO) {
                    val normalizedUrl = RelayUrlNormalizer.normalizeOrNull("wss://$hostname")
                    val score = normalizedUrl?.let { TrustScoreService.getScore(it.url, settingsStore.settings) }
                    trustScores[hostname] = score
                    loadingScores[hostname] = false
                }
            }
        }
    }
}

@Composable
private fun AuthWhitelistContent(
    value: String,
    onValueChange: (String) -> Unit,
    onPaste: () -> Unit,
    onAdd: () -> Unit,
    whitelist: List<String>,
    trustScores: Map<String, Int?>,
    loadingScores: Map<String, Boolean>,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(),
        ) {
            Text(
                text = stringResource(Res.string.auth_whitelist_description),
                modifier = Modifier.padding(bottom = 8.dp),
            )

            AuthWhitelistInput(
                value = value,
                onValueChange = onValueChange,
                onPaste = onPaste,
                onAdd = onAdd,
            )

            AuthWhitelistEntries(
                whitelist = whitelist,
                trustScores = trustScores,
                loadingScores = loadingScores,
                onRemove = onRemove,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AuthWhitelistInput(
    value: String,
    onValueChange: (String) -> Unit,
    onPaste: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        modifier = modifier.fillMaxWidth(),
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
            Text(stringResource(Res.string.add_relay_to_whitelist))
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
                onClick = onAdd,
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
private fun AuthWhitelistEntries(
    whitelist: List<String>,
    trustScores: Map<String, Int?>,
    loadingScores: Map<String, Boolean>,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier,
    ) {
        items(whitelist.size) { index ->
            val hostname = whitelist[index]
            val schemedUrlSoHostnameIsNotExcludedFromScoring = "wss://$hostname"
            RelayCard(
                relay = hostname,
                trustScore = trustScores[hostname],
                isLoadingScore = loadingScores[hostname] == true,
                showTrustScore = hasMeaningfulTrustBadge(schemedUrlSoHostnameIsNotExcludedFromScoring),
                onClick = { onRemove(index) },
            )
        }
    }
}
