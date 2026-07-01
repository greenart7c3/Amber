package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.greenart7c3.nostrsigner.desktop.ui.components.LetterAvatar
import com.greenart7c3.nostrsigner.desktop.ui.components.shortenHex
import com.greenart7c3.nostrsigner.desktop.ui.theme.negativeColor

@Composable
fun AccountSwitcherDialog(
    accounts: List<String>,
    currentPubKeyHex: String,
    onSelect: (String) -> Unit,
    onAddAccount: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp).width(320.dp)) {
                Text("Accounts", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))

                accounts.forEach { pubKeyHex ->
                    val isCurrent = pubKeyHex == currentPubKeyHex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isCurrent) { onSelect(pubKeyHex) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LetterAvatar(pubKeyHex, size = 32.dp)
                        Text(
                            pubKeyHex.shortenHex() + if (isCurrent) " (current)" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    "+ Add another account",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onAddAccount).padding(vertical = 8.dp),
                )
                Text(
                    "Log out",
                    style = MaterialTheme.typography.bodyMedium,
                    color = negativeColor,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onLogout).padding(vertical = 8.dp),
                )
            }
        }
    }
}
