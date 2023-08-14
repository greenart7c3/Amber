package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.toNpub
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppBar(drawerState: DrawerState, accountStateViewModel: AccountStateViewModel, account: Account) {
    val scope = rememberCoroutineScope()
    TopAppBar(
        title = { Text(text = "Amber") },
        navigationIcon = {
            IconButton(
                onClick = {
                    scope.launch {
                        drawerState.open()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            LogoutButton(
                accountStateViewModel = accountStateViewModel,
                account = account
            )
        }
    )
}

@Composable
fun LogoutButton(accountStateViewModel: AccountStateViewModel, account: Account) {
    var logoutDialog by remember { mutableStateOf(false) }
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
    IconButton(
        onClick = {
            logoutDialog = true
        }
    ) {
        Icon(
            imageVector = Icons.Default.Logout,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}
