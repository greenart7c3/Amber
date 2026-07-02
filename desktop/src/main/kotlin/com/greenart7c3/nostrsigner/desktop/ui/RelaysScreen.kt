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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.launch

@Composable
fun RelaysScreen() {
    val settings by SettingsStore.settings.collectAsState()
    var newRelay by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(12.dp))
        Text("Default bunker relays", style = MaterialTheme.typography.titleMedium)
        Text(
            "New bunker connections listen and respond on these relays.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newRelay,
                onValueChange = { newRelay = it },
                label = { Text("wss://relay.example.com") },
                modifier = Modifier.weight(1f),
            )
            AmberOutlinedButton(
                modifier = Modifier.weight(0.3f),
                text = "Add",
                onClick = {
                    val normalized = RelayUrlNormalizer.normalizeOrNull(newRelay.trim())
                    if (normalized == null) {
                        Toaster.toast("Invalid relay URL")
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(settings.defaultRelays.size) { index ->
                val relay = settings.defaultRelays[index]
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(relay, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(
                            onClick = {
                                if (settings.defaultRelays.size == 1) {
                                    Toaster.toast("At least one relay is required")
                                    return@IconButton
                                }
                                SettingsStore.update {
                                    it.copy(defaultRelays = it.defaultRelays.filter { url -> url != relay })
                                }
                                scope.launch { AmberDesktop.engine.updateFilter() }
                            },
                        ) {
                            Icon(Icons.Default.Delete, "Remove relay")
                        }
                    }
                }
            }
        }

        AmberButton(
            text = "Reconnect relays",
            onClick = {
                scope.launch {
                    AmberDesktop.engine.updateFilter()
                    AmberDesktop.client.connect()
                    AmberDesktop.client.reconnect(true)
                    Toaster.toast("Reconnecting…")
                }
            },
        )
        Spacer(Modifier.height(12.dp))
    }
}
