package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.Strings
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun ApplicationsScreen(
    account: DesktopAccount,
    onOpenApplication: (String) -> Unit,
) {
    val store = AmberDesktop.store(account.npub)
    val apps by store.apps.collectAsState()
    val language by Strings.currentLanguage.collectAsState()
    var showNostrConnectDialog by remember { mutableStateOf(false) }
    var showBunkerDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AmberButton(text = Strings.get("d_connect_nostrconnect", language), onClick = { showNostrConnectDialog = true })
            AmberOutlinedButton(text = Strings.get("d_create_bunker", language), onClick = { showBunkerDialog = true })
        }
        Spacer(Modifier.height(16.dp))

        if (apps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    Strings.get("d_no_apps", language),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
                val sorted = apps.sortedByDescending { it.app.lastUsed }
                items(sorted.size, key = { sorted[it].app.key }) { index ->
                    val app = sorted[index]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenApplication(app.app.key) }
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                app.app.displayName(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                buildString {
                                    append(if (app.app.isConnected) Strings.get("d_app_connected", language) else Strings.get("d_app_waiting", language))
                                    append(" · ${Strings.format("d_permissions_count", app.permissions.size, language = language)}")
                                    if (app.app.lastUsed > 0) {
                                        append(" · ${Strings.format("d_last_used", DateFormat.getDateTimeInstance().format(Date(app.app.lastUsed * 1000)), language = language)}")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showNostrConnectDialog) {
        NostrConnectDialog(account) { showNostrConnectDialog = false }
    }
    if (showBunkerDialog) {
        NewBunkerDialog(account) { showBunkerDialog = false }
    }
}

@Composable
private fun NostrConnectDialog(
    account: DesktopAccount,
    onDismiss: () -> Unit,
) {
    var uri by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val language by Strings.currentLanguage.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.get("d_add_an_application", language)) },
        text = {
            Column {
                Text(Strings.get("d_paste_nostrconnect", language))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uri,
                    onValueChange = { uri = it },
                    label = { Text("nostrconnect://…") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        if (!uri.trim().startsWith("nostrconnect://")) {
                            Toaster.toast(Strings.get("d_invalid_nostrconnect", language))
                            return@launch
                        }
                        val error = AmberDesktop.engine.addNostrConnect(uri, account)
                        if (error != null) {
                            Toaster.toast(error)
                        } else {
                            onDismiss()
                        }
                    }
                },
            ) { Text(Strings.get("add", language)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.get("cancel", language)) }
        },
    )
}

@Composable
private fun NewBunkerDialog(
    account: DesktopAccount,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var bunkerUri by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val language by Strings.currentLanguage.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (bunkerUri == null) Strings.get("d_add_nsecbunker", language) else Strings.get("d_bunker_created", language)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val uri = bunkerUri
                if (uri == null) {
                    Text(Strings.get("d_bunker_desc", language))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(Strings.get("d_application_name", language)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    QrCodeImage(uri)
                    Spacer(Modifier.height(8.dp))
                    Text(uri, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            val uri = bunkerUri
            if (uri == null) {
                TextButton(
                    onClick = {
                        if (name.isBlank()) {
                            Toaster.toast(Strings.get("d_name_required", language))
                            return@TextButton
                        }
                        scope.launch {
                            bunkerUri = AmberDesktop.engine.createBunkerConnection(
                                account,
                                name,
                                AmberDesktop.defaultRelays(),
                            )
                        }
                    },
                ) { Text(Strings.get("d_create", language)) }
            } else {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(uri))
                        Toaster.toast(Strings.get("d_copied_clipboard", language))
                    },
                ) { Text(Strings.get("copy", language).trim()) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (bunkerUri == null) Strings.get("cancel", language) else Strings.get("d_close", language)) }
        },
    )
}
