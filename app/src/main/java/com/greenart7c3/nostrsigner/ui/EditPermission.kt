package com.greenart7c3.nostrsigner.ui

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.kindToNipUrl
import com.greenart7c3.nostrsigner.models.nipToUrl
import com.greenart7c3.nostrsigner.ui.actions.EditRelaysDialog
import com.greenart7c3.nostrsigner.ui.actions.RemoveAllPermissionsDialog
import com.vitorpamplona.quartz.encoders.toHexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("StringFormatInvalid")
@Composable
fun EditPermission(
    modifier: Modifier,
    account: Account,
    accountStateViewModel: AccountStateViewModel,
    selectedPackage: String,
    navController: NavController,
    database: AppDatabase,
) {
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val permissions = remember {
        mutableStateListOf<ApplicationPermissionsEntity>()
    }
    var applicationData by remember {
        mutableStateOf(ApplicationEntity(selectedPackage, "", emptyList(), "", "", "", "", true, "", false, 1))
    }

    var wantsToRemovePermissions by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var checked by remember {
        mutableStateOf(applicationData.useSecret)
    }
    val secret = if (checked) "&secret=${applicationData.secret}" else ""
    var bunkerUri by remember {
        val relayString = NostrSigner.getInstance().settings.defaultRelays.joinToString(separator = "&") { "relay=${it.url}" }
        mutableStateOf("bunker://${account.keyPair.pubKey.toHexKey()}?$relayString$secret")
    }
    var editRelaysDialog by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            permissions.addAll(database.applicationDao().getAllByKey(selectedPackage).sortedBy { "${it.type}-${it.kind}" })
            applicationData = database.applicationDao().getByKey(selectedPackage)!!.application
            checked = applicationData.useSecret
            val relays = applicationData.relays.joinToString(separator = "&") { "relay=${it.url}" }
            val localSecret = if (checked) "&secret=${applicationData.secret}" else ""
            bunkerUri = "bunker://${account.keyPair.pubKey.toHexKey()}?$relays$localSecret"
        }
    }

    if (editRelaysDialog) {
        EditRelaysDialog(
            applicationData = applicationData,
            accountStateViewModel = accountStateViewModel,
            account = account,
            onClose = {
                editRelaysDialog = false
            },
        ) { relays2 ->
            applicationData =
                applicationData.copy(
                    relays = relays2.map { it },
                )
            val relays = applicationData.relays.joinToString(separator = "&") { "relay=${it.url}" }
            bunkerUri = "bunker://${account.keyPair.pubKey.toHexKey()}?$relays$secret"
            editRelaysDialog = false
        }
    }

    if (wantsToRemovePermissions) {
        RemoveAllPermissionsDialog(
            onCancel = {
                wantsToRemovePermissions = false
            },
        ) {
            scope.launch(Dispatchers.IO) {
                database
                    .applicationDao()
                    .deletePermissions(applicationData.key)

                permissions.clear()
                wantsToRemovePermissions = false
            }
        }
    }

    Column(
        modifier = modifier
            .padding(8.dp),
    ) {
        if (!applicationData.isConnected) {
            Text(
                bunkerUri,
                Modifier
                    .padding(8.dp),
                textAlign = TextAlign.Start,
                fontSize = 18.sp,
            )

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                onClick = {
                    clipboardManager.setText(AnnotatedString(bunkerUri))
                },
                content = {
                    Text(stringResource(R.string.copy_to_clipboard))
                },
            )

            Spacer(Modifier.height(12.dp))
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Button(
                onClick = {
                    navController.navigate("Activity/${applicationData.key}")
                },
                Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
            ) {
                Text(stringResource(R.string.activity))
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Button(
                onClick = {
                    navController.navigate("EditConfiguration/${applicationData.key}")
                },
                Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
            ) {
                Text(stringResource(R.string.edit_configuration))
            }
        }

        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            text = stringResource(R.string.edit_permissions_description),
        )

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            if (applicationData.secret.isNotEmpty() || applicationData.relays.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Button(
                        onClick = {
                            editRelaysDialog = true
                        },
                        Modifier.padding(6.dp),
                    ) {
                        Text(stringResource(R.string.relays))
                    }
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f),
        ) {
            itemsIndexed(permissions, { index, _ -> index }) { _, permission ->
                val localPermission =
                    Permission(
                        permission.type.toLowerCase(Locale.current),
                        permission.kind,
                    )

                val message =
                    if (permission.type == "SIGN_EVENT" || permission.type == "NIP") {
                        stringResource(R.string.sign, localPermission.toLocalizedString(context))
                    } else {
                        localPermission.toLocalizedString(context)
                    }
                ElevatedCard(
                    modifier = Modifier
                        .padding(4.dp)
                        .fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(vertical = 8.dp, horizontal = 8.dp)
                            .fillMaxWidth(),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                                .clickable {
                                    val localPermissions =
                                        permissions.map {
                                            if (it.id == permission.id) {
                                                it.copy(acceptable = !permission.acceptable)
                                            } else {
                                                it.copy()
                                            }
                                        }
                                    permissions.clear()
                                    permissions.addAll(localPermissions)
                                },
                        ) {
                            Switch(
                                modifier = Modifier.padding(end = 8.dp),
                                checked = permission.acceptable,
                                onCheckedChange = {
                                    val localPermissions =
                                        permissions.map {
                                            if (it.id == permission.id) {
                                                it.copy(acceptable = !permission.acceptable)
                                            } else {
                                                it.copy()
                                            }
                                        }
                                    permissions.clear()
                                    permissions.addAll(localPermissions)
                                },
                            )
                            Text(
                                modifier = Modifier.weight(1f),
                                text = message,
                                fontSize = 18.sp,
                            )
                        }
                        IconButton(
                            content = {
                                Icon(
                                    Icons.Default.Delete,
                                    stringResource(R.string.remove_permission),
                                    modifier = Modifier
                                        .fillMaxHeight(),
                                    tint = Color.Red,
                                )
                            },
                            onClick = {
                                permissions.remove(permission)
                            },
                        )
                        IconButton(
                            content = {
                                Icon(
                                    Icons.Default.Info,
                                    stringResource(R.string.more_info),
                                    modifier = Modifier
                                        .fillMaxHeight(),
                                )
                            },
                            onClick = {
                                if (permission.type.toLowerCase(Locale.current) == "sign_event") {
                                    val nip = permission.kind?.kindToNipUrl()
                                    if (nip == null) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.nip_not_found_for_the_event_kind, permission.kind.toString()),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        return@IconButton
                                    }
                                    uriHandler.openUri(nip)
                                } else if (permission.type.toLowerCase(Locale.current) == "nip") {
                                    val nip = permission.kind?.nipToUrl()
                                    if (nip == null) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.nip_not_found, permission.kind.toString()),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        return@IconButton
                                    }
                                    uriHandler.openUri(nip)
                                } else if ((permission.type.toUpperCase(Locale.current) == "NIP04_ENCRYPT") || (permission.type.toUpperCase(Locale.current) == "NIP04_DECRYPT")) {
                                    uriHandler.openUri("https://github.com/nostr-protocol/nips/blob/master/04.md")
                                } else if ((permission.type.toUpperCase(Locale.current) == "NIP44_ENCRYPT") || (permission.type.toUpperCase(Locale.current) == "NIP44_DECRYPT")) {
                                    uriHandler.openUri("https://github.com/nostr-protocol/nips/blob/master/44.md")
                                } else if (permission.type.toLowerCase(Locale.current) == "decrypt_zap_event") {
                                    uriHandler.openUri("https://github.com/nostr-protocol/nips/blob/master/57.md")
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.no_information_available_for, localPermission.toLocalizedString(context)),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth(),
            Arrangement.Center,
        ) {
            Button(
                onClick = {
                    wantsToRemovePermissions = true
                },
                Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
            ) {
                Text(stringResource(R.string.remove_all_permissions))
            }
        }

//        Row(
//            Modifier
//                .fillMaxWidth(),
//            Arrangement.Center,
//        ) {
//            Button(
//                onClick = {
//                    if (!applicationData.isConnected) {
//                        val relays = applicationData.relays.joinToString(separator = "&") { "relay=${it.url}" }
//                        val localSecret = if (applicationData.useSecret) "&secret=${applicationData.secret}" else ""
//                        val localBunkerUri = "bunker://${account.keyPair.pubKey.toHexKey()}?$relays$localSecret"
//                        clipboardManager.setText(AnnotatedString(localBunkerUri))
//                    }
//
//                    navController.navigateUp()
//                },
//                Modifier.padding(6.dp),
//            ) {
//                Text(stringResource(id = R.string.cancel))
//            }
//            Button(
//                onClick = {
//                    scope.launch(Dispatchers.IO) {
//                        val localApplicationData =
//                            applicationData.copy(
//                                name = textFieldvalue.text,
//                                useSecret = checked,
//                            )
//                        database.applicationDao().delete(applicationData)
//                        database.applicationDao().insertApplicationWithPermissions(
//                            ApplicationWithPermissions(
//                                localApplicationData,
//                                permissions,
//                            ),
//                        )
//                        if (NostrSigner.getInstance().settings.notificationType == NotificationType.DIRECT) {
//                            NostrSigner.getInstance().checkForNewRelays()
//                        }
//
//                        scope.launch(Dispatchers.Main) {
//                            navController.navigateUp()
//                            accountStateViewModel.switchUser(localAccount.keyPair.pubKey.toNpub(), Route.Applications.route)
//                        }
//                    }
//                },
//                Modifier.padding(6.dp),
//            ) {
//                Text(stringResource(id = R.string.confirm))
//            }
//        }
    }
}
