package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.core.CrashReporter
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.Strings
import kotlinx.coroutines.launch

/**
 * Desktop counterpart of the Android `CrashReportScreen`: shows the saved
 * report for review and editing, then sends it to the developer as a NIP-17
 * DM from [account], copies it, or discards it.
 */
@Composable
fun CrashReportDialog(
    report: String,
    account: DesktopAccount,
) {
    val language by Strings.currentLanguage.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var edited by remember(report) { mutableStateOf(report) }
    var sending by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(Strings.get("crashreport_found", language)) },
        text = {
            Column {
                Text(
                    Strings.get("would_you_like_to_send_the_recent_crash_report_to_amber_in_a_dm_no_personal_information_will_be_shared", language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = edited,
                    onValueChange = { edited = it },
                    enabled = !sending,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 360.dp),
                )
            }
        },
        confirmButton = {
            Row {
                AmberTextButton(
                    text = Strings.get("copy", language),
                    enabled = !sending,
                    onClick = {
                        clipboard.setText(AnnotatedString(edited))
                        Toaster.toast(Strings.get("d_copied_clipboard", language))
                    },
                )
                AmberTextButton(
                    text = if (sending) Strings.get("d_working", language) else Strings.get("crashreport_found_send", language),
                    enabled = !sending && edited.isNotBlank(),
                    onClick = {
                        sending = true
                        scope.launch {
                            if (CrashReporter.send(account, edited)) {
                                Toaster.toast(Strings.get("d_crash_report_sent", language))
                            } else {
                                Toaster.toast(Strings.get("d_crash_report_send_failed", language))
                            }
                            sending = false
                        }
                    },
                )
            }
        },
        dismissButton = {
            AmberTextButton(
                text = Strings.get("cancel", language),
                enabled = !sending,
                onClick = { CrashReporter.dismiss() },
            )
        },
    )
}
