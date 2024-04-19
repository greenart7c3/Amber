package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.components.IconRow
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun PermissionsScreen(
    modifier: Modifier,
    account: Account,
    accountStateViewModel: AccountStateViewModel,
    navController: NavController,
    database: AppDatabase
) {
    val lifecycleEvent = rememberLifecycleEvent()
    val localAccount = LocalPreferences.loadFromEncryptedStorage(account.keyPair.pubKey.toNpub())!!
    val applications = remember {
        mutableListOf<ApplicationEntity>()
    }
    var resetPermissions by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(lifecycleEvent) {
        scope.launch(Dispatchers.IO) {
            applications.clear()
            applications.addAll(database.applicationDao().getAll(localAccount.keyPair.pubKey.toHexKey()))
        }
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
                            LocalPreferences.deleteSavedApps(applications, database)
                            accountStateViewModel.switchUser(localAccount.keyPair.pubKey.toNpub(), Route.Permissions.route)
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

    Column(
        modifier
    ) {
        Box(
            Modifier
                .padding(16.dp)
        ) {
            IconRow(
                title = stringResource(id = R.string.reset_permissions),
                icon = Icons.Default.ClearAll,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    resetPermissions = true
                }
            )
        }

        HorizontalDivider()

        if (applications.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                Arrangement.Center,
                Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.no_permissions_granted))
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
            ) {
                items(applications.size) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .padding(16.dp)
                                .weight(0.9f)
                        ) {
                            val localPermission = applications.elementAt(it)
                            IconRow(
                                title = localPermission.name.ifBlank { localPermission.key.toShortenHex() },
                                icon = Icons.AutoMirrored.Default.ArrowForward,
                                tint = MaterialTheme.colorScheme.onBackground,
                                onClick = {
                                    navController.navigate("Permission/${applications.elementAt(it).key}")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
