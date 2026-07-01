package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.greenart7c3.nostrsigner.desktop.data.AccountStore
import com.greenart7c3.nostrsigner.desktop.data.MigrationResult
import com.greenart7c3.nostrsigner.desktop.ui.theme.DesktopTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun BunkerApp(scope: CoroutineScope) {
    var loading by remember { mutableStateOf(true) }
    var migrationFailed by remember { mutableStateOf(false) }
    var accounts by remember { mutableStateOf<List<String>>(emptyList()) }
    var activePubKey by remember { mutableStateOf<String?>(null) }
    var showAddAccount by remember { mutableStateOf(false) }

    suspend fun refreshAccounts() {
        accounts = AccountStore.listAccounts()
        activePubKey = AccountStore.activeAccount()
    }

    LaunchedEffect(Unit) {
        if (AccountStore.migrateLegacyLayoutIfNeeded() is MigrationResult.Failed) {
            migrationFailed = true
        }
        refreshAccounts()
        loading = false
    }

    // When the active account is logged out but others remain, active_account has no valid
    // pointer yet — fall back to another stored account instead of dropping to SetupScreen.
    LaunchedEffect(activePubKey, accounts, loading) {
        if (!loading && activePubKey == null && accounts.isNotEmpty()) {
            AccountStore.setActive(accounts.first())
            refreshAccounts()
        }
    }

    DesktopTheme(darkTheme = false) {
        Surface {
            when {
                loading -> Text("Loading...", modifier = Modifier.padding(24.dp))
                migrationFailed -> Text(
                    "Amber Bunker found an existing account key but couldn't read it. It has been left " +
                        "untouched at ~/.amber-bunker/account.key. Please back it up or restore a working copy " +
                        "before continuing.",
                    modifier = Modifier.padding(24.dp),
                )
                activePubKey == null -> SetupScreen(
                    onGenerate = {
                        scope.launch {
                            AccountStore.generate()
                            refreshAccounts()
                        }
                    },
                    onImport = { hex ->
                        scope.launch {
                            AccountStore.import(hex)
                            refreshAccounts()
                        }
                    },
                )
                else -> {
                    AppShell(
                        pubKeyHex = activePubKey!!,
                        accounts = accounts,
                        scope = scope,
                        onSwitchAccount = { pubKeyHex ->
                            scope.launch {
                                AccountStore.setActive(pubKeyHex)
                                refreshAccounts()
                            }
                        },
                        onAddAccount = { showAddAccount = true },
                        onLogout = { pubKeyHex ->
                            scope.launch {
                                AccountStore.logout(pubKeyHex)
                                refreshAccounts()
                            }
                        },
                    )

                    if (showAddAccount) {
                        Dialog(onDismissRequest = { showAddAccount = false }) {
                            Surface {
                                SetupScreen(
                                    onGenerate = {
                                        scope.launch {
                                            AccountStore.generate()
                                            refreshAccounts()
                                            showAddAccount = false
                                        }
                                    },
                                    onImport = { hex ->
                                        scope.launch {
                                            AccountStore.import(hex)
                                            refreshAccounts()
                                            showAddAccount = false
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
