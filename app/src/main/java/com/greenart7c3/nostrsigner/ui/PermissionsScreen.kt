package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.components.IconRow
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.coroutines.launch

@Composable
fun PermissionsScreen(
    modifier: Modifier,
    account: Account,
    accountStateViewModel: AccountStateViewModel
) {
    val lifecycleEvent = rememberLifecycleEvent()

    val localAccount = LocalPreferences.loadFromEncryptedStorage(account.keyPair.pubKey.toNpub())
    val savedApps = localAccount!!.savedApps
    val applications = savedApps.keys.toList().map {
        it.split("-").first()
    }.toSet()
    var resetPermissions by remember { mutableStateOf(false) }
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var permissions by remember { mutableStateOf(listOf<String>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(lifecycleEvent) {
        if (lifecycleEvent == Lifecycle.Event.ON_RESUME) {
            selectedPackage = null
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
                            LocalPreferences.deleteSavedApps(localAccount)
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

    if (selectedPackage !== null) {
        Dialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = {
                selectedPackage = null
            }
        ) {
            Surface(
                Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .fillMaxSize()
                ) {
                    Text(
                        selectedPackage!!,
                        Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp
                    )
                    LazyColumn(
                        Modifier.weight(1f)
                    ) {
                        itemsIndexed(permissions, { index, _ -> index }) { index, permission ->
                            val message = when (permission) {
                                "NIP04_DECRYPT" -> stringResource(R.string.decrypt_nip_04_data)
                                "NIP44_DECRYPT" -> stringResource(R.string.decrypt_nip_44_data)
                                "NIP44_ENCRYPT" -> stringResource(R.string.encrypt_nip_44_data)
                                "NIP04_ENCRYPT" -> stringResource(R.string.encrypt_nip_04_data)
                                "DECRYPT_ZAP_EVENT" -> stringResource(R.string.decrypt_zap_data)
                                "GET_PUBLIC_KEY" -> stringResource(R.string.read_your_public_key)
                                else -> "Sign event kind ${permission.split("-").last()}"
                            }
                            Row(
                                modifier = Modifier
                                    .padding(vertical = 15.dp, horizontal = 25.dp)
                                    .fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = message,
                                        fontSize = 18.sp
                                    )
                                }
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clickable {
                                            permissions =
                                                permissions.filterIndexed { i, _ -> i != index }
                                        },
                                    tint = Color.Red
                                )
                            }
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth(),
                        Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                selectedPackage = null
                            },
                            Modifier.padding(6.dp)
                        ) {
                            Text(stringResource(id = R.string.cancel))
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    val localSaved = localAccount.savedApps.filter { !it.key.contains(selectedPackage!!) }.toMutableMap()
                                    permissions.forEach {
                                        localSaved["$selectedPackage-$it"] = true
                                    }
                                    localAccount.savedApps = localSaved
                                    LocalPreferences.saveToEncryptedStorage(localAccount)
                                    selectedPackage = null
                                    accountStateViewModel.switchUser(localAccount.keyPair.pubKey.toNpub(), Route.Permissions.route)
                                }
                            },
                            Modifier.padding(6.dp)
                        ) {
                            Text(stringResource(id = R.string.confirm))
                        }
                    }
                }
            }
        }
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

        Divider()

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
                    Box(
                        Modifier
                            .padding(16.dp)
                    ) {
                        IconRow(
                            title = applications.elementAt(it),
                            icon = Icons.Default.ArrowForward,
                            tint = MaterialTheme.colorScheme.onBackground,
                            onClick = {
                                permissions = localAccount.savedApps.keys.filter { item -> item.startsWith(applications.elementAt(it)) }.map { item ->
                                    item.replace("${applications.elementAt(it)}-", "")
                                }
                                selectedPackage = applications.elementAt(it)
                            }
                        )
                    }
                }
            }
        }
    }
}
