package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.core.AccountsStore
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.EncryptionScope
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.SignerDescriptions
import com.greenart7c3.nostrsigner.desktop.core.SignerType
import com.greenart7c3.nostrsigner.desktop.core.Strings
import com.greenart7c3.nostrsigner.desktop.core.contentScopedSignerTypes
import com.greenart7c3.nostrsigner.desktop.core.describe
import com.greenart7c3.nostrsigner.desktop.core.nip44v3SignerTypes
import com.greenart7c3.nostrsigner.desktop.core.toShortenHex

@Composable
fun IncomingRequestsScreen(account: DesktopAccount) {
    val pending by AmberDesktop.engine.pending.collectAsState()
    val language by Strings.currentLanguage.collectAsState()

    if (pending.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Strings.get("d_no_requests_title", language), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    Strings.get("d_no_requests_sub", language),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        return
    }

    val selectedId by UiState.selectedRequestId.collectAsState()
    val listState = rememberLazyListState()

    // Keep the keyboard selection valid and visible as requests come and go.
    LaunchedEffect(pending) {
        UiState.pruneRequestState(pending)
    }
    LaunchedEffect(selectedId) {
        val index = pending.indexOfFirst { it.request.id == selectedId }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    ScrollbarBox(rememberScrollbarAdapter(listState), Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            items(pending.size, key = { pending[it].request.id }) { index ->
                val req = pending[index]
                RequestCard(
                    req = req,
                    selected = req.request.id == (selectedId ?: pending.firstOrNull()?.request?.id),
                    onSelect = { UiState.selectedRequestId.value = req.request.id },
                )
            }
        }
    }
}

@Composable
private fun RequestCard(
    req: PendingBunkerRequest,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val rememberChoices by UiState.rememberChoices.collectAsState()
    val rememberType = rememberChoices[req.request.id] ?: RememberType.NEVER
    val language by Strings.currentLanguage.collectAsState()
    var working by remember { mutableStateOf(false) }
    val connectChoices by UiState.connectChoices.collectAsState()
    val connectChoice = connectChoices[req.request.id] ?: UiState.connectChoiceFor(req)

    Card(
        Modifier.fillMaxWidth().clickable(onClick = onSelect),
        border = if (selected) BorderStroke(2.dp, orange) else null,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                req.appName.ifBlank { req.localKey.toShortenHex() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (req.appUrl.isNotBlank()) {
                Text(req.appUrl, style = MaterialTheme.typography.bodySmall)
            }
            if (req.isLocalSocket) {
                Text(Strings.get("d_via_local_socket", language), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(req.type.describe(req.kind, req.encryptedContent, language), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            // The account that signs this request; a connect can be moved to
            // another account, like the Android connect screen's picker.
            SigningAccountRow(
                npub = if (req.type == SignerType.CONNECT) connectChoice.accountNpub else req.account.npub,
                canSwitch = req.canSwitchAccount,
                onSelect = { UiState.setConnectChoice(req.request.id, connectChoice.copy(accountNpub = it)) },
            )

            if (req.preview.isNotBlank()) {
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

            if (req.type == SignerType.CONNECT) {
                // Mirrors Android's BunkerConnectRequestScreen: pick the sign
                // policy (requested permissions are only granted one by one
                // under the manual policy) and when to delete the connection.
                Spacer(Modifier.height(8.dp))
                Text(Strings.get("permissions", language), style = MaterialTheme.typography.titleSmall)
                SignPolicySelector(connectChoice.signPolicy) {
                    UiState.setConnectChoice(req.request.id, connectChoice.copy(signPolicy = it))
                }
                if (connectChoice.signPolicy == 1 && connectChoice.granted.isNotEmpty()) {
                    Column(Modifier.padding(start = 24.dp)) {
                        connectChoice.granted.forEachIndexed { index, perm ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = perm.checked,
                                    onCheckedChange = { checked ->
                                        val granted = connectChoice.granted.toMutableList()
                                        granted[index] = perm.copy(checked = checked)
                                        UiState.setConnectChoice(req.request.id, connectChoice.copy(granted = granted))
                                    },
                                )
                                Text(
                                    SignerDescriptions.permission(perm.type, perm.kind, language),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                DeleteAfterSelector(connectChoice.deleteAfter) {
                    UiState.setConnectChoice(req.request.id, connectChoice.copy(deleteAfter = it))
                }
            }

            Spacer(Modifier.height(12.dp))
            val isV3 = req.type in nip44v3SignerTypes
            if (isV3) {
                // Mirrors Android's Nip44v3ContextBox: the kind + scope the
                // ciphertext is bound to, i.e. what is being granted.
                Spacer(Modifier.height(4.dp))
                Text(Strings.get("nip44_v3_context", language), style = MaterialTheme.typography.labelMedium)
                val kindLabel = req.kind?.let { "$it (${SignerDescriptions.signEventDescription(it, language)})" } ?: "-"
                Text(Strings.format("nip44_v3_kind", kindLabel, language = language), style = MaterialTheme.typography.bodySmall)
                Text(
                    Strings.format("nip44_v3_scope", req.nip44v3Scope.ifEmpty { Strings.get("nip44_v3_no_scope", language) }, language = language),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (req.type in contentScopedSignerTypes || isV3) {
                // Mirrors the Android "Encryption scope" toggle: NIP-04/44 grants
                // this content type or the whole NIP; NIP-44 v3 this kind or all.
                val scopeChoices by UiState.scopeChoices.collectAsState()
                val scope = scopeChoices[req.request.id] ?: UiState.defaultScope(req)
                Text(Strings.get("encryption_scope", language), style = MaterialTheme.typography.labelMedium)
                val options = if (isV3) {
                    listOf(EncryptionScope.SPECIFIC to "for_this_kind_only", EncryptionScope.ALL to "for_all_kinds")
                } else {
                    listOf(EncryptionScope.SPECIFIC to "for_this_method_only", EncryptionScope.ALL to "for_all_methods")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (value, key) ->
                        FilterChip(
                            selected = scope == value,
                            onClick = { UiState.setScopeChoice(req.request.id, value) },
                            label = { Text(Strings.get(key, language)) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            if (req.type != SignerType.CONNECT) {
                RememberTypeSelector(rememberType) { UiState.setRememberChoice(req.request.id, it) }
                Spacer(Modifier.height(8.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AmberButton(
                    text = if (working) Strings.get("d_working", language) else Strings.get("d_approve", language),
                    enabled = !working,
                    onClick = {
                        working = true
                        // approve() runs on the engine's application scope, so it
                        // survives this card leaving composition and still
                        // publishes the relay response. Toast optimistically.
                        UiState.approve(req)
                        Toaster.toast(Strings.get("d_request_approved", language))
                    },
                )
                AmberOutlinedButton(
                    text = Strings.get("reject", language),
                    onClick = {
                        working = true
                        UiState.reject(req)
                        Toaster.toast(Strings.get("d_request_rejected", language))
                    },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    Strings.format(
                        when {
                            req.type == SignerType.CONNECT -> "d_shortcut_hint_connect"
                            req.type in contentScopedSignerTypes || req.type in nip44v3SignerTypes -> "d_shortcut_hint_scope"
                            else -> "d_shortcut_hint"
                        },
                        shortcutLabel("↵"),
                        shortcutLabel("↵", shift = true),
                        language = language,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * "Account: name · npub…" for a request; with [canSwitch] and more than one
 * account, a dropdown to pick the account a connection is saved under.
 */
@Composable
private fun SigningAccountRow(
    npub: String,
    canSwitch: Boolean,
    onSelect: (String) -> Unit,
) {
    val language by Strings.currentLanguage.collectAsState()
    val accounts by AccountsStore.accounts.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val switchable = canSwitch && accounts.size > 1

    fun label(npub: String): String {
        val name = accounts.firstOrNull { it.npub == npub }?.name.orEmpty()
        return if (name.isBlank()) npub.toShortenHex() else "$name · ${npub.toShortenHex()}"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${Strings.get("account", language)}:",
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
                    text = "${Strings.get("switch_account", language)} (A)",
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
