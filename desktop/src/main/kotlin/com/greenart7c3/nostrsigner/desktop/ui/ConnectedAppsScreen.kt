package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.data.ConnectedApp
import com.greenart7c3.nostrsigner.desktop.ui.components.LetterAvatar
import com.greenart7c3.nostrsigner.desktop.ui.components.relativeTimeFromNow
import com.greenart7c3.nostrsigner.desktop.ui.components.shortenHex

@Composable
fun ConnectedAppsScreen(connectedApps: List<ConnectedApp>, onAppClick: (String) -> Unit) {
    Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
        Text("Connected apps", style = MaterialTheme.typography.titleLarge)

        if (connectedApps.isEmpty()) {
            Text(
                "No apps connected yet — pair one from the Connect screen.",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
            items(connectedApps, key = { it.pubKey }) { app ->
                val displayName = app.name.ifBlank { app.pubKey.shortenHex() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAppClick(app.pubKey) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LetterAvatar(displayName)
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(displayName, style = MaterialTheme.typography.titleMedium)
                        Text(app.pubKey.shortenHex(), style = MaterialTheme.typography.bodySmall)
                        Text("Last used ${relativeTimeFromNow(app.connectedAt)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
