package com.greenart7c3.nostrsigner.desktop.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.SignerDescriptions
import com.greenart7c3.nostrsigner.desktop.core.Strings
import com.greenart7c3.nostrsigner.desktop.core.toShortenHex
import com.vitorpamplona.quartz.utils.TimeUtils
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

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(Strings.get("permissions", language)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(Strings.get("d_activity", language)) })
        }
        Spacer(Modifier.height(8.dp))

        if (tab == 0) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(app.permissions.size) { index ->
                    val permission = app.permissions[index]
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                SignerDescriptions.permission(permission.type, permission.kind, language),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            val until = if (permission.acceptable) permission.acceptUntil else permission.rejectUntil
                            val untilLabel = when {
                                until >= Long.MAX_VALUE / 1000 -> Strings.get("d_until_always", language)
                                until > TimeUtils.now() -> Strings.format("d_until", DateFormat.getDateTimeInstance().format(Date(until * 1000)), language = language)
                                else -> Strings.get("d_expired", language)
                            }
                            Text(
                                (if (permission.acceptable) Strings.get("d_accept", language) else Strings.get("reject", language)) + " · $untilLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = permission.acceptable,
                            onCheckedChange = { accepted ->
                                val newPermissions = app.permissions.toMutableList()
                                newPermissions[index] = permission.copy(
                                    acceptable = accepted,
                                    acceptUntil = if (accepted) RememberType.ALWAYS.acceptUntil() else 0,
                                    rejectUntil = if (accepted) 0 else RememberType.ALWAYS.acceptUntil(),
                                )
                                store.upsert(app.copy(permissions = newPermissions))
                            },
                        )
                        IconButton(
                            onClick = {
                                val newPermissions = app.permissions.toMutableList()
                                newPermissions.removeAt(index)
                                store.upsert(app.copy(permissions = newPermissions))
                            },
                        ) {
                            Icon(Icons.Default.Delete, Strings.get("d_delete_permission", language))
                        }
                    }
                    HorizontalDivider()
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
