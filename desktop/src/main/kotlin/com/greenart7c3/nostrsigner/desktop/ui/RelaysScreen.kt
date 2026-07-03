package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import com.greenart7c3.nostrsigner.desktop.core.Strings
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.launch

@Composable
fun RelaysScreen() {
    val settings by SettingsStore.settings.collectAsState()
    val language by Strings.currentLanguage.collectAsState()
    var newRelay by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = newRelay,
                onValueChange = { newRelay = it },
                label = { Text(Strings.get("d_relay_hint", language)) },
                singleLine = true,
                modifier = Modifier.widthIn(max = 420.dp).weight(1f, fill = false),
            )
            AmberOutlinedButton(
                text = Strings.get("add", language),
                onClick = {
                    val normalized = RelayUrlNormalizer.normalizeOrNull(newRelay.trim())
                    if (normalized == null) {
                        Toaster.toast(Strings.get("d_invalid_relay", language))
                        return@AmberOutlinedButton
                    }
                    SettingsStore.update {
                        it.copy(defaultRelays = (it.defaultRelays + normalized.url).distinct())
                    }
                    newRelay = ""
                    scope.launch {
                        AmberDesktop.engine.updateFilter()
                        AmberDesktop.client.connect()
                    }
                },
            )
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(settings.defaultRelays.size) { index ->
                val relay = settings.defaultRelays[index]
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(relay, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(
                        onClick = {
                            if (settings.defaultRelays.size == 1) {
                                Toaster.toast(Strings.get("d_one_relay_required", language))
                                return@IconButton
                            }
                            SettingsStore.update {
                                it.copy(defaultRelays = it.defaultRelays.filter { url -> url != relay })
                            }
                            scope.launch { AmberDesktop.engine.updateFilter() }
                        },
                    ) {
                        Icon(Icons.Default.Delete, Strings.get("d_remove_relay", language))
                    }
                }
                HorizontalDivider()
            }
        }

        Spacer(Modifier.height(8.dp))
        AmberOutlinedButton(
            text = Strings.get("d_reconnect_relays", language),
            onClick = {
                scope.launch {
                    AmberDesktop.engine.updateFilter()
                    AmberDesktop.client.connect()
                    AmberDesktop.client.reconnect(true)
                    Toaster.toast(Strings.get("d_reconnecting", language))
                }
            },
        )
        Spacer(Modifier.height(12.dp))
    }
}
