package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.builtin_tor_active
import com.greenart7c3.nostrsigner.commonsui.builtin_tor_description
import com.greenart7c3.nostrsigner.commonsui.builtin_tor_title
import com.greenart7c3.nostrsigner.commonsui.d_tor_external
import com.greenart7c3.nostrsigner.commonsui.d_tor_external_sub
import com.greenart7c3.nostrsigner.commonsui.d_tor_invalid_port
import com.greenart7c3.nostrsigner.commonsui.d_tor_local_note
import com.greenart7c3.nostrsigner.commonsui.d_tor_off
import com.greenart7c3.nostrsigner.commonsui.d_tor_proxy_ok
import com.greenart7c3.nostrsigner.commonsui.d_tor_socks_port
import com.greenart7c3.nostrsigner.commonsui.d_tor_stopped
import com.greenart7c3.nostrsigner.commonsui.do_you_really_want_to_disable_tor_text
import com.greenart7c3.nostrsigner.commonsui.do_you_really_want_to_disable_tor_title
import com.greenart7c3.nostrsigner.commonsui.failed_to_connect_to_tor_orbot
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.no
import com.greenart7c3.nostrsigner.commonsui.save
import com.greenart7c3.nostrsigner.commonsui.tor_connecting
import com.greenart7c3.nostrsigner.commonsui.tor_connection_failed
import com.greenart7c3.nostrsigner.commonsui.tor_restart
import com.greenart7c3.nostrsigner.commonsui.tor_starting
import com.greenart7c3.nostrsigner.commonsui.yes
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.RelayConnections
import com.greenart7c3.nostrsigner.desktop.core.RelayHttpClients
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import com.greenart7c3.nostrsigner.desktop.core.TorManager
import com.greenart7c3.nostrsigner.desktop.core.TorMode
import com.greenart7c3.nostrsigner.desktop.core.TorStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ColumnScope.TorSection() {
    val settings by SettingsStore.settings.collectAsState()
    val torStatus by TorManager.status.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmDisable by remember { mutableStateOf(false) }

    TorModeChips(
        selected = settings.torMode,
        onSelect = { mode ->
            when (mode) {
                settings.torMode -> {}
                TorMode.DISABLED -> confirmDisable = true
                else -> applyTorMode(scope, mode, settings.proxyPort)
            }
        },
    )
    Spacer(Modifier.height(4.dp))
    when (settings.torMode) {
        TorMode.BUILTIN -> BuiltinTorSection(torStatus)
        TorMode.EXTERNAL -> ExternalTorSection(settings.proxyPort) { applyTorMode(scope, TorMode.EXTERNAL, it) }
        TorMode.DISABLED -> {}
    }
    Text(
        stringResource(Res.string.d_tor_local_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (confirmDisable) {
        ConfirmDisableTorDialog(
            onConfirm = {
                confirmDisable = false
                applyTorMode(scope, TorMode.DISABLED, settings.proxyPort)
            },
            onDismiss = { confirmDisable = false },
        )
    }
}

@Composable
private fun TorModeChips(
    selected: TorMode,
    onSelect: (TorMode) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            TorMode.DISABLED to stringResource(Res.string.d_tor_off),
            TorMode.BUILTIN to stringResource(Res.string.builtin_tor_title),
            TorMode.EXTERNAL to stringResource(Res.string.d_tor_external),
        ).forEach { (mode, label) ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun ColumnScope.BuiltinTorSection(torStatus: TorStatus) {
    Text(stringResource(Res.string.builtin_tor_description), style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            torStatusText(torStatus),
            style = MaterialTheme.typography.bodyMedium,
            color = if (torStatus is TorStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false),
        )
        AmberTextButton(
            text = stringResource(Res.string.tor_restart),
            onClick = { TorManager.restart(AmberDesktop.applicationIOScope) },
        )
    }
}

@Composable
private fun torStatusText(status: TorStatus): String = when (status) {
    TorStatus.Stopped -> stringResource(Res.string.d_tor_stopped)
    is TorStatus.Connecting -> if (status.percentage <= 0) {
        stringResource(Res.string.tor_starting)
    } else {
        stringResource(Res.string.tor_connecting, status.percentage)
    }
    TorStatus.Connected -> stringResource(Res.string.builtin_tor_active)
    is TorStatus.Failed -> listOfNotNull(
        stringResource(Res.string.tor_connection_failed),
        status.message?.takeIf { it.isNotBlank() },
    ).joinToString(": ")
}

@Composable
private fun ColumnScope.ExternalTorSection(
    proxyPort: Int,
    onSave: (Int) -> Unit,
) {
    var portText by remember(proxyPort) { mutableStateOf(proxyPort.toString()) }
    Text(stringResource(Res.string.d_tor_external_sub), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = portText,
            onValueChange = { value -> portText = value.filter { it.isDigit() }.take(5) },
            label = { Text(stringResource(Res.string.d_tor_socks_port)) },
            singleLine = true,
            modifier = Modifier.widthIn(max = 200.dp),
        )
        AmberOutlinedButton(
            text = stringResource(Res.string.save),
            onClick = {
                val port = portText.toIntOrNull()
                if (port == null || port !in 1..65535) {
                    Toaster.toast(getStringBlocking(Res.string.d_tor_invalid_port))
                } else {
                    onSave(port)
                }
            },
        )
    }
}

@Composable
private fun ConfirmDisableTorDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.do_you_really_want_to_disable_tor_title)) },
        text = { Text(stringResource(Res.string.do_you_really_want_to_disable_tor_text)) },
        confirmButton = {
            AmberTextButton(text = stringResource(Res.string.yes), onClick = onConfirm)
        },
        dismissButton = {
            AmberTextButton(text = stringResource(Res.string.no), onClick = onDismiss)
        },
    )
}

private fun applyTorMode(
    scope: CoroutineScope,
    mode: TorMode,
    port: Int,
) {
    SettingsStore.update { it.copy(torMode = mode, proxyPort = port) }
    RelayConnections.applyTorSettings()
    if (mode == TorMode.EXTERNAL) {
        scope.launch {
            val alive = withContext(Dispatchers.IO) { RelayHttpClients.isSocksProxyAlive(port) }
            Toaster.toast(getStringBlocking(if (alive) Res.string.d_tor_proxy_ok else Res.string.failed_to_connect_to_tor_orbot))
        }
    }
}
