package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.data.StoredPermission
import com.greenart7c3.nostrsigner.desktop.ui.components.ConfirmDialog
import com.greenart7c3.nostrsigner.desktop.ui.components.LetterAvatar
import com.greenart7c3.nostrsigner.desktop.ui.components.bunkerMethodDescription
import com.greenart7c3.nostrsigner.desktop.ui.components.relativeTimeFromNow
import com.greenart7c3.nostrsigner.desktop.ui.components.shortenHex
import com.greenart7c3.nostrsigner.desktop.ui.theme.negativeColor
import com.greenart7c3.nostrsigner.desktop.ui.theme.orange
import com.greenart7c3.nostrsigner.shared.BunkerHistoryEntry

@Composable
fun AppDetailScreen(
    appPubKey: String,
    appName: String,
    bunkerUri: String,
    permissions: List<StoredPermission>,
    recentActivity: List<BunkerHistoryEntry>,
    onAllow: (StoredPermission) -> Unit,
    onDeny: (StoredPermission) -> Unit,
    onAsk: (StoredPermission) -> Unit,
    onRevokeAll: () -> Unit,
    onRemoveApp: () -> Unit,
    onCopyUri: (String) -> Unit,
    onBack: () -> Unit,
) {
    val displayName = appName.ifBlank { appPubKey.shortenHex() }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
        Row {
            IconButton(onClick = onBack) { Text("←") }
            Spacer(Modifier.width(8.dp))
            LetterAvatar(displayName)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(appPubKey.shortenHex(), style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(bunkerUri, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        TextButton(onClick = { onCopyUri(bunkerUri) }) { Text("Copy connection string") }

        if (recentActivity.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Recent activity", style = MaterialTheme.typography.titleMedium)
            recentActivity.take(10).forEach { entry ->
                Text(
                    "${bunkerMethodDescription(entry.method, entry.kind)} — ${if (entry.approved) "allowed" else "rejected"} ${relativeTimeFromNow(entry.time)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Permissions", style = MaterialTheme.typography.titleMedium)
        Text(
            "Allow or deny future requests for this app; choosing Ask again removes the stored rule.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        if (permissions.isEmpty()) {
            Text("No stored permissions yet — you'll be prompted on the next request.", style = MaterialTheme.typography.bodyMedium)
        } else {
            permissions.forEach { permission ->
                PermissionRow(permission, onAllow, onDeny, onAsk)
            }
        }

        if (permissions.isNotEmpty()) {
            Button(
                modifier = Modifier.padding(top = 32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = orange),
                onClick = onRevokeAll,
            ) {
                Text("Revoke all permissions")
            }
        }

        Button(
            modifier = Modifier.padding(top = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = negativeColor),
            onClick = { showRemoveConfirm = true },
        ) {
            Text("Remove app")
        }
    }

    if (showRemoveConfirm) {
        ConfirmDialog(
            title = "Remove $displayName?",
            message = "This removes the app from your connected apps list and revokes all of its permissions. It can reconnect later if it sends a new request.",
            confirmLabel = "Remove",
            onConfirm = {
                showRemoveConfirm = false
                onRemoveApp()
            },
            onCancel = { showRemoveConfirm = false },
        )
    }
}

@Composable
private fun PermissionRow(
    permission: StoredPermission,
    onAllow: (StoredPermission) -> Unit,
    onDeny: (StoredPermission) -> Unit,
    onAsk: (StoredPermission) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(6.dp))
            .padding(12.dp),
    ) {
        Text(bunkerMethodDescription(permission.method, permission.kind), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterToggle("Allow", selected = permission.approved) { onAllow(permission) }
            FilterToggle("Deny", selected = !permission.approved) { onDeny(permission) }
            TextButton(onClick = { onAsk(permission) }) { Text("Ask again") }
        }
    }
}

@Composable
private fun FilterToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = if (selected) {
        ButtonDefaults.buttonColors()
    } else {
        ButtonDefaults.outlinedButtonColors()
    }
    if (selected) {
        Button(onClick = onClick, colors = colors) { Text(label) }
    } else {
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) { Text(label) }
    }
}
