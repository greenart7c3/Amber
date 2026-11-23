package com.greenart7c3.nostrsigner.ui

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.rememberUntil
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.action
import com.greenart7c3.nostrsigner.commonsui.activity
import com.greenart7c3.nostrsigner.commonsui.add_permission
import com.greenart7c3.nostrsigner.commonsui.allow
import com.greenart7c3.nostrsigner.commonsui.ask
import com.greenart7c3.nostrsigner.commonsui.automatically_sign_this_for
import com.greenart7c3.nostrsigner.commonsui.connected_app_warning
import com.greenart7c3.nostrsigner.commonsui.copy_to_clipboard
import com.greenart7c3.nostrsigner.commonsui.delete
import com.greenart7c3.nostrsigner.commonsui.deny
import com.greenart7c3.nostrsigner.commonsui.edit_configuration
import com.greenart7c3.nostrsigner.commonsui.edit_permissions_description
import com.greenart7c3.nostrsigner.commonsui.for_all_relays
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.localizedBlocking
import com.greenart7c3.nostrsigner.commonsui.remove_all_permissions
import com.greenart7c3.nostrsigner.commonsui.sign
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.actions.RemoveAllPermissionsDialog
import com.greenart7c3.nostrsigner.ui.components.AddPermissionsSheet
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.AmberToggles
import com.greenart7c3.nostrsigner.ui.components.LabeledBorderBox
import com.greenart7c3.nostrsigner.ui.components.TrustScoreBadge
import com.greenart7c3.nostrsigner.ui.theme.orange
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun EditPermission(
    account: Account,
    selectedPackage: String,
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: EditPermissionViewModel = viewModel(factory = EditPermissionViewModel.factory(LocalAccountRepository.current)),
) {
    val permissions = remember {
        mutableStateListOf<PermissionGrant>()
    }
    var applicationData by remember {
        mutableStateOf(PermissionsApp())
    }

    LaunchedEffect(selectedPackage) {
        val (perms, app) = viewModel.load(account.npub, selectedPackage) ?: return@LaunchedEffect

        permissions.clear()
        permissions.addAll(perms)

        applicationData = app
    }

    Column(
        modifier = modifier,
    ) {
        // Only a loaded bunker connection has relays, so the URI is never shown before the app loads.
        if (applicationData.relays.isNotEmpty()) {
            BunkerUriSection(bunkerUriOf(applicationData, account.hexKey), isConnected = applicationData.isConnected)
        }

        AppLinks(navController, applicationData.key)

        if (applicationData.key.isNotEmpty()) {
            AddPermissionsButton(
                existingPermissions = permissions.map { Permission(it.type, it.kind) },
                onAdd = { addedPermissions ->
                    val newPermissions = alwaysAllowed(addedPermissions)
                    permissions.addAll(newPermissions)
                    viewModel.save(account.npub, applicationData.key, newPermissions)
                },
            )
        }

        permissions.forEach { permission ->
            PermissionRow(
                permission = permission,
                onToggle = { updated ->
                    val index = permissions.indexOfFirst { it.id == updated.id }
                    if (index != -1) {
                        permissions[index] = updated
                    }

                    viewModel.save(account.npub, applicationData.key, listOf(updated))
                },
            )
        }

        if (permissions.isNotEmpty()) {
            RemoveAllPermissionsButton(
                onRemoveAll = { onRemoved ->
                    viewModel.removeAll(account.npub, applicationData.key) {
                        permissions.clear()
                        onRemoved()
                    }
                },
            )
        }
    }
}

/** The connection's bunker:// URI: its own key (else the account's), relays and, when it uses one, secret. */
private fun bunkerUriOf(
    app: PermissionsApp,
    accountHex: String,
): String {
    val relays = app.relays.joinToString("&") { "relay=${it.url}" }
    val secret = if (app.useSecret) "&secret=${app.secret}" else ""
    return "bunker://${app.localPubKey.ifEmpty { accountHex }}?$relays$secret"
}

/** Grants for [added] permissions, allowed until revoked. */
private fun alwaysAllowed(added: List<Permission>): List<PermissionGrant> = added.map {
    PermissionGrant(
        null,
        it.type.uppercase(),
        it.kind,
        true,
        RememberType.ALWAYS.screenCode,
        Long.MAX_VALUE / 1000,
        0,
    )
}

/** Removes every permission after a confirmation; [onRemoveAll] calls back once they are gone. */
@Composable
private fun RemoveAllPermissionsButton(onRemoveAll: (onRemoved: () -> Unit) -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    if (confirming) {
        RemoveAllPermissionsDialog(
            onCancel = {
                confirming = false
            },
        ) {
            onRemoveAll { confirming = false }
        }
    }
    AmberButton(
        modifier = Modifier.padding(top = 60.dp, bottom = 60.dp),
        colors = ButtonDefaults.buttonColors().copy(
            containerColor = orange,
        ),
        onClick = {
            confirming = true
        },
        text = stringResource(Res.string.remove_all_permissions),
        textColor = Color.White,
    )
}

/** A bunker connection's URI with a copy button, under a warning when the app is connected. */
@Composable
private fun BunkerUriSection(
    bunkerUri: String,
    isConnected: Boolean,
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column {
        if (isConnected) {
            Text(
                stringResource(Res.string.connected_app_warning),
            )
            Spacer(Modifier.height(12.dp))
        }

        Text(
            bunkerUri,
            Modifier
                .padding(bottom = 8.dp),
            textAlign = TextAlign.Start,
            fontSize = 18.sp,
        )

        AmberButton(
            modifier = Modifier
                .fillMaxWidth(),
            onClick = {
                scope.launch {
                    clipboardManager.setClipEntry(
                        ClipEntry(
                            ClipData.newPlainText("", bunkerUri),
                        ),
                    )
                }
            },
            text = stringResource(Res.string.copy_to_clipboard),
        )

        Spacer(Modifier.height(12.dp))
    }
}

/** The app's activity and configuration screens, and what the permissions below do. */
@Composable
private fun AppLinks(
    navController: NavController,
    appKey: String,
) {
    Column {
        AmberButton(
            modifier = Modifier.padding(top = 20.dp),
            onClick = {
                navController.navigate("Activity/$appKey")
            },
            text = stringResource(Res.string.activity),
        )

        AmberButton(
            modifier = Modifier.padding(bottom = 20.dp),
            onClick = {
                navController.navigate("EditConfiguration/$appKey")
            },
            text = stringResource(Res.string.edit_configuration),
        )

        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            text = stringResource(Res.string.edit_permissions_description),
        )
    }
}

/** The Add permission button and the sheet it opens. */
@Composable
private fun AddPermissionsButton(
    existingPermissions: List<Permission>,
    onAdd: (List<Permission>) -> Unit,
) {
    var showAddPermissions by remember { mutableStateOf(false) }
    if (showAddPermissions) {
        AddPermissionsSheet(
            existingPermissions = existingPermissions,
            onAdd = onAdd,
            onDismiss = {
                showAddPermissions = false
            },
        )
    }

    AmberButton(
        modifier = Modifier.padding(bottom = 20.dp),
        onClick = {
            showAddPermissions = true
        },
        text = stringResource(Res.string.add_permission),
    )
}

fun onSetPermission(optionIndex: Int, rememberType: RememberType, permission: PermissionGrant, onToggle: (PermissionGrant) -> Unit) {
    val time = rememberUntil(rememberType.screenCode, TimeUtils.now())
    val isAcceptable = optionIndex == 0 || optionIndex == 2

    onToggle(
        permission.copy(
            acceptable = isAcceptable,
            acceptUntil = if (optionIndex == 2) {
                0L
            } else if (isAcceptable) {
                time
            } else {
                0L
            },
            rejectUntil = if (optionIndex == 2) {
                0L
            } else if (!isAcceptable) {
                time
            } else {
                0L
            },
            rememberType = rememberType.screenCode,
        ),
    )
}

@Composable
fun PermissionRow(
    permission: PermissionGrant,
    onToggle: (PermissionGrant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = remember(permission.type, permission.kind, permission.acceptable, permission.relay) { permissionLabel(permission) }
    var optionIndex by remember { mutableIntStateOf(actionOption(permission)) }
    var rememberType by remember {
        mutableStateOf(
            parseRememberType(permission.rememberType).let {
                if (it == RememberType.NEVER) RememberType.ALWAYS else it
            },
        )
    }

    Column(
        modifier = modifier
            .padding(4.dp)
            .fillMaxWidth()
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                RoundedCornerShape(6.dp),
            )
            .padding(4.dp),
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            text = message,
            style = MaterialTheme.typography.bodyLarge,
        )

        if (permission.kind == 22242 && permission.relay.isNotEmpty()) {
            AuthRelayLabel(permission.relay)
        }

        ActionToggle(
            optionIndex,
            onSelect = { newIndex ->
                optionIndex = newIndex
                onSetPermission(optionIndex, rememberType, permission, onToggle)
            },
        )

        if (optionIndex != 2) {
            RememberForToggle(
                rememberType,
                onSelect = { newType ->
                    rememberType = newType
                    onSetPermission(optionIndex, rememberType, permission, onToggle)
                },
            )
        }
    }
}

/** "Sign <kind>" for an event or NIP permission, else the permission's own description. */
private fun permissionLabel(permission: PermissionGrant): String {
    val localPermission = Permission(permission.type.lowercase(), permission.kind)
    return if (permission.type == "SIGN_EVENT" || permission.type == "NIP") {
        getStringBlocking(Res.string.sign, localPermission.localizedBlocking())
    } else {
        localPermission.localizedBlocking(permission.type == SignerType.CONNECT.name)
    }
}

/** The action a stored grant shows: 0 allow, 1 deny, 2 ask (no active decision). */
private fun actionOption(permission: PermissionGrant): Int = when {
    permission.acceptable && permission.acceptUntil > 0 -> 0
    !permission.acceptable && permission.rejectUntil > 0 -> 1
    else -> 2
}

/** The relay a relay-auth grant is for; `*` is every relay. */
@Composable
private fun AuthRelayLabel(relay: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        text = if (relay == "*") {
            getStringBlocking(Res.string.for_all_relays)
        } else {
            relay
        },
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Allow (0), deny (1) or ask (2). */
@Composable
private fun ActionToggle(
    optionIndex: Int,
    onSelect: (Int) -> Unit,
) {
    LabeledBorderBox(
        label = stringResource(Res.string.action),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        AmberToggles(
            selected = optionIndex,
            options = listOf(0, 1, 2),
            onSelect = onSelect,
            label = {
                stringResource(
                    when (it) {
                        0 -> Res.string.allow
                        1 -> Res.string.deny
                        else -> Res.string.ask
                    },
                )
            },
        )
    }
}

/** How long an allow or deny lasts. */
@Composable
private fun RememberForToggle(
    rememberType: RememberType,
    onSelect: (RememberType) -> Unit,
) {
    LabeledBorderBox(
        label = stringResource(Res.string.automatically_sign_this_for),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        AmberToggles(
            selected = rememberType,
            options = listOf(
                RememberType.FIVE_MINUTES,
                RememberType.TEN_MINUTES,
                RememberType.ONE_HOUR,
                RememberType.ONE_DAY,
                RememberType.ONE_WEEK,
                RememberType.ALWAYS,
            ),
            onSelect = onSelect,
            label = { stringResource(it.shortResourceId) },
        )
    }
}

@Composable
fun RelayCard(
    relay: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trustScore: Int? = null,
    isLoadingScore: Boolean = false,
    showTrustScore: Boolean = true,
) {
    Card(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        border = BorderStroke(1.dp, Color.LightGray),
        colors = CardDefaults.cardColors().copy(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                relay,
                Modifier
                    .weight(1f)
                    .padding(8.dp)
                    .padding(start = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Excluded relays (private/anonymous endpoints) show no badge at
            // all: UNKNOWN would read as "untrusted" for a relay the ratings
            // API will never be asked about.
            if (showTrustScore) {
                TrustScoreBadge(
                    score = trustScore,
                    isLoading = isLoadingScore,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            IconButton(
                onClick = onClick,
            ) {
                Icon(
                    ImageVector.vectorResource(R.drawable.delete),
                    stringResource(Res.string.delete),
                )
            }
        }
    }
}
