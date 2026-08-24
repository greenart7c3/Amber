package com.greenart7c3.nostrsigner.ui.components

import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.service.KillSwitchReceiver

/**
 * A connect (NIP-46) request can only be answered while Amber is connected to
 * its relays, but with the kill switch enabled every relay connection is
 * dropped. When a connect request is being displayed in that state, ask the
 * user once whether to disable the kill switch (reusing [KillSwitchReceiver],
 * the same path as the Applications screen banner and the notification action).
 */
@Composable
fun KillSwitchConnectPrompt(hasConnectRequest: Boolean) {
    // Amber.instance doesn't exist in the preview renderer.
    if (LocalInspectionMode.current) return

    val killSwitch by Amber.instance.settings.killSwitch.collectAsStateWithLifecycle()
    var dismissed by remember { mutableStateOf(false) }

    if (hasConnectRequest && killSwitch && !dismissed) {
        val context = LocalContext.current
        AlertDialog(
            title = { Text(text = stringResource(R.string.kill_switch_connect_title)) },
            text = { Text(text = stringResource(R.string.kill_switch_connect_message)) },
            onDismissRequest = { dismissed = true },
            confirmButton = {
                TextButton(
                    onClick = {
                        context.sendBroadcast(Intent(context, KillSwitchReceiver::class.java))
                        dismissed = true
                    },
                ) {
                    Text(text = stringResource(R.string.disable_kill_switch))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { dismissed = true },
                ) {
                    Text(text = stringResource(R.string.no))
                }
            },
        )
    }
}
