package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.AlertDialog
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ScaffoldState
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.toNpub
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.ShowQRDialog
import com.greenart7c3.nostrsigner.ui.actions.AccountBackupDialog
import kotlinx.coroutines.launch

@Composable
fun Drawer(
    accountStateViewModel: AccountStateViewModel,
    account: Account,
    scaffoldState: ScaffoldState
) {
    var logoutDialog by remember { mutableStateOf(false) }
    var backupDialogOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (logoutDialog) {
        AlertDialog(
            title = {
                Text(text = "Logout")
            },
            text = {
                Text(text = "Logging out deletes all your local information. Make sure to have your private keys backed up to avoid losing your account. Do you want to continue?")
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
                    Text(text = "Logout")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        logoutDialog = false
                    }
                ) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colors.background
    ) {
        Column {
            Spacer(modifier = Modifier.weight(1f))
            IconRow(
                title = "Backup Keys",
                icon = Icons.Default.Key,
                tint = MaterialTheme.colors.onBackground,
                onClick = {
                    scope.launch {
                        scaffoldState.drawerState.close()
                    }
                    backupDialogOpen = true
                }
            )
            BottomContent(account, scaffoldState)
        }
    }
    if (backupDialogOpen) {
        AccountBackupDialog(account, onClose = { backupDialogOpen = false })
    }
    BackHandler(enabled = scaffoldState.drawerState.isOpen) {
        scope.launch { scaffoldState.drawerState.close() }
    }
}

@Composable
fun BottomContent(account: Account, scaffoldState: ScaffoldState) {
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
                    scaffoldState.drawerState.close()
                }
            }) {
                Icon(
                    Icons.Default.QrCode,
                    null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colors.primary
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
