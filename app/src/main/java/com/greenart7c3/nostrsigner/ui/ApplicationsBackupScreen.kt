package com.greenart7c3.nostrsigner.ui

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.ApplicationBackup
import com.greenart7c3.nostrsigner.service.RestoreResult
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.ProfileSubscriptionEffect
import com.greenart7c3.nostrsigner.ui.theme.fromHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ApplicationsBackupScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val accounts by produceState<List<Account>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { LocalPreferences.allAccounts(context) }
    }

    val loadedAccounts = accounts
    var isBusy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Account?>(null) }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.applications_backup_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (loadedAccounts == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            loadedAccounts.forEachIndexed { index, account ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                }
                AccountBackupRow(
                    account = account,
                    isBusy = isBusy,
                    onBusyChange = { isBusy = it },
                    onRequestRestore = { pendingRestore = account },
                )
            }
        }
    }

    pendingRestore?.let { account ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    isBusy = true
                    val scope = Amber.instance.applicationIOScope
                    scope.launch(Dispatchers.IO) {
                        val result = ApplicationBackup.restoreFromRelays(account.npub, account)
                        withContext(Dispatchers.Main) {
                            val msg = when (result) {
                                is RestoreResult.Success -> context.getString(R.string.restore_success, result.apps, result.permissions)
                                is RestoreResult.NoBackupFound -> context.getString(R.string.restore_no_backup_found)
                                is RestoreResult.Failed -> context.getString(R.string.restore_failed, result.message)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            isBusy = false
                        }
                    }
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.no)) }
            },
        )
    }
}

@Composable
private fun AccountBackupRow(
    account: Account,
    isBusy: Boolean,
    onBusyChange: (Boolean) -> Unit,
    onRequestRestore: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    ProfileSubscriptionEffect(account)
    val name by account.name.collectAsState()
    val picture by account.picture.collectAsState()
    var backupEnabled by remember(account.npub) {
        mutableStateOf(LocalPreferences.getBackupApplications(context, account.npub))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            AccountAvatar(account = account, picture = picture)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (name.isNotBlank()) {
                    Text(text = name)
                }
                Text(
                    text = account.npub.toShortenHex(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
            Switch(
                checked = backupEnabled,
                onCheckedChange = { enabled ->
                    backupEnabled = enabled
                    scope.launch(Dispatchers.IO) {
                        LocalPreferences.setBackupApplications(context, account.npub, enabled)
                        if (LocalPreferences.anyAccountBackupEnabled(context)) {
                            Amber.instance.startBackupApplicationsAlarm()
                        } else {
                            Amber.instance.cancelBackupApplicationsAlarm()
                        }
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        AmberButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.backup_now),
            enabled = !isBusy,
            onClick = {
                if (isBusy) return@AmberButton
                onBusyChange(true)
                scope.launch(Dispatchers.IO) {
                    val ok = ApplicationBackup.publishBackup(account.npub, account)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            if (ok) context.getString(R.string.backup_success) else context.getString(R.string.backup_failed),
                            Toast.LENGTH_LONG,
                        ).show()
                        onBusyChange(false)
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
                if (!isBusy) onRequestRestore()
            },
        )
    }
}

@Composable
private fun AccountAvatar(
    account: Account,
    picture: String,
) {
    val borderColor = Color.fromHex(account.hexKey.slice(0..5))
    val fallback: @Composable () -> Unit = {
        Icon(
            Icons.Outlined.Person,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .border(2.dp, borderColor, CircleShape),
        )
    }

    if (picture.isNotBlank() && !BuildFlavorChecker.isOfflineFlavor()) {
        SubcomposeAsyncImage(
            model = picture,
            contentDescription = null,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .size(40.dp),
            loading = { CenterCircularProgressIndicator(Modifier) },
            error = { fallback() },
        )
    } else {
        fallback()
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
