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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.UpdateCheckFrequency
import com.greenart7c3.nostrsigner.service.DownloadState
import com.greenart7c3.nostrsigner.ui.components.TitleExplainer
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun UpdateSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(modifier = modifier) {
        // Current version
        Text(
            text = stringResource(R.string.current_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        val updater = Amber.instance.zapstoreUpdater
        if (updater == null) {
            Text(
                text = stringResource(R.string.updates_not_available),
                color = Color.Gray,
            )
            return@Column
        }

        val latestRelease by updater.latestRelease.collectAsStateWithLifecycle()
        val isChecking by updater.isChecking.collectAsStateWithLifecycle()
        val dlState by updater.downloadState.collectAsStateWithLifecycle()
        val dlProgress by updater.downloadProgress.collectAsStateWithLifecycle()
        var autoCheck by remember { mutableStateOf(Amber.instance.settings.autoCheckUpdates) }
        var frequency by remember { mutableStateOf(Amber.instance.settings.updateCheckFrequency) }

        // Status row
        val statusText = when {
            dlState == DownloadState.DOWNLOADING -> stringResource(R.string.downloading_update)
            dlState == DownloadState.INSTALLING -> stringResource(R.string.installing_update)
            dlState == DownloadState.ERROR -> stringResource(R.string.update_download_failed)
            latestRelease != null -> stringResource(R.string.update_available, latestRelease!!.version)
            isChecking -> stringResource(R.string.checking_for_updates)
            else -> stringResource(R.string.up_to_date)
        }
        val statusColor = when {
            dlState == DownloadState.ERROR -> MaterialTheme.colorScheme.error
            latestRelease != null -> MaterialTheme.colorScheme.primary
            else -> Color.Gray
        }
        Text(
            text = statusText,
            color = statusColor,
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

        // Action button
        Button(
            onClick = {
                if (latestRelease != null && dlState == DownloadState.IDLE) {
                    updater.downloadAndInstall(context, latestRelease!!)
                } else if (!isChecking && dlState != DownloadState.DOWNLOADING) {
                    LocalPreferences.setLastUpdateCheckTime(context, 0L)
                    updater.checkForUpdates()
                }
            },
            enabled = !isChecking && dlState != DownloadState.DOWNLOADING,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (latestRelease != null && dlState == DownloadState.IDLE) {
                    stringResource(R.string.download_and_install_update)
                } else {
                    stringResource(R.string.check_for_updates_now)
                },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Auto-check toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.auto_check_updates),
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = autoCheck,
                onCheckedChange = { enabled ->
                    autoCheck = enabled
                    scope.launch(Dispatchers.IO) {
                        LocalPreferences.updateAutoCheckUpdates(context, enabled)
                    }
                },
            )
        }

        // Frequency selector (only when auto-check is on)
        if (autoCheck) {
            Spacer(modifier = Modifier.height(8.dp))

            val frequencyOptions = UpdateCheckFrequency.entries.map {
                TitleExplainer(stringResource(it.resourceId))
            }.toImmutableList()

            SettingsRow(
                name = R.string.update_check_frequency,
                description = null,
                selectedItems = frequencyOptions,
                selectedIndex = UpdateCheckFrequency.entries.indexOf(frequency),
                onSelect = { index ->
                    val selected = UpdateCheckFrequency.entries[index]
                    frequency = selected
                    scope.launch(Dispatchers.IO) {
                        LocalPreferences.updateUpdateCheckFrequency(context, selected)
                    }
                },
            )
        }
    }
}
