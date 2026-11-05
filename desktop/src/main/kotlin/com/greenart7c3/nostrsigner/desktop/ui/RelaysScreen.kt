package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.active_relays_connections_description
import com.greenart7c3.nostrsigner.commonsui.active_relays_default_description
import com.greenart7c3.nostrsigner.commonsui.add
import com.greenart7c3.nostrsigner.commonsui.could_not_connect_to_relay
import com.greenart7c3.nostrsigner.commonsui.d_default_relays
import com.greenart7c3.nostrsigner.commonsui.d_invalid_relay
import com.greenart7c3.nostrsigner.commonsui.d_local_relay
import com.greenart7c3.nostrsigner.commonsui.d_one_relay_required
import com.greenart7c3.nostrsigner.commonsui.d_reconnect_relays
import com.greenart7c3.nostrsigner.commonsui.d_reconnecting
import com.greenart7c3.nostrsigner.commonsui.d_relay_connected
import com.greenart7c3.nostrsigner.commonsui.d_relay_disconnected
import com.greenart7c3.nostrsigner.commonsui.d_relay_hint
import com.greenart7c3.nostrsigner.commonsui.d_relay_not_in_use
import com.greenart7c3.nostrsigner.commonsui.d_remove_relay
import com.greenart7c3.nostrsigner.commonsui.d_saved
import com.greenart7c3.nostrsigner.commonsui.d_working
import com.greenart7c3.nostrsigner.commonsui.default_relays
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.insecure_relay_warning
import com.greenart7c3.nostrsigner.commonsui.no
import com.greenart7c3.nostrsigner.commonsui.relay
import com.greenart7c3.nostrsigner.commonsui.relay_filter_failed
import com.greenart7c3.nostrsigner.commonsui.relays_used_by_connections
import com.greenart7c3.nostrsigner.commonsui.yes
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.DesktopSettings
import com.greenart7c3.nostrsigner.desktop.core.LocalRelays
import com.greenart7c3.nostrsigner.desktop.core.RelayChecker
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun RelaysScreen() {
    val settings by SettingsStore.settings.collectAsState()
    val available by AmberDesktop.client.availableRelaysFlow().collectAsState()
    val connected by AmberDesktop.client.connectedRelaysFlow().collectAsState()
    val connectionRelays by remember { AmberDesktop.connectionRelaysFlow() }.collectAsState(emptySet())
    // Mirrors the Android active relays screen: relays existing connections
    // still use that are not in the default set get their own section.
    val connectionOnlyRelays = remember(connectionRelays, settings.defaultRelays) {
        val defaults = settings.normalizedDefaultRelays().toSet()
        connectionRelays.filterNot { it in defaults }.map { it.url }.sorted()
    }
    var newRelay by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    // Non-null while asking "the check failed — add anyway?"; holds the relay
    // and the message key (could_not_connect_to_relay / relay_filter_failed).
    var addAnyway by remember { mutableStateOf<Pair<NormalizedRelayUrl, StringResource>?>(null) }
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
            title = { Text(stringResource(Res.string.relay)) },
            text = { Text(stringResource(messageKey)) },
            confirmButton = {
                AmberTextButton(
                    text = stringResource(Res.string.yes),
                    onClick = {
                        addAnyway = null
                        addRelay(relay)
                    },
                )
            },
            dismissButton = {
                AmberTextButton(
                    text = stringResource(Res.string.no),
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
                label = { Text(stringResource(Res.string.d_relay_hint)) },
                singleLine = true,
                enabled = !checking,
                modifier = Modifier.widthIn(max = 420.dp).weight(1f, fill = false),
            )
            AmberOutlinedButton(
                text = if (checking) stringResource(Res.string.d_working) else stringResource(Res.string.add),
                enabled = !checking,
                onClick = {
                    // Mirrors the mobile onAddRelay flow: normalize (bare hosts
                    // get wss://, .onion and private IPs ws://), then live-test
                    // the relay as a bunker relay before adding; on failure ask
                    // whether to add it anyway.
                    val normalized = RelayChecker.normalizeUserInput(newRelay)
                    if (normalized == null) {
                        Toaster.toast(getStringBlocking(Res.string.d_invalid_relay))
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
                                RelayChecker.Outcome.CANNOT_CONNECT -> addAnyway = normalized to Res.string.could_not_connect_to_relay
                                RelayChecker.Outcome.FILTER_FAILED -> addAnyway = normalized to Res.string.relay_filter_failed
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
                stringResource(Res.string.insecure_relay_warning),
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
                item {
                    RelaySectionHeader(
                        stringResource(Res.string.default_relays),
                        stringResource(Res.string.active_relays_default_description),
                    )
                }
                items(settings.defaultRelays.size) { index ->
                    val relay = settings.defaultRelays[index]
                    RelayRow(relay, available, connected) {
                        IconButton(
                            onClick = {
                                if (settings.defaultRelays.size == 1) {
                                    Toaster.toast(getStringBlocking(Res.string.d_one_relay_required))
                                    return@IconButton
                                }
                                SettingsStore.update {
                                    it.copy(defaultRelays = it.defaultRelays.filter { url -> url != relay })
                                }
                                scope.launch { AmberDesktop.engine.checkForNewRelaysAndUpdateAllFilters() }
                            },
                        ) {
                            Icon(Icons.Default.Delete, stringResource(Res.string.d_remove_relay))
                        }
                    }
                    HorizontalDivider()
                }
                if (connectionOnlyRelays.isNotEmpty()) {
                    item {
                        RelaySectionHeader(
                            stringResource(Res.string.relays_used_by_connections),
                            stringResource(Res.string.active_relays_connections_description),
                            Modifier.padding(top = 16.dp),
                        )
                    }
                    items(connectionOnlyRelays.size) { index ->
                        RelayRow(connectionOnlyRelays[index], available, connected)
                        HorizontalDivider()
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AmberOutlinedButton(
                text = stringResource(Res.string.d_default_relays),
                onClick = {
                    // Union with the shipped defaults (same set as Android):
                    // existing relays stay, missing defaults come back.
                    SettingsStore.update {
                        it.copy(defaultRelays = (it.defaultRelays + DesktopSettings().defaultRelays).distinct())
                    }
                    scope.launch {
                        AmberDesktop.engine.checkForNewRelaysAndUpdateAllFilters()
                        Toaster.toast(getStringBlocking(Res.string.d_saved))
                    }
                },
            )
            AmberOutlinedButton(
                text = stringResource(Res.string.d_reconnect_relays),
                onClick = {
                    scope.launch {
                        AmberDesktop.engine.checkForNewRelaysAndUpdateAllFilters(shouldReconnect = true)
                        Toaster.toast(getStringBlocking(Res.string.d_reconnecting))
                    }
                },
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun RelaySectionHeader(title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RelayRow(
    relay: String,
    available: Set<NormalizedRelayUrl>,
    connected: Set<NormalizedRelayUrl>,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        // Rows without a trailing button keep the height of those with one.
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(relay, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (LocalRelays.isLocal(relay)) {
            Text(
                stringResource(Res.string.d_local_relay),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
        }
        RelayConnectionStatus(RelayUrlNormalizer.normalizeOrNull(relay), available, connected)
        Spacer(Modifier.width(8.dp))
        trailing()
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
    val (color, key) = when {
        relay != null && relay in connected -> Color(0xFF2E7D32) to Res.string.d_relay_connected
        relay != null && relay in available -> MaterialTheme.colorScheme.error to Res.string.d_relay_disconnected
        else -> MaterialTheme.colorScheme.outline to Res.string.d_relay_not_in_use
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(key),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
