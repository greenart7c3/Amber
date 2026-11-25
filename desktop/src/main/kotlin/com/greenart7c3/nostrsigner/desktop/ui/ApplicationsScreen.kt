package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.add
import com.greenart7c3.nostrsigner.commonsui.cancel
import com.greenart7c3.nostrsigner.commonsui.copy
import com.greenart7c3.nostrsigner.commonsui.d_add_an_application
import com.greenart7c3.nostrsigner.commonsui.d_add_nsecbunker
import com.greenart7c3.nostrsigner.commonsui.d_app_connected
import com.greenart7c3.nostrsigner.commonsui.d_app_waiting
import com.greenart7c3.nostrsigner.commonsui.d_application_name
import com.greenart7c3.nostrsigner.commonsui.d_bunker_created
import com.greenart7c3.nostrsigner.commonsui.d_bunker_desc
import com.greenart7c3.nostrsigner.commonsui.d_close
import com.greenart7c3.nostrsigner.commonsui.d_connect_nostrconnect
import com.greenart7c3.nostrsigner.commonsui.d_copied_clipboard
import com.greenart7c3.nostrsigner.commonsui.d_create
import com.greenart7c3.nostrsigner.commonsui.d_create_bunker
import com.greenart7c3.nostrsigner.commonsui.d_invalid_nostrconnect
import com.greenart7c3.nostrsigner.commonsui.d_last_used
import com.greenart7c3.nostrsigner.commonsui.d_local_socket
import com.greenart7c3.nostrsigner.commonsui.d_name_required
import com.greenart7c3.nostrsigner.commonsui.d_no_apps
import com.greenart7c3.nostrsigner.commonsui.d_paste_nostrconnect
import com.greenart7c3.nostrsigner.commonsui.d_permissions_count
import com.greenart7c3.nostrsigner.commonsui.d_relays_label
import com.greenart7c3.nostrsigner.commonsui.d_sort_alphabetically
import com.greenart7c3.nostrsigner.commonsui.d_sort_most_recent
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.no_relays_added
import com.greenart7c3.nostrsigner.commonsui.relays
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.AppWithPermissions
import com.greenart7c3.nostrsigner.desktop.core.DateTimes
import com.greenart7c3.nostrsigner.desktop.core.DeleteAfterType
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import com.greenart7c3.nostrsigner.desktop.core.addNostrConnect
import com.greenart7c3.nostrsigner.desktop.core.createBunkerConnection
import com.greenart7c3.nostrsigner.desktop.core.isLocalSocket
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun ApplicationsScreen(
    account: DesktopAccount,
    onOpenApplication: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = AmberDesktop.store(account.npub)
    val apps by store.apps.collectAsState()
    val settings by SettingsStore.settings.collectAsState()
    var showNostrConnectDialog by remember { mutableStateOf(false) }
    var showBunkerDialog by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        ApplicationsToolbar(
            sortAlphabetically = settings.sortApplicationsAlphabetically,
            onConnectClick = { showNostrConnectDialog = true },
            onCreateBunkerClick = { showBunkerDialog = true },
            onToggleSort = {
                SettingsStore.update { it.copy(sortApplicationsAlphabetically = !it.sortApplicationsAlphabetically) }
            },
        )
        Spacer(Modifier.height(16.dp))

        if (apps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(Res.string.d_no_apps),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            ApplicationsList(
                apps = apps,
                sortAlphabetically = settings.sortApplicationsAlphabetically,
                onOpenApplication = onOpenApplication,
            )
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
private fun ApplicationsToolbar(
    sortAlphabetically: Boolean,
    onConnectClick: () -> Unit,
    onCreateBunkerClick: () -> Unit,
    onToggleSort: () -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AmberButton(text = stringResource(Res.string.d_connect_nostrconnect), onClick = onConnectClick)
        AmberOutlinedButton(text = stringResource(Res.string.d_create_bunker), onClick = onCreateBunkerClick)
        IconButton(onClick = onToggleSort) {
            Icon(
                imageVector = Icons.Default.SortByAlpha,
                contentDescription = stringResource(
                    if (sortAlphabetically) Res.string.d_sort_most_recent else Res.string.d_sort_alphabetically,
                ),
                tint = if (sortAlphabetically) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun ApplicationsList(
    apps: List<AppWithPermissions>,
    sortAlphabetically: Boolean,
    onOpenApplication: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val sorted = if (sortAlphabetically) {
        apps.sortedBy { it.app.displayName().lowercase() }
    } else {
        apps.sortedByDescending { it.app.lastUsed }
    }
    ScrollbarBox(rememberScrollbarAdapter(listState), Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 12.dp)) {
            items(sorted.size, key = { sorted[it].app.key }) { index ->
                val app = sorted[index]
                ApplicationListRow(
                    app = app,
                    onClick = { onOpenApplication(app.app.key) },
                )
            }
        }
    }
}

@Composable
private fun ApplicationListRow(
    app: AppWithPermissions,
    onClick: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
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
                        append(if (app.app.isConnected) stringResource(Res.string.d_app_connected) else stringResource(Res.string.d_app_waiting))
                        if (app.app.isLocalSocket) append(" · ${stringResource(Res.string.d_local_socket)}")
                        append(" · ${stringResource(Res.string.d_permissions_count, app.permissions.size)}")
                        if (app.app.lastUsed > 0) {
                            append(" · ${stringResource(Res.string.d_last_used, DateTimes.format(app.app.lastUsed * 1000))}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (app.app.relays.isNotEmpty()) {
                    Text(
                        stringResource(
                            Res.string.d_relays_label,
                            app.app.relays.joinToString { it.removePrefix("wss://").removeSuffix("/") },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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

@Composable
private fun NostrConnectDialog(
    account: DesktopAccount,
    onDismiss: () -> Unit,
) {
    var uri by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.d_add_an_application)) },
        text = {
            Column {
                Text(stringResource(Res.string.d_paste_nostrconnect))
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
            AmberTextButton(
                text = stringResource(Res.string.add),
                onClick = {
                    scope.launch {
                        if (!uri.trim().startsWith("nostrconnect://")) {
                            Toaster.toast(getStringBlocking(Res.string.d_invalid_nostrconnect))
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
            )
        },
        dismissButton = {
            AmberTextButton(
                text = stringResource(Res.string.cancel),
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun NewBunkerDialog(
    account: DesktopAccount,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var relays by remember { mutableStateOf(AmberDesktop.newBunkerRelays().map { it.url }) }
    var deleteAfter by remember { mutableStateOf(DeleteAfterType.NEVER) }
    var bunkerUri by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val copyText = rememberCopyText()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (bunkerUri == null) stringResource(Res.string.d_add_nsecbunker) else stringResource(Res.string.d_bunker_created)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val uri = bunkerUri
                if (uri == null) {
                    NewBunkerFormFields(
                        name = name,
                        deleteAfter = deleteAfter,
                        relays = relays,
                        onNameChange = { name = it },
                        onDeleteAfterChange = { deleteAfter = it },
                        onRelaysChange = { relays = it },
                    )
                } else {
                    BunkerCreatedContent(uri)
                }
            }
        },
        confirmButton = {
            NewBunkerConfirmButton(
                uri = bunkerUri,
                onCreate = {
                    createBunker(
                        account = account,
                        name = name,
                        relays = relays,
                        deleteAfter = deleteAfter,
                        scope = scope,
                        onCreated = { bunkerUri = it },
                    )
                },
                onCopy = { uri ->
                    copyText(uri)
                    Toaster.toast(getStringBlocking(Res.string.d_copied_clipboard))
                },
            )
        },
        dismissButton = {
            AmberTextButton(
                text = if (bunkerUri == null) stringResource(Res.string.cancel) else stringResource(Res.string.d_close),
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun NewBunkerConfirmButton(
    uri: String?,
    onCreate: () -> Unit,
    onCopy: (String) -> Unit,
) {
    AmberTextButton(
        text = if (uri == null) stringResource(Res.string.d_create) else stringResource(Res.string.copy).trim(),
        onClick = { if (uri == null) onCreate() else onCopy(uri) },
    )
}

@Composable
private fun NewBunkerFormFields(
    name: String,
    deleteAfter: DeleteAfterType,
    relays: List<String>,
    onNameChange: (String) -> Unit,
    onDeleteAfterChange: (DeleteAfterType) -> Unit,
    onRelaysChange: (List<String>) -> Unit,
) {
    val nameFocus = remember { FocusRequester() }
    Column {
        Text(stringResource(Res.string.d_bunker_desc))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(Res.string.d_application_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
        )
        LaunchedEffect(Unit) { nameFocus.requestFocus() }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth()) {
            DeleteAfterSelector(deleteAfter, onSelect = onDeleteAfterChange)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(Res.string.relays),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        RelayListEditor(
            relays = relays,
            onChange = onRelaysChange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BunkerCreatedContent(uri: String) {
    val qrSize = (LocalWindowContentSize.current.width - 120.dp).coerceIn(160.dp, 300.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        QrCodeImage(uri, size = qrSize)
        Spacer(Modifier.height(8.dp))
        Text(uri, style = MaterialTheme.typography.bodySmall)
    }
}

private fun createBunker(
    account: DesktopAccount,
    name: String,
    relays: List<String>,
    deleteAfter: DeleteAfterType,
    scope: CoroutineScope,
    onCreated: (String) -> Unit,
) {
    if (name.isBlank()) {
        Toaster.toast(getStringBlocking(Res.string.d_name_required))
        return
    }
    val normalized = relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
    if (normalized.isEmpty()) {
        Toaster.toast(getStringBlocking(Res.string.no_relays_added))
        return
    }
    scope.launch {
        onCreated(
            AmberDesktop.engine.createBunkerConnection(
                account,
                name,
                normalized,
                deleteAfter.deleteAt(),
            ),
        )
    }
}
