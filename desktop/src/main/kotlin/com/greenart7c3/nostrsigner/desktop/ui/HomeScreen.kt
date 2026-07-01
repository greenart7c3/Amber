package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.ui.components.LetterAvatar
import com.greenart7c3.nostrsigner.desktop.ui.components.shortenHex
import com.greenart7c3.nostrsigner.desktop.ui.theme.negativeColor
import com.greenart7c3.nostrsigner.desktop.ui.theme.positiveColor

@Composable
fun HomeScreen(
    pubKeyHex: String,
    totalRelays: Int,
    connectedRelayCount: Int,
    connectedAppsCount: Int,
    pendingApprovalCount: Int,
    onCopyPubKey: () -> Unit,
    onOpenAccountMenu: () -> Unit,
) {
    Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
        Row {
            LetterAvatar(pubKeyHex, size = 56.dp, modifier = Modifier.clickable(onClick = onOpenAccountMenu))
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Amber Bunker", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    pubKeyHex.shortenHex(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable(onClick = onCopyPubKey),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            if (connectedRelayCount == 0 && totalRelays > 0) {
                "Connecting to relays…"
            } else {
                "Connected to $connectedRelayCount of $totalRelays relays"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (connectedRelayCount > 0) positiveColor else negativeColor,
        )

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard("Connected apps", connectedAppsCount)
            StatCard("Pending approvals", pendingApprovalCount)
        }
    }
}

@Composable
private fun StatCard(label: String, count: Int) {
    Card(
        modifier = Modifier.width(180.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("$count", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}
