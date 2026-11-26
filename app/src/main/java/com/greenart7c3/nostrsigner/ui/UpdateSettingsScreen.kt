package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.UpdateCheckSettings
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.auto_check_updates
import com.greenart7c3.nostrsigner.commonsui.check_for_updates_now
import com.greenart7c3.nostrsigner.commonsui.checking_for_updates
import com.greenart7c3.nostrsigner.commonsui.current_version
import com.greenart7c3.nostrsigner.commonsui.download_and_install_update
import com.greenart7c3.nostrsigner.commonsui.downloading_update
import com.greenart7c3.nostrsigner.commonsui.installing_update
import com.greenart7c3.nostrsigner.commonsui.up_to_date
import com.greenart7c3.nostrsigner.commonsui.update_available
import com.greenart7c3.nostrsigner.commonsui.update_channel
import com.greenart7c3.nostrsigner.commonsui.update_channel_description
import com.greenart7c3.nostrsigner.commonsui.update_check_frequency
import com.greenart7c3.nostrsigner.commonsui.update_download_failed
import com.greenart7c3.nostrsigner.commonsui.updates_not_available
import com.greenart7c3.nostrsigner.models.UpdateChannel
import com.greenart7c3.nostrsigner.models.UpdateCheckFrequency
import com.greenart7c3.nostrsigner.service.DownloadState
import com.greenart7c3.nostrsigner.service.ZapstoreRelease
import com.greenart7c3.nostrsigner.service.ZapstoreUpdater
import com.greenart7c3.nostrsigner.ui.components.TitleExplainer
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun UpdateSettingsScreen(modifier: Modifier = Modifier) {
    val zapstoreUpdater = LocalZapstoreUpdater.current

    Column(modifier = modifier) {
        // Current version
        Text(
            text = stringResource(Res.string.current_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        val updater = zapstoreUpdater
        if (updater == null) {
            Text(
                text = stringResource(Res.string.updates_not_available),
                color = Color.Gray,
            )
            return@Column
        }

        UpdateStatus(updater)

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        AutoUpdateSettings()
    }
}

/** Whether an update is available (or downloading), and the button to check for one or install it. */
@Composable
private fun UpdateStatus(updater: ZapstoreUpdater) {
    val context = LocalContext.current
    val appScope = LocalAppScope.current
    val latestRelease by updater.latestRelease.collectAsStateWithLifecycle()
    val isChecking by updater.isChecking.collectAsStateWithLifecycle()
    val dlState by updater.downloadState.collectAsStateWithLifecycle()
    val dlProgress by updater.downloadProgress.collectAsStateWithLifecycle()

    Column {
        Text(
            text = updateStatusText(dlState, latestRelease, isChecking),
            color = updateStatusColor(dlState, latestRelease),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (dlState == DownloadState.DOWNLOADING) {
            LinearProgressIndicator(
                progress = { dlProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }

        // Installs the release when one is available, else checks again.
        val release = latestRelease?.takeIf { dlState == DownloadState.IDLE }
        Button(
            onClick = {
                appScope.launch {
                    if (release != null) {
                        updater.downloadAndInstall(context, release)
                    } else if (!isChecking && dlState != DownloadState.DOWNLOADING) {
                        UpdateCheckSettings.setLastUpdateCheckTime(context, 0L)
                        updater.checkForUpdates()
                    }
                }
            },
            enabled = !isChecking && dlState != DownloadState.DOWNLOADING,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (release != null) Res.string.download_and_install_update else Res.string.check_for_updates_now))
        }
    }
}

@Composable
private fun updateStatusText(
    dlState: DownloadState,
    latestRelease: ZapstoreRelease?,
    isChecking: Boolean,
): String = when {
    dlState == DownloadState.DOWNLOADING -> stringResource(Res.string.downloading_update)
    dlState == DownloadState.INSTALLING -> stringResource(Res.string.installing_update)
    dlState == DownloadState.ERROR -> stringResource(Res.string.update_download_failed)
    latestRelease != null -> stringResource(Res.string.update_available, latestRelease.version)
    isChecking -> stringResource(Res.string.checking_for_updates)
    else -> stringResource(Res.string.up_to_date)
}

@Composable
private fun updateStatusColor(
    dlState: DownloadState,
    latestRelease: ZapstoreRelease?,
): Color = when {
    dlState == DownloadState.ERROR -> MaterialTheme.colorScheme.error
    latestRelease != null -> MaterialTheme.colorScheme.primary
    else -> Color.Gray
}

/** Whether and how often to check for updates, and on which channel. */
@Composable
private fun AutoUpdateSettings() {
    val context = LocalContext.current
    val settingsStore = LocalSettingsStore.current
    val scope = rememberCoroutineScope()
    var autoCheck by remember { mutableStateOf(settingsStore.settings.autoCheckUpdates) }
    var frequency by remember { mutableStateOf(settingsStore.settings.updateCheckFrequency) }

    Column {
        AutoCheckRow(
            checked = autoCheck,
            onCheckedChange = { enabled ->
                autoCheck = enabled
                scope.launch(Dispatchers.IO) {
                    UpdateCheckSettings.updateAutoCheckUpdates(context, enabled)
                }
            },
        )

        // Frequency selector (only when auto-check is on)
        if (autoCheck) {
            Spacer(modifier = Modifier.height(8.dp))

            val frequencyOptions = UpdateCheckFrequency.entries.map {
                TitleExplainer(stringResource(it.resourceId))
            }.toImmutableList()

            SettingsRow(
                name = Res.string.update_check_frequency,
                description = null,
                selectedItems = frequencyOptions,
                selectedIndex = UpdateCheckFrequency.entries.indexOf(frequency),
                onSelect = { index ->
                    val selected = UpdateCheckFrequency.entries[index]
                    frequency = selected
                    scope.launch(Dispatchers.IO) {
                        UpdateCheckSettings.updateUpdateCheckFrequency(context, selected)
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        UpdateChannelSetting()
    }
}

@Composable
private fun AutoCheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.auto_check_updates),
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/** Stable or beta releases; changing it makes the next check run at once. */
@Composable
private fun UpdateChannelSetting() {
    val context = LocalContext.current
    val settingsStore = LocalSettingsStore.current
    val scope = rememberCoroutineScope()
    var channel by remember { mutableStateOf(settingsStore.settings.updateChannel) }
    val channelOptions = UpdateChannel.entries.map {
        TitleExplainer(stringResource(it.resourceId))
    }.toImmutableList()

    SettingsRow(
        name = Res.string.update_channel,
        description = Res.string.update_channel_description,
        selectedItems = channelOptions,
        selectedIndex = UpdateChannel.entries.indexOf(channel),
        onSelect = { index ->
            val selected = UpdateChannel.entries[index]
            if (selected != channel) {
                channel = selected
                scope.launch(Dispatchers.IO) {
                    UpdateCheckSettings.updateUpdateChannel(context, selected)
                    // Reset last-check timestamp so the next check actually runs
                    // and surfaces a release on the newly selected channel.
                    UpdateCheckSettings.setLastUpdateCheckTime(context, 0L)
                }
            }
        },
    )
}
