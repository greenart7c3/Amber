package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.isTraySupported
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.d_fetch_profiles
import com.greenart7c3.nostrsigner.commonsui.d_fetch_profiles_sub
import com.greenart7c3.nostrsigner.commonsui.d_keep_in_tray
import com.greenart7c3.nostrsigner.commonsui.d_keep_in_tray_sub
import com.greenart7c3.nostrsigner.commonsui.d_local_bunker_port
import com.greenart7c3.nostrsigner.commonsui.d_local_bunker_port_invalid
import com.greenart7c3.nostrsigner.commonsui.d_local_bunker_server
import com.greenart7c3.nostrsigner.commonsui.d_local_bunker_server_sub
import com.greenart7c3.nostrsigner.commonsui.d_notifications
import com.greenart7c3.nostrsigner.commonsui.d_notifications_sub
import com.greenart7c3.nostrsigner.commonsui.d_saved
import com.greenart7c3.nostrsigner.commonsui.d_start_on_boot
import com.greenart7c3.nostrsigner.commonsui.d_start_on_boot_sub
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.save
import com.greenart7c3.nostrsigner.desktop.core.AutoStart
import com.greenart7c3.nostrsigner.desktop.core.BunkerRelays
import com.greenart7c3.nostrsigner.desktop.core.DesktopSettings
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ColumnScope.DesktopSection(settings: DesktopSettings) {
    if (isTraySupported) {
        SettingSwitch(
            title = stringResource(Res.string.d_keep_in_tray),
            description = stringResource(Res.string.d_keep_in_tray_sub),
            checked = settings.closeToTray,
            onCheckedChange = { value -> SettingsStore.update { it.copy(closeToTray = value) } },
        )
    }
    SettingSwitch(
        title = stringResource(Res.string.d_notifications),
        description = stringResource(Res.string.d_notifications_sub),
        checked = settings.showNotifications,
        onCheckedChange = { value -> SettingsStore.update { it.copy(showNotifications = value) } },
    )
    SettingSwitch(
        title = stringResource(Res.string.d_fetch_profiles),
        description = stringResource(Res.string.d_fetch_profiles_sub),
        checked = settings.fetchProfiles,
        onCheckedChange = { value -> SettingsStore.update { it.copy(fetchProfiles = value) } },
    )
    if (AutoStart.isSupported()) {
        SettingSwitch(
            title = stringResource(Res.string.d_start_on_boot),
            description = stringResource(Res.string.d_start_on_boot_sub),
            checked = settings.startOnBoot,
            onCheckedChange = { value ->
                SettingsStore.update { it.copy(startOnBoot = value) }
                AutoStart.setEnabled(value)
            },
        )
    }
    SettingSwitch(
        title = stringResource(Res.string.d_local_bunker_server),
        description = stringResource(Res.string.d_local_bunker_server_sub, BunkerRelays.localBunkerPort()),
        checked = settings.localBunkerServer,
        onCheckedChange = { value ->
            SettingsStore.update { it.copy(localBunkerServer = value) }
            BunkerRelays.applyLocalBunkerServerSetting()
        },
    )
    LocalBunkerPortRow(settings.localBunkerPort)
}

@Composable
private fun LocalBunkerPortRow(configuredPort: Int?) {
    var portText by remember(configuredPort) { mutableStateOf(BunkerRelays.localBunkerPort().toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = portText,
            onValueChange = { portText = it.filter(Char::isDigit).take(5) },
            label = { Text(stringResource(Res.string.d_local_bunker_port)) },
            singleLine = true,
            modifier = Modifier.widthIn(max = 180.dp),
        )
        AmberOutlinedButton(
            text = stringResource(Res.string.save),
            onClick = {
                val port = portText.toIntOrNull()
                if (port == null || port !in 1024..65535) {
                    Toaster.toast(getStringBlocking(Res.string.d_local_bunker_port_invalid))
                } else {
                    val error = BunkerRelays.changeLocalBunkerPort(port)
                    Toaster.toast(getStringBlocking(error ?: Res.string.d_saved))
                }
            },
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
