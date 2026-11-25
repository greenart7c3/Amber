package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.nip44v3SignerTypes
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.account
import com.greenart7c3.nostrsigner.commonsui.approve
import com.greenart7c3.nostrsigner.commonsui.confirm
import com.greenart7c3.nostrsigner.commonsui.d_approve
import com.greenart7c3.nostrsigner.commonsui.d_no_requests_sub
import com.greenart7c3.nostrsigner.commonsui.d_no_requests_title
import com.greenart7c3.nostrsigner.commonsui.d_relays_label
import com.greenart7c3.nostrsigner.commonsui.d_request_approved
import com.greenart7c3.nostrsigner.commonsui.d_request_rejected
import com.greenart7c3.nostrsigner.commonsui.d_shortcut_hint
import com.greenart7c3.nostrsigner.commonsui.d_shortcut_hint_batch
import com.greenart7c3.nostrsigner.commonsui.d_shortcut_hint_connect
import com.greenart7c3.nostrsigner.commonsui.d_shortcut_hint_scope
import com.greenart7c3.nostrsigner.commonsui.d_socket_name_unverified
import com.greenart7c3.nostrsigner.commonsui.d_via_local_socket
import com.greenart7c3.nostrsigner.commonsui.d_working
import com.greenart7c3.nostrsigner.commonsui.deny
import com.greenart7c3.nostrsigner.commonsui.encryption_scope
import com.greenart7c3.nostrsigner.commonsui.for_all_kinds
import com.greenart7c3.nostrsigner.commonsui.for_all_methods
import com.greenart7c3.nostrsigner.commonsui.for_all_relays
import com.greenart7c3.nostrsigner.commonsui.for_this_kind_only
import com.greenart7c3.nostrsigner.commonsui.for_this_method_only
import com.greenart7c3.nostrsigner.commonsui.for_this_relay_only
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.insecure_relay_warning
import com.greenart7c3.nostrsigner.commonsui.is_requiring_some_permissions_please_review_them
import com.greenart7c3.nostrsigner.commonsui.localizedBlocking
import com.greenart7c3.nostrsigner.commonsui.nip44_v3_context
import com.greenart7c3.nostrsigner.commonsui.nip44_v3_kind
import com.greenart7c3.nostrsigner.commonsui.nip44_v3_no_scope
import com.greenart7c3.nostrsigner.commonsui.nip44_v3_scope
import com.greenart7c3.nostrsigner.commonsui.permissions
import com.greenart7c3.nostrsigner.commonsui.reject
import com.greenart7c3.nostrsigner.commonsui.relay_auth_scope
import com.greenart7c3.nostrsigner.commonsui.replace_app_message
import com.greenart7c3.nostrsigner.commonsui.see_details
import com.greenart7c3.nostrsigner.commonsui.several_apps_are_requiring_some_permissions_please_review_them
import com.greenart7c3.nostrsigner.commonsui.switch_account
import com.greenart7c3.nostrsigner.desktop.core.AccountsStore
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.EncryptionScope
import com.greenart7c3.nostrsigner.desktop.core.LocalRelays
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.connectionToReplace
import com.greenart7c3.nostrsigner.desktop.core.describe
import com.greenart7c3.nostrsigner.desktop.core.toShortenHex
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun IncomingRequestsScreen(modifier: Modifier = Modifier) {
    val pending by AmberDesktop.engine.pending.collectAsState()

    if (pending.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.d_no_requests_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.d_no_requests_sub),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        return
    }

    LaunchedEffect(pending) {
        UiState.pruneRequestState(pending)
    }

    val single = singleRequest(pending)
    if (single != null) {
        val scrollState = rememberScrollState()
        ScrollbarBox(rememberScrollbarAdapter(scrollState), Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(vertical = 12.dp)) {
                key(single.queueKey) {
                    RequestCard(single)
                }
            }
        }
    } else {
        RequestBatch(batchApps(pending))
    }
}

@Composable
private fun RequestBatch(apps: List<RequestApp>) {
    val selectedId by UiState.selectedRequestId.collectAsState()
    val approved by UiState.batchApproved.collectAsState()
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val listState = rememberLazyListState()
    val entries: List<Any> = remember(apps) {
        apps.flatMap { app -> (if (apps.size > 1) listOf<Any>(app) else emptyList()) + app.groups }
    }

    LaunchedEffect(apps) { UiState.seedBatch(apps.flatMap { it.requests }) }
    LaunchedEffect(selectedId, entries) {
        val index = entries.indexOfFirst { entry -> entry is RequestGroup && entry.requests.any { it.queueKey == selectedId } }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        Text(
            if (apps.size == 1) {
                stringResource(Res.string.is_requiring_some_permissions_please_review_them, apps.first().first.displayName())
            } else {
                pluralStringResource(Res.plurals.several_apps_are_requiring_some_permissions_please_review_them, apps.size, apps.size)
            },
            style = MaterialTheme.typography.titleMedium,
        )
        if (apps.size == 1) BatchAppHeader(apps.first(), showName = false)
        ScrollbarBox(rememberScrollbarAdapter(listState), Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            ) {
                items(entries.size, key = { (entries[it] as? RequestGroup)?.id ?: "app:${(entries[it] as RequestApp).first.queueKey}" }) { index ->
                    when (val entry = entries[index]) {
                        is RequestApp -> BatchAppHeader(entry, showName = true)
                        is RequestGroup -> {
                            val app = apps.first { entry in it.groups }
                            val collapsible = app.groups.size > 1
                            BatchGroupCard(
                                group = entry,
                                approved = approved,
                                selectedId = selectedId ?: apps.first().first.queueKey,
                                collapsible = collapsible,
                                expanded = !collapsible || expanded[entry.id] == true || entry.requests.any { it.queueKey == selectedId },
                                onExpandToggle = { expanded[entry.id] = expanded[entry.id] != true },
                            )
                        }
                    }
                }
            }
        }
        BatchConfirmBar()
    }
}

private fun PendingBunkerRequest.displayName() = appName.ifBlank { localKey.toShortenHex() }

@Composable
private fun BatchAppHeader(
    app: RequestApp,
    showName: Boolean,
) {
    val req = app.first
    Column(Modifier.padding(top = 4.dp)) {
        if (showName) {
            Text(
                "${req.displayName()} (${app.requests.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (req.appUrl.isNotBlank()) Text(req.appUrl, style = MaterialTheme.typography.bodySmall)
        if (req.isLocalSocket) Text(stringResource(Res.string.d_via_local_socket), style = MaterialTheme.typography.bodySmall)
        SigningAccountRow(npub = req.account.npub, canSwitch = false, onSelect = {})
    }
}

@Composable
private fun BatchGroupCard(
    group: RequestGroup,
    approved: Map<String, Boolean>,
    selectedId: String,
    collapsible: Boolean,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
) {
    val first = group.requests.first()
    val rememberChoices by UiState.rememberChoices.collectAsState()
    val scopeChoices by UiState.scopeChoices.collectAsState()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = group.requests.all { approved[it.queueKey] != false },
                    onCheckedChange = { UiState.setBatchApproved(group.requests, it) },
                )
                Text(
                    "${group.type.describe(group.kind, group.content)} (${group.requests.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (collapsible) {
                    AmberTextButton(text = if (expanded) "▲" else "▼", onClick = onExpandToggle)
                }
            }
            if (first.hasScopeChoice) {
                ScopeChips(first, scopeChoices[first.queueKey] ?: first.defaultScope) { UiState.setGroupScope(group.requests, it) }
            }
            RememberTypeSelector(rememberChoices[first.queueKey] ?: RememberType.NEVER, onValueChange = { UiState.setGroupRemember(group.requests, it) })
            if (expanded) {
                group.requests.forEach { req ->
                    BatchRequestRow(
                        req = req,
                        approved = approved[req.queueKey] != false,
                        selected = req.queueKey == selectedId,
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchRequestRow(
    req: PendingBunkerRequest,
    approved: Boolean,
    selected: Boolean,
) {
    val details = remember(req) { RequestDetails.of(req) }
    Card(
        Modifier.fillMaxWidth().padding(top = 8.dp).clickable { UiState.selectedRequestId.value = req.queueKey },
        border = if (selected) BorderStroke(2.dp, orange) else null,
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = approved, onCheckedChange = { UiState.setBatchApproved(listOf(req), it) })
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (approved) Res.string.approve else Res.string.deny),
                    style = MaterialTheme.typography.labelMedium,
                )
                val summary = details?.summary().orEmpty().ifBlank { req.preview }
                if (summary.isNotBlank()) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (req.type in nip44v3SignerTypes) {
                    Text(
                        stringResource(Res.string.nip44_v3_scope, req.nip44v3Scope.ifEmpty { stringResource(Res.string.nip44_v3_no_scope) }),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (details != null) {
                AmberTextButton(
                    text = if (selected) "${stringResource(Res.string.see_details)} (D)" else stringResource(Res.string.see_details),
                    onClick = { UiState.detailsRequestId.value = req.queueKey },
                )
                val detailsId by UiState.detailsRequestId.collectAsState()
                if (detailsId == req.queueKey) {
                    RequestDetailsDialog(req, details, onDismiss = { UiState.detailsRequestId.value = null })
                }
            }
        }
    }
}

@Composable
private fun ScopeChips(
    req: PendingBunkerRequest,
    scope: EncryptionScope,
    onSelect: (EncryptionScope) -> Unit,
) {
    val options = if (req.isRelayAuth) {
        listOf(EncryptionScope.SPECIFIC to Res.string.for_this_relay_only, EncryptionScope.ALL to Res.string.for_all_relays)
    } else if (req.type in nip44v3SignerTypes) {
        listOf(EncryptionScope.SPECIFIC to Res.string.for_this_kind_only, EncryptionScope.ALL to Res.string.for_all_kinds)
    } else {
        listOf(EncryptionScope.SPECIFIC to Res.string.for_this_method_only, EncryptionScope.ALL to Res.string.for_all_methods)
    }
    Column {
        Text(
            stringResource(if (req.isRelayAuth) Res.string.relay_auth_scope else Res.string.encryption_scope),
            style = MaterialTheme.typography.labelMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, key) ->
                FilterChip(
                    selected = scope == value,
                    onClick = { onSelect(value) },
                    label = { Text(stringResource(key)) },
                )
            }
        }
    }
}

@Composable
private fun RequestCard(req: PendingBunkerRequest) {
    val rememberChoices by UiState.rememberChoices.collectAsState()
    val rememberType = rememberChoices[req.queueKey] ?: RememberType.NEVER
    var working by remember { mutableStateOf(false) }
    val connectChoices by UiState.connectChoices.collectAsState()
    val connectChoice = connectChoices[req.queueKey] ?: UiState.connectChoiceFor(req)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            RequestCardHeader(
                req = req,
                signingAccountNpub = if (req.type == SignerType.CONNECT) connectChoice.accountNpub else req.account.npub,
                onSelectAccount = { UiState.setConnectChoice(req.queueKey, connectChoice.copy(accountNpub = it)) },
            )
            RequestPreviewSection(req)
            if (req.type == SignerType.CONNECT) {
                ConnectRequestSection(
                    req = req,
                    connectChoice = connectChoice,
                    onChoiceChange = { UiState.setConnectChoice(req.queueKey, it) },
                )
            }
            Spacer(Modifier.height(12.dp))
            RequestGrantOptions(req = req, rememberType = rememberType)
            RequestActionBar(
                req = req,
                working = working,
                onApprove = {
                    working = true
                    UiState.approve(req)
                    Toaster.toast(getStringBlocking(Res.string.d_request_approved))
                },
                onReject = {
                    working = true
                    UiState.reject(req)
                    Toaster.toast(getStringBlocking(Res.string.d_request_rejected))
                },
            )
        }
    }
}

@Composable
private fun RequestCardHeader(
    req: PendingBunkerRequest,
    signingAccountNpub: String,
    onSelectAccount: (String) -> Unit,
) {
    Column {
        Text(
            req.displayName(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (req.appUrl.isNotBlank()) {
            Text(req.appUrl, style = MaterialTheme.typography.bodySmall)
        }
        if (req.isLocalSocket) {
            Text(stringResource(Res.string.d_via_local_socket), style = MaterialTheme.typography.bodySmall)
            if (req.type == SignerType.CONNECT) {
                Text(
                    stringResource(Res.string.d_socket_name_unverified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(req.type.describe(req.kind, req.encryptedContent), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        SigningAccountRow(
            npub = signingAccountNpub,
            canSwitch = req.canSwitchAccount,
            onSelect = onSelectAccount,
        )
    }
}

@Composable
private fun RequestPreviewSection(req: PendingBunkerRequest) {
    val details = remember(req) { RequestDetails.of(req) }
    if (details == null && req.preview.isBlank()) return

    Column {
        if (details != null) {
            val summary = details.summary()
            if (summary.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AmberTextButton(
                text = "${stringResource(Res.string.see_details)} (D)",
                onClick = { UiState.detailsRequestId.value = req.queueKey },
            )
            val detailsId by UiState.detailsRequestId.collectAsState()
            if (detailsId == req.queueKey) {
                RequestDetailsDialog(req, details, onDismiss = { UiState.detailsRequestId.value = null })
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Text(
                    req.preview,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun ConnectRequestSection(
    req: PendingBunkerRequest,
    connectChoice: ConnectChoice,
    onChoiceChange: (ConnectChoice) -> Unit,
) {
    val replaced = remember(req, connectChoice.accountNpub) {
        connectionToReplace(req, connectChoice.accountNpub)
    }
    Column {
        if (replaced != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    Res.string.replace_app_message,
                    replaced.app.name.ifBlank { replaced.app.key.toShortenHex() },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (!req.isLocalSocket && req.relays.isNotEmpty()) {
            ConnectRelaysNotice(req.relays.map { it.url })
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.permissions), style = MaterialTheme.typography.titleSmall)
        SignPolicySelector(connectChoice.signPolicy, onSelect = {
            onChoiceChange(connectChoice.copy(signPolicy = it))
        })
        if (connectChoice.signPolicy == 1 && connectChoice.granted.isNotEmpty()) {
            ConnectPermissionChecklist(connectChoice, onChoiceChange)
        }
        Spacer(Modifier.height(8.dp))
        DeleteAfterSelector(connectChoice.deleteAfter, onSelect = {
            onChoiceChange(connectChoice.copy(deleteAfter = it))
        })
    }
}

@Composable
private fun ConnectRelaysNotice(relayUrls: List<String>) {
    Column {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.d_relays_label, relayUrls.joinToString()),
            style = MaterialTheme.typography.bodySmall,
        )
        if (relayUrls.any { LocalRelays.isInsecure(it) }) {
            Text(
                stringResource(Res.string.insecure_relay_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ConnectPermissionChecklist(
    connectChoice: ConnectChoice,
    onChoiceChange: (ConnectChoice) -> Unit,
) {
    Column(Modifier.padding(start = 24.dp)) {
        connectChoice.granted.forEachIndexed { index, perm ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = perm.checked,
                    onCheckedChange = { checked ->
                        val granted = connectChoice.granted.toMutableList()
                        granted[index] = perm.copy(checked = checked)
                        onChoiceChange(connectChoice.copy(granted = granted))
                    },
                )
                Text(
                    Permission(perm.type, perm.kind).localizedBlocking(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun RequestGrantOptions(
    req: PendingBunkerRequest,
    rememberType: RememberType,
) {
    Column {
        if (req.type in nip44v3SignerTypes) {
            Nip44v3ContextNotice(req)
        }
        if (req.hasScopeChoice) {
            val scopeChoices by UiState.scopeChoices.collectAsState()
            ScopeChips(req, scopeChoices[req.queueKey] ?: req.defaultScope) { UiState.setScopeChoice(req.queueKey, it) }
            Spacer(Modifier.height(4.dp))
        }
        if (req.type != SignerType.CONNECT) {
            RememberTypeSelector(rememberType, onValueChange = { UiState.setRememberChoice(req.queueKey, it) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Nip44v3ContextNotice(req: PendingBunkerRequest) {
    Column {
        Spacer(Modifier.height(4.dp))
        Text(stringResource(Res.string.nip44_v3_context), style = MaterialTheme.typography.labelMedium)
        val kindLabel = req.kind?.let { "$it (${Permission("sign_event", it).localizedBlocking()})" } ?: "-"
        Text(stringResource(Res.string.nip44_v3_kind, kindLabel), style = MaterialTheme.typography.bodySmall)
        Text(
            stringResource(Res.string.nip44_v3_scope, req.nip44v3Scope.ifEmpty { stringResource(Res.string.nip44_v3_no_scope) }),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RequestActionBar(
    req: PendingBunkerRequest,
    working: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val compact = LocalLayoutWidth.current == LayoutWidth.COMPACT
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AmberButton(
                text = if (working) stringResource(Res.string.d_working) else stringResource(Res.string.d_approve),
                enabled = !working,
                onClick = onApprove,
            )
            AmberOutlinedButton(
                text = stringResource(Res.string.reject),
                onClick = onReject,
            )
            if (!compact) {
                Spacer(Modifier.weight(1f))
                ShortcutHint(req)
            }
        }
        if (compact) {
            Spacer(Modifier.height(6.dp))
            ShortcutHint(req)
        }
    }
}

@Composable
private fun ShortcutHint(
    req: PendingBunkerRequest,
) {
    Text(
        stringResource(
            when {
                req.type == SignerType.CONNECT -> Res.string.d_shortcut_hint_connect
                req.hasScopeChoice -> Res.string.d_shortcut_hint_scope
                else -> Res.string.d_shortcut_hint
            },
            shortcutLabel("↵"),
            shortcutLabel("↵", shift = true),
        ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SigningAccountRow(
    npub: String,
    canSwitch: Boolean,
    onSelect: (String) -> Unit,
) {
    val accounts by AccountsStore.accounts.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val switchable = canSwitch && accounts.size > 1

    fun label(npub: String): String {
        val name = accounts.firstOrNull { it.npub == npub }?.name.orEmpty()
        return if (name.isBlank()) npub.toShortenHex() else "$name · ${npub.toShortenHex()}"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${stringResource(Res.string.account)}:",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(6.dp))
        AccountAvatar(npub, size = 24.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            label(npub),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (switchable) {
            Spacer(Modifier.width(8.dp))
            Box {
                AmberTextButton(
                    text = "${stringResource(Res.string.switch_account)} (A)",
                    onClick = { expanded = true },
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    accounts.forEach { account ->
                        DropdownMenuItem(
                            leadingIcon = { AccountAvatar(account.npub, size = 24.dp) },
                            text = {
                                Text(
                                    label(account.npub),
                                    fontWeight = if (account.npub == npub) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                onSelect(account.npub)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchConfirmBar() {
    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AmberButton(
            text = "${stringResource(Res.string.confirm)} (${shortcutLabel("↵")})",
            onClick = { UiState.confirmBatch() },
        )
        Spacer(Modifier.width(16.dp))
        Text(
            stringResource(Res.string.d_shortcut_hint_batch, shortcutLabel("↵")),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
