package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.ShowQRDialog
import com.greenart7c3.nostrsigner.ui.actions.AccountBackupDialog
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.coroutines.launch

@Composable
fun Drawer(
    accountStateViewModel: AccountStateViewModel,
    account: Account,
    drawerState: DrawerState
) {
    var logoutDialog by remember { mutableStateOf(false) }
    var resetPermissions by remember { mutableStateOf(false) }
    var backupDialogOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (logoutDialog) {
        AlertDialog(
            title = {
                Text(text = stringResource(R.string.logout))
            },
            text = {
                Text(text = stringResource(R.string.logging_out_deletes_all_your_local_information_make_sure_to_have_your_private_keys_backed_up_to_avoid_losing_your_account_do_you_want_to_continue))
            },
            onDismissRequest = {
                logoutDialog = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        logoutDialog = false
                        accountStateViewModel.logOff(account.keyPair.pubKey.toNpub())
                    }
                ) {
                    Text(text = stringResource(R.string.logout))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        logoutDialog = false
                    }
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    if (resetPermissions) {
        AlertDialog(
            title = {
                Text(text = stringResource(R.string.reset_permissions))
            },
            text = {
                Text(text = stringResource(R.string.do_you_want_to_reset_all_permissions))
            },
            onDismissRequest = {
                resetPermissions = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        resetPermissions = false
                        scope.launch {
                            drawerState.close()
                            LocalPreferences.deleteSavedApps(account)
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        resetPermissions = false
                    }
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column {
            Spacer(modifier = Modifier.weight(1f))
            IconRow(
                title = "Reset permissions",
                icon = Icons.Default.ClearAll,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    resetPermissions = true
                }
            )
            IconRow(
                title = "Backup Keys",
                icon = Icons.Default.Key,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    scope.launch {
                        drawerState.close()
                    }
                    backupDialogOpen = true
                }
            )
            BottomContent(account, drawerState)
        }
    }
    if (backupDialogOpen) {
        AccountBackupDialog(account, onClose = { backupDialogOpen = false })
    }
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
}

@Composable
fun BottomContent(account: Account, drawerState: DrawerState) {
    val coroutineScope = rememberCoroutineScope()

    // store the dialog open or close state
    var dialogOpen by remember {
        mutableStateOf(false)
    }

    Column(modifier = Modifier) {
        Divider(
            modifier = Modifier.padding(top = 15.dp),
            thickness = 0.25.dp
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.padding(start = 16.dp),
                text = "v" + BuildConfig.VERSION_NAME,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Box(modifier = Modifier.weight(1F))
            IconButton(onClick = {
                dialogOpen = true
                coroutineScope.launch {
                    drawerState.close()
                }
            }) {
                Icon(
                    Icons.Default.QrCode,
                    null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (dialogOpen) {
        ShowQRDialog(
            account,
            onClose = { dialogOpen = false }
        )
    }
}
