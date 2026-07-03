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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.SignerDescriptions
import com.greenart7c3.nostrsigner.desktop.core.SignerType
import com.greenart7c3.nostrsigner.desktop.core.Strings
import com.greenart7c3.nostrsigner.desktop.core.describe
import com.greenart7c3.nostrsigner.desktop.core.toShortenHex

@Composable
fun IncomingRequestsScreen(account: DesktopAccount) {
    val pending by AmberDesktop.engine.pending.collectAsState()

    if (pending.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No requests waiting for approval", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Connect an application from the Applications tab and its signing requests will show up here.",
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
    val grantedPermissions = remember(req.request.id) { req.requestedPermissions.map { it.copy() } }

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
            Spacer(Modifier.height(4.dp))
            Text(req.type.describe(req.kind, language), style = MaterialTheme.typography.bodyLarge)
            Text(
                "${Strings.get("account", language)}: ${req.account.npub.toShortenHex()}",
                style = MaterialTheme.typography.bodySmall,
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

            if (req.type == SignerType.CONNECT && grantedPermissions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(Strings.get("permissions", language), style = MaterialTheme.typography.titleSmall)
                grantedPermissions.forEach { perm ->
                    var checked by remember { mutableStateOf(perm.checked) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                checked = it
                                perm.checked = it
                            },
                        )
                        Text(
                            SignerDescriptions.permission(perm.type, perm.kind, language),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (req.type != SignerType.CONNECT) {
                RememberTypeSelector(rememberType) { UiState.setRememberChoice(req.request.id, it) }
                Spacer(Modifier.height(8.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AmberButton(
                    text = if (working) "Working…" else "Approve",
                    enabled = !working,
                    onClick = {
                        working = true
                        // approve() runs on the engine's application scope, so it
                        // survives this card leaving composition and still
                        // publishes the relay response. Toast optimistically.
                        AmberDesktop.engine.approve(
                            req,
                            if (req.type == SignerType.CONNECT) RememberType.ALWAYS else rememberType,
                            grantedPermissions,
                        )
                        Toaster.toast("Request approved")
                    },
                )
                AmberOutlinedButton(
                    text = "Reject",
                    onClick = {
                        working = true
                        AmberDesktop.engine.reject(req, rememberType)
                        Toaster.toast("Request rejected")
                    },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "↑↓ select · ←→ remember · ${shortcutLabel("↵")} approve · ${shortcutLabel("↵", shift = true)} reject",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
