package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.DesktopSettings
import com.greenart7c3.nostrsigner.desktop.core.LocalRelays
import com.greenart7c3.nostrsigner.desktop.core.RelayChecker
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import com.greenart7c3.nostrsigner.desktop.core.Strings
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.launch

@Composable
fun RelaysScreen() {
    val settings by SettingsStore.settings.collectAsState()
    val available by AmberDesktop.client.availableRelaysFlow().collectAsState()
    val connected by AmberDesktop.client.connectedRelaysFlow().collectAsState()
    val language by Strings.currentLanguage.collectAsState()
    var newRelay by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    // Non-null while asking "the check failed — add anyway?"; holds the relay
    // and the message key (could_not_connect_to_relay / relay_filter_failed).
    var addAnyway by remember { mutableStateOf<Pair<NormalizedRelayUrl, String>?>(null) }
    val scope = rememberCoroutineScope()

    fun addRelay(relay: NormalizedRelayUrl) {
        SettingsStore.update {
            it.copy(defaultRelays = (it.defaultRelays + relay.url).distinct())
        }
        newRelay = ""
        scope.launch {
            AmberDesktop.engine.checkForNewRelaysAndUpdateAllFilters()
        }
    }

    addAnyway?.let { (relay, messageKey) ->
        AlertDialog(
            onDismissRequest = { addAnyway = null },
            title = { Text(Strings.get("relay", language)) },
            text = { Text(Strings.get(messageKey, language)) },
            confirmButton = {
                AmberTextButton(
                    text = Strings.get("yes", language),
                    onClick = {
                        addAnyway = null
                        addRelay(relay)
                    },
                )
            },
            dismissButton = {
                AmberTextButton(
                    text = Strings.get("no", language),
                    onClick = { addAnyway = null },
                )
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = newRelay,
                onValueChange = { newRelay = it },
                label = { Text(Strings.get("d_relay_hint", language)) },
                singleLine = true,
                enabled = !checking,
                modifier = Modifier.widthIn(max = 420.dp).weight(1f, fill = false),
            )
            AmberOutlinedButton(
                text = if (checking) Strings.get("d_working", language) else Strings.get("add", language),
                enabled = !checking,
                onClick = {
                    // Mirrors the mobile onAddRelay flow: normalize (bare hosts
                    // get wss://, .onion and private IPs ws://), then live-test
                    // the relay as a bunker relay before adding; on failure ask
                    // whether to add it anyway.
                    val normalized = RelayChecker.normalizeUserInput(newRelay)
                    if (normalized == null) {
                        Toaster.toast(Strings.get("d_invalid_relay", language))
                        return@AmberOutlinedButton
                    }
                    if (settings.defaultRelays.contains(normalized.url)) {
                        newRelay = ""
                        return@AmberOutlinedButton
                    }
                    checking = true
                    scope.launch {
                        try {
                            when (RelayChecker.check(normalized)) {
                                RelayChecker.Outcome.OK -> addRelay(normalized)
                                RelayChecker.Outcome.CANNOT_CONNECT -> addAnyway = normalized to "could_not_connect_to_relay"
                                RelayChecker.Outcome.FILTER_FAILED -> addAnyway = normalized to "relay_filter_failed"
                            }
                        } finally {
                            checking = false
                        }
                    }
                },
            )
        }
        // Port of the Android GHSA-8844-q5vh-9j8f warning: an explicit ws://
        // relay on the public internet is cleartext; fine for .onion / local.
        if (LocalRelays.isInsecure(newRelay)) {
            Text(
                Strings.get("insecure_relay_warning", language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.widthIn(max = 520.dp).padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))

        val listState = rememberLazyListState()
        ScrollbarBox(rememberScrollbarAdapter(listState), Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(settings.defaultRelays.size) { index ->
                    val relay = settings.defaultRelays[index]
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(relay, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        if (LocalRelays.isLocal(relay)) {
                            Text(
                                Strings.get("d_local_relay", language),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        RelayConnectionStatus(RelayUrlNormalizer.normalizeOrNull(relay), available, connected)
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (settings.defaultRelays.size == 1) {
                                    Toaster.toast(Strings.get("d_one_relay_required", language))
                                    return@IconButton
                                }
                                SettingsStore.update {
                                    it.copy(defaultRelays = it.defaultRelays.filter { url -> url != relay })
                                }
                                scope.launch { AmberDesktop.engine.checkForNewRelaysAndUpdateAllFilters() }
                            },
                        ) {
                            Icon(Icons.Default.Delete, Strings.get("d_remove_relay", language))
                        }
                    }
                    HorizontalDivider()
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AmberOutlinedButton(
                text = Strings.get("d_default_relays", language),
                onClick = {
                    // Union with the shipped defaults (same set as Android):
                    // existing relays stay, missing defaults come back.
                    SettingsStore.update {
                        it.copy(defaultRelays = (it.defaultRelays + DesktopSettings().defaultRelays).distinct())
                    }
                    scope.launch {
                        AmberDesktop.engine.checkForNewRelaysAndUpdateAllFilters()
                        Toaster.toast(Strings.get("d_saved", language))
                    }
                },
            )
            AmberOutlinedButton(
                text = Strings.get("d_reconnect_relays", language),
                onClick = {
                    scope.launch {
                        AmberDesktop.engine.checkForNewRelaysAndUpdateAllFilters(shouldReconnect = true)
                        Toaster.toast(Strings.get("d_reconnecting", language))
                    }
                },
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Live connection state of one configured relay: connected, in the client's
 * pool but not connected (connecting, failing or given up on), or not in use
 * (no subscription references it, so the client never opens a socket).
 */
@Composable
private fun RelayConnectionStatus(
    relay: NormalizedRelayUrl?,
    available: Set<NormalizedRelayUrl>,
    connected: Set<NormalizedRelayUrl>,
) {
    val language by Strings.currentLanguage.collectAsState()
    val (color, key) = when {
        relay != null && relay in connected -> Color(0xFF2E7D32) to "d_relay_connected"
        relay != null && relay in available -> MaterialTheme.colorScheme.error to "d_relay_disconnected"
        else -> MaterialTheme.colorScheme.outline to "d_relay_not_in_use"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            Strings.get(key, language),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
