package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.common.generateBunkerPrivKey
import com.greenart7c3.nostrsigner.common.localPubKeyFromPrivKey
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.action
import com.greenart7c3.nostrsigner.commonsui.allow
import com.greenart7c3.nostrsigner.commonsui.ask
import com.greenart7c3.nostrsigner.commonsui.automatically_sign_this_for
import com.greenart7c3.nostrsigner.commonsui.cancel
import com.greenart7c3.nostrsigner.commonsui.copy
import com.greenart7c3.nostrsigner.commonsui.d_accepted
import com.greenart7c3.nostrsigner.commonsui.d_activity
import com.greenart7c3.nostrsigner.commonsui.d_application_removed
import com.greenart7c3.nostrsigner.commonsui.d_application_updated
import com.greenart7c3.nostrsigner.commonsui.d_back
import com.greenart7c3.nostrsigner.commonsui.d_connection_string
import com.greenart7c3.nostrsigner.commonsui.d_connection_string_copied
import com.greenart7c3.nostrsigner.commonsui.d_delete_permission
import com.greenart7c3.nostrsigner.commonsui.d_disconnect_delete
import com.greenart7c3.nostrsigner.commonsui.d_key_label
import com.greenart7c3.nostrsigner.commonsui.d_local_socket
import com.greenart7c3.nostrsigner.commonsui.d_no_activity
import com.greenart7c3.nostrsigner.commonsui.d_rejected
import com.greenart7c3.nostrsigner.commonsui.d_relays_label
import com.greenart7c3.nostrsigner.commonsui.d_reset_bunker_message
import com.greenart7c3.nostrsigner.commonsui.d_saved
import com.greenart7c3.nostrsigner.commonsui.deny
import com.greenart7c3.nostrsigner.commonsui.edit_permissions_description
import com.greenart7c3.nostrsigner.commonsui.for_all_kinds
import com.greenart7c3.nostrsigner.commonsui.for_all_relays
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.localizedBlocking
import com.greenart7c3.nostrsigner.commonsui.name
import com.greenart7c3.nostrsigner.commonsui.no_relays_added
import com.greenart7c3.nostrsigner.commonsui.permissions
import com.greenart7c3.nostrsigner.commonsui.relays
import com.greenart7c3.nostrsigner.commonsui.remove
import com.greenart7c3.nostrsigner.commonsui.remove_all_message
import com.greenart7c3.nostrsigner.commonsui.remove_all_permissions
import com.greenart7c3.nostrsigner.commonsui.reset_bunker
import com.greenart7c3.nostrsigner.commonsui.save
import com.greenart7c3.nostrsigner.commonsui.sign
import com.greenart7c3.nostrsigner.commonsui.update
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.AppPermissionRecord
import com.greenart7c3.nostrsigner.desktop.core.AppWithPermissions
import com.greenart7c3.nostrsigner.desktop.core.DateTimes
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.isLocalSocket
import com.greenart7c3.nostrsigner.desktop.core.toShortenHex
import java.util.UUID
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun ApplicationDetailScreen(
    account: DesktopAccount,
    appKey: String,
    onBack: () -> Unit,
) {
    val store = AmberDesktop.store(account.npub)
    val apps by store.apps.collectAsState()
    val history by store.history.collectAsState()
    val scope = rememberCoroutineScope()
    val app = apps.firstOrNull { it.app.key == appKey }
    var tab by remember { mutableStateOf(0) }

    if (app == null) {
        onBack()
        return
    }

    var name by remember { mutableStateOf(app.app.name) }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.d_back))
            }
            Column {
                Text(
                    app.app.displayName(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    buildString {
                        append(stringResource(Res.string.d_key_label, app.app.key.toShortenHex()))
                        if (app.app.isLocalSocket) append(" · ${stringResource(Res.string.d_local_socket)}")
                        if (app.app.relays.isNotEmpty()) append(" · ${stringResource(Res.string.d_relays_label, app.app.relays.joinToString())}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.name)) },
                singleLine = true,
                modifier = Modifier.widthIn(max = 420.dp).weight(1f, fill = false),
            )
            AmberOutlinedButton(
                text = stringResource(Res.string.save),
                onClick = {
                    store.upsert(app.copy(app = app.app.copy(name = name)))
                    Toaster.toast(getStringBlocking(Res.string.d_application_updated))
                },
            )
        }
        Spacer(Modifier.height(12.dp))

        // A local socket client has no relays: its tabs skip index 1.
        val isSocket = app.app.isLocalSocket
        AmberTabRow(
            selectedTabIndex = if (isSocket && tab == 2) 1 else tab,
            titles = if (isSocket) {
                listOf(stringResource(Res.string.permissions), stringResource(Res.string.d_activity))
            } else {
                listOf(stringResource(Res.string.permissions), stringResource(Res.string.relays), stringResource(Res.string.d_activity))
            },
            onSelect = { tab = if (isSocket && it == 1) 2 else it },
        )
        Spacer(Modifier.height(8.dp))

        // The bunker:// string and its reset only exist for NIP-46 connections.
        if (!isSocket) {
            // Mirrors the Android app-detail connection string (EditPermission):
            // the bunker:// URI the client app can re-paste to reconnect.
            val connectionPubKey = if (app.app.localKey.isNotEmpty()) {
                localPubKeyFromPrivKey(app.app.localKey)
            } else {
                account.hexKey
            }
            val bunkerUri = remember(connectionPubKey, app.app.relays, app.app.useSecret, app.app.secret) {
                val relayParams = app.app.relays.joinToString(separator = "&") { "relay=$it" }
                val localSecret = if (app.app.useSecret) "&secret=${app.app.secret}" else ""
                "bunker://$connectionPubKey?$relayParams$localSecret"
            }
            val copyText = rememberCopyText()
            Spacer(Modifier.height(16.dp))
            Text(stringResource(Res.string.d_connection_string), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                bunkerUri,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(8.dp))
                    .padding(8.dp),
            )
            AmberTextButton(
                text = stringResource(Res.string.copy).trim(),
                onClick = {
                    copyText(bunkerUri)
                    Toaster.toast(getStringBlocking(Res.string.d_connection_string_copied))
                },
            )
            Spacer(Modifier.height(12.dp))

            // Mirrors the Android "Reset Bunker" (EditConfigurationScreen): a new
            // key and secret invalidate the old pairing; permission rules stay.
            var showResetBunker by remember { mutableStateOf(false) }
            if (showResetBunker) {
                AlertDialog(
                    onDismissRequest = { showResetBunker = false },
                    title = { Text(stringResource(Res.string.reset_bunker)) },
                    text = { Text(stringResource(Res.string.d_reset_bunker_message)) },
                    confirmButton = {
                        AmberTextButton(
                            text = stringResource(Res.string.reset_bunker),
                            onClick = {
                                showResetBunker = false
                                val oldKey = app.app.key
                                val newSecret = UUID.randomUUID().toString()
                                store.upsert(
                                    AppWithPermissions(
                                        app = app.app.copy(
                                            key = newSecret,
                                            isConnected = false,
                                            secret = newSecret,
                                            useSecret = true,
                                            localKey = generateBunkerPrivKey(),
                                        ),
                                        permissions = app.permissions,
                                    ),
                                )
                                store.delete(oldKey)
                                scope.launch {
                                    AmberDesktop.engine.checkForNewRelaysAndUpdateAllFilters()
                                }
                                Toaster.toast(getStringBlocking(Res.string.d_saved))
                            },
                        )
                    },
                    dismissButton = {
                        AmberTextButton(
                            text = stringResource(Res.string.cancel),
                            onClick = { showResetBunker = false },
                        )
                    },
                )
            }
            AmberOutlinedButton(
                text = stringResource(Res.string.reset_bunker),
                onClick = { showResetBunker = true },
            )
            Spacer(Modifier.height(12.dp))
        }

        var showRemoveAll by remember { mutableStateOf(false) }
        if (showRemoveAll) {
            AlertDialog(
                onDismissRequest = { showRemoveAll = false },
                title = { Text(stringResource(Res.string.remove)) },
                text = { Text(stringResource(Res.string.remove_all_message)) },
                confirmButton = {
                    AmberTextButton(
                        text = stringResource(Res.string.remove),
                        onClick = {
                            showRemoveAll = false
                            store.upsert(app.copy(permissions = mutableListOf()))
                        },
                    )
                },
                dismissButton = {
                    AmberTextButton(
                        text = stringResource(Res.string.cancel),
                        onClick = { showRemoveAll = false },
                    )
                },
            )
        }

        when (tab) {
            0 -> {
                val permissionsState = rememberLazyListState()
                ScrollbarBox(rememberScrollbarAdapter(permissionsState), Modifier.weight(1f)) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = permissionsState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        item {
                            Text(
                                stringResource(Res.string.edit_permissions_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(app.permissions.size) { index ->
                            val permission = app.permissions[index]
                            PermissionCard(
                                permission = permission,
                                onChange = { updated ->
                                    val newPermissions = app.permissions.toMutableList()
                                    newPermissions[index] = updated
                                    store.upsert(app.copy(permissions = newPermissions))
                                },
                                onDelete = {
                                    val newPermissions = app.permissions.toMutableList()
                                    newPermissions.removeAt(index)
                                    store.upsert(app.copy(permissions = newPermissions))
                                },
                            )
                        }
                        if (app.permissions.isNotEmpty()) {
                            item {
                                AmberButton(
                                    modifier = Modifier.padding(top = 12.dp),
                                    text = stringResource(Res.string.remove_all_permissions),
                                    onClick = { showRemoveAll = true },
                                )
                            }
                        }
                    }
                }
            }
            1 -> {
                // Mirrors the Android EditConfigurationScreen relay section: the
                // relays are editable until the client connects (it only listens
                // on the relays it was handed), then shown read-only.
                val editable = (app.app.secret.isNotEmpty() || app.app.relays.isNotEmpty()) && !app.app.isConnected
                var relays by remember(app.app.relays) { mutableStateOf(app.app.relays) }
                val relaysScroll = rememberScrollState()
                ScrollbarBox(rememberScrollbarAdapter(relaysScroll), Modifier.weight(1f)) {
                    Column(Modifier.fillMaxSize().verticalScroll(relaysScroll).padding(bottom = 8.dp)) {
                        RelayListEditor(
                            relays = relays,
                            onChange = { relays = it },
                            editable = editable,
                            modifier = Modifier.widthIn(max = 640.dp),
                        )
                        if (editable) {
                            AmberButton(
                                modifier = Modifier.padding(top = 12.dp),
                                text = stringResource(Res.string.update),
                                enabled = relays != app.app.relays,
                                onClick = {
                                    if (relays.isEmpty()) {
                                        Toaster.toast(getStringBlocking(Res.string.no_relays_added))
                                        return@AmberButton
                                    }
                                    store.upsert(app.copy(app = app.app.copy(relays = relays)))
                                    scope.launch {
                                        AmberDesktop.engine.checkForNewRelaysAndUpdateAllFilters()
                                    }
                                    Toaster.toast(getStringBlocking(Res.string.d_application_updated))
                                },
                            )
                        }
                    }
                }
            }
            else -> {
                val appHistory = history.filter { it.appKey == appKey }.sortedByDescending { it.time }
                val historyState = rememberLazyListState()
                ScrollbarBox(rememberScrollbarAdapter(historyState), Modifier.weight(1f)) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = historyState,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        if (appHistory.isEmpty()) {
                            item {
                                Text(stringResource(Res.string.d_no_activity), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        items(appHistory.size) { index ->
                            val entry = appHistory[index]
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(
                                    permissionTitle(entry.type, entry.kind),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "${DateTimes.format(entry.time * 1000)} · " +
                                        if (entry.accepted) stringResource(Res.string.d_accepted) else stringResource(Res.string.d_rejected),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        AmberOutlinedButton(
            text = stringResource(Res.string.d_disconnect_delete),
            onClick = {
                // Deleting drops this screen from composition, so run the
                // unsubscribe on the application scope (not this composable's)
                // to be sure the relay refresh completes and no stale relay
                // subscription is left behind.
                AmberDesktop.applicationIOScope.launch {
                    store.delete(appKey)
                    // Queued requests from the removed app must not linger as approval cards.
                    AmberDesktop.engine.dropPending("application removed") { it.account.npub == account.npub && it.localKey == appKey }
                    AmberDesktop.engine.checkForNewRelaysAndUpdateAllFilters()
                    Toaster.toast(getStringBlocking(Res.string.d_application_removed))
                }
                onBack()
            },
        )
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Localized display title for a permission or history entry — the kind /
 * method description, wrapped in "Sign %1$s" for signing types like the
 * mobile screens do.
 */
private fun permissionTitle(type: String, kind: Int?): String {
    val description = Permission(type, kind).localizedBlocking()
    return when (type.trim().lowercase()) {
        "sign_event", "nip" -> getStringBlocking(Res.string.sign, description)
        // NIP-44 v3 grants are per context kind, or for all kinds without one.
        "nip44_v3_encrypt", "nip44_v3_decrypt" ->
            "$description · " + (kind?.let { "$it (${Permission("sign_event", it).localizedBlocking()})" } ?: getStringBlocking(Res.string.for_all_kinds))
        else -> description
    }
}

/**
 * One permission, with the same options as the mobile `PermissionRow`:
 * an Allow / Deny / Ask action and, when not asking, the "automatically
 * sign this for" duration. State is derived from the record so the card
 * always reflects what is persisted.
 */
@Composable
private fun PermissionCard(
    permission: AppPermissionRecord,
    onChange: (AppPermissionRecord) -> Unit,
    onDelete: () -> Unit,
) {
    val title = permissionTitle(permission.type, permission.kind)

    // Mirrors the mobile mapping: accept window -> Allow, reject window ->
    // Deny, neither -> Ask; a NEVER remember choice edits as ALWAYS.
    val optionIndex = when {
        permission.acceptable && permission.acceptUntil > 0 -> 0
        !permission.acceptable && permission.rejectUntil > 0 -> 1
        else -> 2
    }
    val rememberType = (RememberType.entries.firstOrNull { it.screenCode == permission.rememberType } ?: RememberType.ALWAYS)
        .let { if (it == RememberType.NEVER) RememberType.ALWAYS else it }

    fun set(newIndex: Int, newType: RememberType) {
        val time = newType.acceptUntil()
        onChange(
            permission.copy(
                acceptable = newIndex == 0 || newIndex == 2,
                acceptUntil = if (newIndex == 0) time else 0L,
                rejectUntil = if (newIndex == 1) time else 0L,
                rememberType = newType.screenCode,
            ),
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (permission.kind == 22242 && permission.relay.isNotEmpty()) {
                    Text(
                        if (permission.relay == "*") stringResource(Res.string.for_all_relays) else permission.relay,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, stringResource(Res.string.d_delete_permission))
            }
        }

        Text(
            stringResource(Res.string.action),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to Res.string.allow, 1 to Res.string.deny, 2 to Res.string.ask).forEach { (index, key) ->
                FilterChip(
                    selected = optionIndex == index,
                    onClick = { set(index, rememberType) },
                    label = { Text(stringResource(key)) },
                )
            }
        }

        if (optionIndex != 2) {
            Text(
                stringResource(Res.string.automatically_sign_this_for),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    RememberType.FIVE_MINUTES,
                    RememberType.TEN_MINUTES,
                    RememberType.ONE_HOUR,
                    RememberType.ONE_DAY,
                    RememberType.ONE_WEEK,
                    RememberType.ALWAYS,
                ).forEach { type ->
                    FilterChip(
                        selected = rememberType == type,
                        onClick = { set(optionIndex, type) },
                        label = { Text(type.shortLabel()) },
                    )
                }
            }
        }
    }
}
