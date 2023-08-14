package com.greenart7c3.nostrsigner.ui.actions

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.Biometrics
import com.greenart7c3.nostrsigner.service.toNpub
import com.greenart7c3.nostrsigner.service.toNsec
import com.greenart7c3.nostrsigner.ui.components.CloseButton
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.material3.Material3RichText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AccountBackupDialog(account: Account, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloseButton(onCancel = onClose)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Material3RichText {
                        Markdown(
                            content = stringResource(R.string.account_backup_tips_md)
                        )
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    NSecCopyButton(account)
                    NPubCopyButton(account)
                }
            }
        }
    }
}

@Composable
private fun NSecCopyButton(
    account: Account
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                copyNSec(context, scope, account, clipboardManager)
            }
        }

    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = {
            Biometrics.authenticate(
                title = context.getString(R.string.copy_my_secret_key),
                context = context,
                scope = scope,
                keyguardLauncher = keyguardLauncher
            ) {
                copyNSec(context, scope, account, clipboardManager)
            }
        },
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Key,
            contentDescription = stringResource(R.string.copies_the_nsec_id_your_password_to_the_clipboard_for_backup),
            Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(id = R.string.copy_my_secret_key))
    }
}

@Composable
private fun NPubCopyButton(
    account: Account
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = {
            clipboardManager.setText(AnnotatedString(account.keyPair.pubKey.toNpub()))
            scope.launch {
                Toast.makeText(
                    context,
                    context.getString(R.string.public_key_npub_copied_to_clipboard),
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Key,
            contentDescription = stringResource(R.string.copies_the_npub_id_your_user_to_the_clipboard_for_backup),
            Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            stringResource(R.string.copy_my_public_key)
        )
    }
}

private fun copyNSec(
    context: Context,
    scope: CoroutineScope,
    account: Account,
    clipboardManager: ClipboardManager
) {
    account.keyPair.privKey?.let {
        clipboardManager.setText(AnnotatedString(it.toNsec()))
        scope.launch {
            Toast.makeText(
                context,
                context.getString(R.string.secret_key_copied_to_clipboard),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
