package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.greenart7c3.nostrsigner.AccountInfo
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.actions.AccountBackupDialog
import com.greenart7c3.nostrsigner.ui.components.IconRow
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.coroutines.launch

@Composable
private fun ActiveMarker(acc: AccountInfo, account: Account) {
    val isCurrentUser by remember(account) {
        derivedStateOf {
            account.keyPair.pubKey.toNpub() == acc.npub
        }
    }

    if (isCurrentUser) {
        Icon(
            imageVector = Icons.Default.RadioButtonChecked,
            contentDescription = "Active Account",
            tint = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun LogoutButton(
    acc: AccountInfo,
    accountStateViewModel: AccountStateViewModel
) {
    var logoutDialog by remember { mutableStateOf(false) }
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
                        accountStateViewModel.logOff(acc.npub)
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

    IconButton(
        onClick = { logoutDialog = true }
    ) {
        Icon(
            imageVector = Icons.Default.Logout,
            contentDescription = stringResource(R.string.logout),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier,
    accountStateViewModel: AccountStateViewModel,
    account: Account
) {
    var backupDialogOpen by remember { mutableStateOf(false) }
    var logoutDialog by remember { mutableStateOf(false) }
    var shouldShowBottomSheet by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(
        confirmValueChange = { it != SheetValue.PartiallyExpanded },
        skipPartiallyExpanded = true
    )
    val scope = rememberCoroutineScope()

    if (shouldShowBottomSheet) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = {
                scope.launch {
                    shouldShowBottomSheet = false
                    sheetState.hide()
                }
            }
        ) {
            val accounts = LocalPreferences.allSavedAccounts()

            var popupExpanded by remember { mutableStateOf(false) }
            val scrollState = rememberScrollState()

            Column(modifier = Modifier.verticalScroll(scrollState)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Account", fontWeight = FontWeight.Bold)
                }
                accounts.forEach { acc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                accountStateViewModel.switchUser(acc.npub, null)
                            }
                            .padding(16.dp, 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(acc.npub.toShortenHex() ?: "")
                                }
                                Column(modifier = Modifier.width(32.dp)) {
                                    ActiveMarker(acc, account)
                                }
                            }
                        }

                        LogoutButton(acc, accountStateViewModel)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { popupExpanded = true }) {
                        Text("Add New Account")
                    }
                }
            }

            if (popupExpanded) {
                Dialog(
                    onDismissRequest = { popupExpanded = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box {
                            LoginPage(accountStateViewModel)
                            TopAppBar(
                                title = { Text(text = "Add New Account") },
                                navigationIcon = {
                                    IconButton(onClick = { popupExpanded = false }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Back"
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

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

    Column(
        modifier
    ) {
        Box(
            Modifier
                .padding(16.dp)
        ) {
            IconRow(
                title = "Accounts",
                icon = Icons.Default.Person,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    scope.launch {
                        sheetState.show()
                        shouldShowBottomSheet = true
                    }
                }
            )
        }
        Box(
            Modifier
                .padding(16.dp)
        ) {
            IconRow(
                title = stringResource(R.string.backup_keys),
                icon = Icons.Default.Key,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    backupDialogOpen = true
                }
            )
        }
        Box(
            Modifier
                .padding(16.dp)
        ) {
            IconRow(
                title = stringResource(R.string.logout),
                icon = Icons.Default.ExitToApp,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    logoutDialog = true
                }
            )
        }
    }
    if (backupDialogOpen) {
        AccountBackupDialog(account, onClose = { backupDialogOpen = false })
    }
}
