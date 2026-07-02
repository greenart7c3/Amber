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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.RememberType
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
    val app = apps.firstOrNull { it.app.key == appKey }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }

    if (app == null) {
        onBack()
        return
    }

    var name by remember { mutableStateOf(app.app.name) }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text(
                app.app.displayName(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Text("Key: ${app.app.key.toShortenHex()}", style = MaterialTheme.typography.bodySmall)
        if (app.app.relays.isNotEmpty()) {
            Text("Relays: ${app.app.relays.joinToString()}", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.weight(1f),
            )
            AmberOutlinedButton(
                modifier = Modifier.weight(0.4f),
                text = "Save",
                onClick = {
                    store.upsert(app.copy(app = app.app.copy(name = name)))
                    Toaster.toast("Application updated")
                },
            )
        }
        Spacer(Modifier.height(8.dp))

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Permissions") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Activity") })
        }
        Spacer(Modifier.height(8.dp))

        if (tab == 0) {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(app.permissions.size) { index ->
                    val permission = app.permissions[index]
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    permission.type + (permission.kind?.let { " (kind $it)" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                val until = if (permission.acceptable) permission.acceptUntil else permission.rejectUntil
                                val untilLabel = when {
                                    until >= Long.MAX_VALUE / 1000 -> "always"
                                    until > TimeUtils.now() -> "until ${DateFormat.getDateTimeInstance().format(Date(until * 1000))}"
                                    else -> "expired"
                                }
                                Text(
                                    (if (permission.acceptable) "Accept" else "Reject") + " · $untilLabel",
                                    style = MaterialTheme.typography.bodySmall,
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
                                Icon(Icons.Default.Delete, "Delete permission")
                            }
                        }
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
                        Text("No activity yet", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                items(appHistory.size) { index ->
                    val entry = appHistory[index]
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            entry.type + (entry.kind?.let { " (kind $it)" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${DateFormat.getDateTimeInstance().format(Date(entry.time * 1000))} · " +
                                if (entry.accepted) "accepted" else "rejected",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        AmberButton(
            text = "Disconnect and delete application",
            onClick = {
                scope.launch {
                    store.delete(appKey)
                    AmberDesktop.engine.updateFilter()
                    Toaster.toast("Application removed")
                    onBack()
                }
            },
        )
        Spacer(Modifier.height(12.dp))
    }
}
