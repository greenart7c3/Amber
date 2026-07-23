package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.AppPermissionRecord
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.SignerDescriptions
import com.greenart7c3.nostrsigner.desktop.core.Strings
import com.greenart7c3.nostrsigner.desktop.core.toShortenHex
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun ApplicationDetailScreen(
    account: DesktopAccount,
    appKey: String,
    onBack: () -> Unit,
) {
    val store = AmberDesktop.store(account.npub)
    val apps by store.apps.collectAsState()
    val history by store.history.collectAsState()
    val language by Strings.currentLanguage.collectAsState()
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, Strings.get("d_back", language))
            }
            Column {
                Text(
                    app.app.displayName(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    buildString {
                        append(Strings.format("d_key_label", app.app.key.toShortenHex(), language = language))
                        if (app.app.relays.isNotEmpty()) append(" · ${Strings.format("d_relays_label", app.app.relays.joinToString(), language = language)}")
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
                label = { Text(Strings.get("name", language)) },
                singleLine = true,
                modifier = Modifier.widthIn(max = 420.dp).weight(1f, fill = false),
            )
            AmberOutlinedButton(
                text = Strings.get("save", language),
                onClick = {
                    store.upsert(app.copy(app = app.app.copy(name = name)))
                    Toaster.toast(Strings.get("d_application_updated", language))
                },
            )
        }
        Spacer(Modifier.height(12.dp))

        AmberTabRow(
            selectedTabIndex = tab,
            titles = listOf(Strings.get("permissions", language), Strings.get("d_activity", language)),
            onSelect = { tab = it },
        )
        Spacer(Modifier.height(8.dp))

        var showRemoveAll by remember { mutableStateOf(false) }
        if (showRemoveAll) {
            AlertDialog(
                onDismissRequest = { showRemoveAll = false },
                title = { Text(Strings.get("remove", language)) },
                text = { Text(Strings.get("remove_all_message", language)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRemoveAll = false
                            store.upsert(app.copy(permissions = mutableListOf()))
                        },
                    ) { Text(Strings.get("remove", language)) }
                },
                dismissButton = {
                    TextButton(onClick = { showRemoveAll = false }) { Text(Strings.get("cancel", language)) }
                },
            )
        }

        if (tab == 0) {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                item {
                    Text(
                        Strings.get("edit_permissions_description", language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(app.permissions.size) { index ->
                    val permission = app.permissions[index]
                    PermissionCard(
                        permission = permission,
                        language = language,
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
                            text = Strings.get("remove_all_permissions", language),
                            onClick = { showRemoveAll = true },
                        )
                    }
                }
            }
        } else {
            val appHistory = history.filter { it.appKey == appKey }.sortedByDescending { it.time }
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                if (appHistory.isEmpty()) {
                    item {
                        Text(Strings.get("d_no_activity", language), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                items(appHistory.size) { index ->
                    val entry = appHistory[index]
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            entry.type + (entry.kind?.let { " (${Strings.format("d_kind_paren", it, language = language)})" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${DateFormat.getDateTimeInstance().format(Date(entry.time * 1000))} · " +
                                if (entry.accepted) Strings.get("d_accepted", language) else Strings.get("d_rejected", language),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        AmberOutlinedButton(
            text = Strings.get("d_disconnect_delete", language),
            onClick = {
                // Deleting drops this screen from composition, so run the
                // unsubscribe on the application scope (not this composable's)
                // to be sure updateFilter() completes and no stale relay
                // subscription is left behind.
                AmberDesktop.applicationIOScope.launch {
                    store.delete(appKey)
                    AmberDesktop.engine.updateFilter()
                    Toaster.toast(Strings.get("d_application_removed", language))
                }
                onBack()
            },
        )
        Spacer(Modifier.height(12.dp))
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
    language: String,
    onChange: (AppPermissionRecord) -> Unit,
    onDelete: () -> Unit,
) {
    val typeLower = permission.type.trim().lowercase()
    val description = SignerDescriptions.permission(permission.type, permission.kind, language)
    val title = if (typeLower == "sign_event" || typeLower == "nip") {
        Strings.format("sign", description, language = language)
    } else {
        description
    }

    // Mirrors the mobile mapping: accept window -> Allow, reject window ->
    // Deny, neither -> Ask; a NEVER remember choice edits as ALWAYS.
    val optionIndex = when {
        permission.acceptUntil > 0 -> 0
        permission.rejectUntil > 0 -> 1
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
                        if (permission.relay == "*") Strings.get("for_all_relays", language) else permission.relay,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, Strings.get("d_delete_permission", language))
            }
        }

        Text(
            Strings.get("action", language),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "allow", 1 to "deny", 2 to "ask").forEach { (index, key) ->
                FilterChip(
                    selected = optionIndex == index,
                    onClick = { set(index, rememberType) },
                    label = { Text(Strings.get(key, language)) },
                )
            }
        }

        if (optionIndex != 2) {
            Text(
                Strings.get("automatically_sign_this_for", language),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        label = { Text(type.shortLabel(language)) },
                    )
                }
            }
        }
    }
}
