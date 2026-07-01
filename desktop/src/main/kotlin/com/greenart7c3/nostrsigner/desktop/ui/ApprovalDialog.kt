package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.greenart7c3.nostrsigner.desktop.ui.components.LetterAvatar
import com.greenart7c3.nostrsigner.desktop.ui.components.bunkerMethodDescription
import com.greenart7c3.nostrsigner.desktop.ui.components.shortenHex

@Composable
fun ApprovalDialog(entry: PendingApproval, onAnswer: (approved: Boolean, remember: Boolean) -> Unit) {
    val request = entry.request
    val displayName = request.appName?.takeIf { it.isNotBlank() } ?: request.appPubKey.shortenHex()

    Dialog(onDismissRequest = {}) {
        Card {
            Column(modifier = Modifier.padding(24.dp).width(360.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LetterAvatar(displayName)
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(displayName, style = MaterialTheme.typography.titleMedium)
                        Text(request.appPubKey.shortenHex(), style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(bunkerMethodDescription(request.method, request.kind), style = MaterialTheme.typography.bodyLarge)

                if (request.payloadPreview.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(4.dp))
                            .padding(8.dp),
                    ) {
                        Text(
                            request.payloadPreview,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onAnswer(false, false) }) { Text("Reject") }
                    TextButton(onClick = { onAnswer(true, false) }) { Text("Allow once") }
                    Button(onClick = { onAnswer(true, true) }) { Text("Always allow") }
                }
            }
        }
    }
}
