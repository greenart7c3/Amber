package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.data.HistoryRow
import com.greenart7c3.nostrsigner.desktop.ui.components.LetterAvatar
import com.greenart7c3.nostrsigner.desktop.ui.components.bunkerMethodDescription
import com.greenart7c3.nostrsigner.desktop.ui.components.relativeTimeFromNow
import com.greenart7c3.nostrsigner.desktop.ui.components.shortenHex
import com.greenart7c3.nostrsigner.desktop.ui.theme.negativeColor
import com.greenart7c3.nostrsigner.desktop.ui.theme.positiveColor

@Composable
fun ActivityScreen(history: List<HistoryRow>) {
    Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
        Text("Activity", style = MaterialTheme.typography.titleLarge)

        if (history.isEmpty()) {
            Text(
                "No requests handled yet.",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
            items(history, key = { it.id }) { row ->
                val entry = row.entry
                val displayName = entry.appName?.ifBlank { null } ?: entry.appPubKey.shortenHex()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LetterAvatar(displayName)
                    Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(bunkerMethodDescription(entry.method, entry.kind), style = MaterialTheme.typography.bodySmall)
                    }
                    ApprovedBadge(entry.approved)
                    Text(
                        relativeTimeFromNow(entry.time),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ApprovedBadge(approved: Boolean) {
    val color = if (approved) positiveColor else negativeColor
    Surface(color = color, shape = RoundedCornerShape(4.dp)) {
        Text(
            if (approved) "Allowed" else "Rejected",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
