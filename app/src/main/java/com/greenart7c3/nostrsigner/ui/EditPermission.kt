package com.greenart7c3.nostrsigner.ui

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.greenart7c3.nostrsigner.ui.actions.RemoveAllPermissionsDialog
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.theme.orange
import com.vitorpamplona.quartz.encoders.toHexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("StringFormatInvalid")
@Composable
fun EditPermission(
    modifier: Modifier,
    account: Account,
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
        mutableStateOf("bunker://${account.signer.keyPair.pubKey.toHexKey()}?$relayString$secret")
    }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            permissions.addAll(database.applicationDao().getAllByKey(selectedPackage).sortedBy { "${it.type}-${it.kind}" })
            applicationData = database.applicationDao().getByKey(selectedPackage)!!.application
            checked = applicationData.useSecret
            val relays = applicationData.relays.joinToString(separator = "&") { "relay=${it.url}" }
            val localSecret = if (checked) "&secret=${applicationData.secret}" else ""
            bunkerUri = "bunker://${account.signer.keyPair.pubKey.toHexKey()}?$relays$localSecret"
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
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        if (!applicationData.isConnected) {
            Text(
                bunkerUri,
                Modifier
                    .padding(8.dp),
                textAlign = TextAlign.Start,
                fontSize = 18.sp,
            )

            AmberButton(
                modifier = Modifier
                    .fillMaxWidth(),
                onClick = {
                    clipboardManager.setText(AnnotatedString(bunkerUri))
                },
                content = {
                    Text(stringResource(R.string.copy_to_clipboard))
                },
            )

            Spacer(Modifier.height(12.dp))
        }

        AmberButton(
            modifier = Modifier.padding(top = 20.dp),
            onClick = {
                navController.navigate("Activity/${applicationData.key}")
            },
            content = {
                Text(stringResource(R.string.activity))
            },
        )

        AmberButton(
            modifier = Modifier.padding(bottom = 20.dp),
            onClick = {
                navController.navigate("EditConfiguration/${applicationData.key}")
            },
            content = {
                Text(stringResource(R.string.edit_configuration))
            },
        )

        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            text = stringResource(R.string.edit_permissions_description),
        )

        permissions.forEachIndexed { _, permission ->
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

            Card(
                modifier = Modifier
                    .padding(4.dp)
                    .fillMaxWidth(),
                border = BorderStroke(1.dp, Color.Gray),
                colors = CardDefaults.cardColors().copy(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
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

        AmberButton(
            colors = ButtonDefaults.buttonColors().copy(
                containerColor = orange,
            ),
            modifier = Modifier.padding(bottom = 20.dp),
            onClick = {
                wantsToRemovePermissions = true
            },
            content = {
                Text(
                    stringResource(R.string.remove_all_permissions),
                    color = Color.White,
                )
            },
        )
    }
}
