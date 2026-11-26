package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.d_clear
import com.greenart7c3.nostrsigner.commonsui.d_close
import com.greenart7c3.nostrsigner.commonsui.d_logs
import com.greenart7c3.nostrsigner.commonsui.d_no_logs
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.DateTimes
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LogsDialog(
    account: DesktopAccount,
    onDismiss: () -> Unit,
) {
    val store = AmberDesktop.store(account.npub)
    val logs by store.logs.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.d_logs)) },
        text = {
            val window = LocalWindowContentSize.current
            Column(Modifier.heightIn(max = (window.height - 220.dp).coerceIn(120.dp, 420.dp)).verticalScroll(rememberScrollState())) {
                if (logs.isEmpty()) {
                    Text(stringResource(Res.string.d_no_logs))
                }
                logs.sortedByDescending { it.time }.forEach { log ->
                    Text(
                        "${DateTimes.format(log.time)} · ${log.type} · ${log.url}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(log.message, style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                }
            }
        },
        confirmButton = {
            AmberTextButton(
                text = stringResource(Res.string.d_clear),
                onClick = { store.clearLogs() },
            )
        },
        dismissButton = {
            AmberTextButton(
                text = stringResource(Res.string.d_close),
                onClick = onDismiss,
            )
        },
    )
}
