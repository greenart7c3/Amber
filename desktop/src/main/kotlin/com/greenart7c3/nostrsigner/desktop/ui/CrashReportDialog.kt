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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.cancel
import com.greenart7c3.nostrsigner.commonsui.copy
import com.greenart7c3.nostrsigner.commonsui.crash_report_send_anonymously
import com.greenart7c3.nostrsigner.commonsui.crash_report_send_prompt
import com.greenart7c3.nostrsigner.commonsui.crashreport_found
import com.greenart7c3.nostrsigner.commonsui.crashreport_found_send
import com.greenart7c3.nostrsigner.commonsui.d_copied_clipboard
import com.greenart7c3.nostrsigner.commonsui.d_crash_report_send_failed
import com.greenart7c3.nostrsigner.commonsui.d_crash_report_sent
import com.greenart7c3.nostrsigner.commonsui.d_working
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.desktop.core.CrashReporter
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Desktop counterpart of the Android `CrashReportScreen`: shows the saved
 * report for review and editing, then sends it to the developer as a NIP-17
 * DM from [account] or anonymously from a throwaway key, copies it, or
 * discards it.
 */
@Composable
fun CrashReportDialog(
    report: String,
    account: DesktopAccount,
) {
    val scope = rememberCoroutineScope()
    val copyText = rememberCopyText()
    var edited by remember(report) { mutableStateOf(report) }
    var sending by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(Res.string.crashreport_found)) },
        text = {
            Column {
                Text(
                    stringResource(Res.string.crash_report_send_prompt),
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
                    text = stringResource(Res.string.copy),
                    enabled = !sending,
                    onClick = {
                        copyText(edited)
                        Toaster.toast(getStringBlocking(Res.string.d_copied_clipboard))
                    },
                )
                val send = { anonymous: Boolean ->
                    sending = true
                    scope.launch {
                        if (CrashReporter.send(account, edited, anonymous)) {
                            Toaster.toast(getStringBlocking(Res.string.d_crash_report_sent))
                        } else {
                            Toaster.toast(getStringBlocking(Res.string.d_crash_report_send_failed))
                        }
                        sending = false
                    }
                }
                AmberTextButton(
                    text = stringResource(Res.string.crash_report_send_anonymously),
                    enabled = !sending && edited.isNotBlank(),
                    onClick = { send(true) },
                )
                AmberTextButton(
                    text = if (sending) stringResource(Res.string.d_working) else stringResource(Res.string.crashreport_found_send),
                    enabled = !sending && edited.isNotBlank(),
                    onClick = { send(false) },
                )
            }
        },
        dismissButton = {
            AmberTextButton(
                text = stringResource(Res.string.cancel),
                enabled = !sending,
                onClick = { CrashReporter.dismiss() },
            )
        },
    )
}
