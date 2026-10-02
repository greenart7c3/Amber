package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.core.LocalRelays
import com.greenart7c3.nostrsigner.desktop.core.RelayChecker
import com.greenart7c3.nostrsigner.desktop.core.Strings
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.launch

/**
 * Editable list of a connection's relays, mirroring the Android relay
 * section of `EditConfigurationScreen` / `NewNsecBunkerScreen`: an input that
 * normalizes and live-tests the relay before adding it (asking whether to add
 * it anyway when the check fails), and a removable row per relay. When
 * [editable] is false the relays are shown greyed out and read-only, like
 * Android does for an already-connected application.
 *
 * Not lazily laid out, so it can sit inside a dialog or a scrolling column.
 */
@Composable
fun RelayListEditor(
    relays: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    editable: Boolean = true,
) {
    val language by Strings.currentLanguage.collectAsState()
    var newRelay by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    // Non-null while asking "the check failed — add anyway?"; holds the relay
    // and the message key (could_not_connect_to_relay / relay_filter_failed).
    var addAnyway by remember { mutableStateOf<Pair<NormalizedRelayUrl, String>?>(null) }
    val scope = rememberCoroutineScope()

    fun addRelay(relay: NormalizedRelayUrl) {
        onChange((relays + relay.url).distinct())
        newRelay = ""
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

    Column(modifier) {
        if (editable) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = newRelay,
                    onValueChange = { newRelay = it },
                    label = { Text(Strings.get("d_relay_hint", language)) },
                    singleLine = true,
                    enabled = !checking,
                    modifier = Modifier.weight(1f),
                )
                AmberOutlinedButton(
                    text = if (checking) Strings.get("d_working", language) else Strings.get("add", language),
                    enabled = !checking,
                    onClick = {
                        val normalized = RelayChecker.normalizeUserInput(newRelay)
                        if (normalized == null) {
                            Toaster.toast(Strings.get("d_invalid_relay", language))
                            return@AmberOutlinedButton
                        }
                        if (relays.contains(normalized.url)) {
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
            if (LocalRelays.isInsecure(newRelay)) {
                Text(
                    Strings.get("insecure_relay_warning", language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (relays.isEmpty()) {
            Text(
                Strings.get("no_relays_added", language),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        relays.forEach { relay ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp).height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    relay,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (editable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (LocalRelays.isLocal(relay)) {
                    Text(
                        Strings.get("d_local_relay", language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (editable) {
                    IconButton(onClick = { onChange(relays.filter { it != relay }) }) {
                        Icon(Icons.Default.Delete, Strings.get("d_remove_relay", language))
                    }
                }
            }
            HorizontalDivider()
        }
    }
}
