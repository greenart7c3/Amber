package com.greenart7c3.nostrsigner.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.ApplicationBackup
import com.greenart7c3.nostrsigner.service.RestoreResult
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ApplicationsBackupScreen(
    modifier: Modifier = Modifier,
    account: Account,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backupApplications by remember { mutableStateOf(account.backupApplications) }
    var isBusy by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable {
                    val newValue = !backupApplications
                    backupApplications = newValue
                    account.backupApplications = newValue
                    if (newValue) {
                        scope.launch(Dispatchers.IO) {
                            Amber.instance.startBackupApplicationsAlarm()
                        }
                    }
                },
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.enable_applications_backup))
                Text(
                    text = stringResource(R.string.applications_backup_explainer),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
            Switch(
                checked = backupApplications,
                onCheckedChange = { enabled ->
                    backupApplications = enabled
                    account.backupApplications = enabled
                    if (enabled) {
                        scope.launch(Dispatchers.IO) {
                            Amber.instance.startBackupApplicationsAlarm()
                        }
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        AmberButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.backup_now),
            enabled = !isBusy,
            onClick = {
                if (isBusy) return@AmberButton
                isBusy = true
                scope.launch(Dispatchers.IO) {
                    val ok = ApplicationBackup.publishBackup(account.npub, account)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            if (ok) context.getString(R.string.backup_success) else context.getString(R.string.backup_failed),
                            Toast.LENGTH_LONG,
                        ).show()
                        isBusy = false
                    }
                }
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        AmberButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.restore_from_relays),
            enabled = !isBusy,
            onClick = {
                if (!isBusy) showRestoreConfirm = true
            },
        )
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    isBusy = true
                    scope.launch(Dispatchers.IO) {
                        val result = ApplicationBackup.restoreFromRelays(account)
                        withContext(Dispatchers.Main) {
                            val msg = when (result) {
                                is RestoreResult.Success -> context.getString(R.string.restore_success, result.apps, result.permissions)
                                is RestoreResult.NoBackupFound -> context.getString(R.string.restore_no_backup_found)
                                is RestoreResult.Failed -> context.getString(R.string.restore_failed, result.message)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            backupApplications = account.backupApplications
                            isBusy = false
                        }
                    }
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text(stringResource(R.string.no)) }
            },
        )
    }
}

@Composable
fun RestoreBackupDialog(accountStateViewModel: AccountStateViewModel) {
    val state by accountStateViewModel.restorePrompt.collectAsState()
    val found = state as? RestorePromptState.Found ?: return
    val context = LocalContext.current
    val apps = found.payload.applications.size
    val perms = found.payload.applications.sumOf { it.permissions.size }

    AlertDialog(
        onDismissRequest = { accountStateViewModel.dismissRestore() },
        title = { Text(stringResource(R.string.signin_restore_title)) },
        text = { Text(stringResource(R.string.signin_restore_message, apps, perms)) },
        confirmButton = {
            TextButton(onClick = {
                accountStateViewModel.acceptRestore { result ->
                    val msg = when (result) {
                        is RestoreResult.Success -> context.getString(R.string.restore_success, result.apps, result.permissions)
                        is RestoreResult.NoBackupFound -> context.getString(R.string.restore_no_backup_found)
                        is RestoreResult.Failed -> context.getString(R.string.restore_failed, result.message)
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }) { Text(stringResource(R.string.signin_restore_accept)) }
        },
        dismissButton = {
            TextButton(onClick = { accountStateViewModel.dismissRestore() }) {
                Text(stringResource(R.string.signin_restore_skip))
            }
        },
    )
}
