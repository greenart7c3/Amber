package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ScaffoldState
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.toNpub
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import kotlinx.coroutines.launch

@Composable
fun MainAppBar(scaffoldState: ScaffoldState, accountStateViewModel: AccountStateViewModel, account: Account) {
    val scope = rememberCoroutineScope()
    TopAppBar(
        title = { Text(text = "Amber") },
        navigationIcon = {
            IconButton(
                onClick = {
                    scope.launch {
                        scaffoldState.drawerState.open()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = null,
                    tint = MaterialTheme.colors.onSurface
                )
            }
        },
        actions = {
            LogoutButton(
                accountStateViewModel = accountStateViewModel,
                account = account
            )
        },
        backgroundColor = Color.Transparent,
        elevation = 0.dp
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
            tint = MaterialTheme.colors.onSurface
        )
    }
}
