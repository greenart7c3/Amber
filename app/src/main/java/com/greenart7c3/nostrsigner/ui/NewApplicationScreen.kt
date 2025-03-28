package com.greenart7c3.nostrsigner.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.getAppCompatActivity
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.navigation.Route

@Composable
fun NewApplicationScreen(
    modifier: Modifier,
    accountStateViewModel: AccountStateViewModel,
    account: Account,
    navController: NavController,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    if (dialogOpen) {
        SimpleQrCodeScanner {
            dialogOpen = false
            if (!it.isNullOrEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = it.toUri()
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent.putExtra("closeApplication", false)
                intent.`package` = context.packageName
                context.getAppCompatActivity()?.startActivity(intent)
                accountStateViewModel.switchUser(account.npub, Route.IncomingRequest.route)
            }
        }
    }

    Column(
        modifier =
        modifier
            .fillMaxSize(),
    ) {
        Text(
            modifier = Modifier.padding(bottom = 20.dp),
            text = stringResource(R.string.new_app_description),
        )

        AmberButton(
            onClick = {
                val clipboardText = clipboardManager.getText()
                if (clipboardText == null) {
                    accountStateViewModel.toast(
                        context.getString(R.string.warning),
                        context.getString(R.string.invalid_nostr_connect_uri),
                    )
                    return@AmberButton
                }

                if (clipboardText.text.isBlank()) {
                    accountStateViewModel.toast(
                        context.getString(R.string.warning),
                        context.getString(R.string.invalid_nostr_connect_uri),
                    )
                    return@AmberButton
                }
                if (!clipboardText.text.startsWith("nostrconnect://")) {
                    accountStateViewModel.toast(
                        context.getString(R.string.warning),
                        context.getString(R.string.invalid_nostr_connect_uri),
                    )
                    return@AmberButton
                }

                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = clipboardText.text.toUri()
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent.`package` = context.packageName
                context.getAppCompatActivity()?.startActivity(intent)
                accountStateViewModel.switchUser(account.npub, Route.IncomingRequest.route)
            },
            text = stringResource(R.string.paste_from_clipboard),
        )

        Text(
            modifier = Modifier.padding(bottom = 20.dp),
            text = stringResource(R.string.nostr_connect_description),
        )

        AmberButton(
            onClick = {
                dialogOpen = true
            },
            text = stringResource(R.string.scan_qr_code),
        )

        Text(
            modifier = Modifier.padding(bottom = 20.dp),
            text = stringResource(R.string.nostr_connect_qr_description),
        )

        AmberButton(
            onClick = {
                navController.navigate(Route.NewNsecBunker.route)
            },
            text = stringResource(R.string.add_a_nsecbunker),
        )

        Text(
            stringResource(R.string.nsecbunker_description),
        )
    }
}
