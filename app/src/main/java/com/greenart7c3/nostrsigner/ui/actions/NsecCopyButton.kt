package com.greenart7c3.nostrsigner.ui.actions

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.copy_to_clipboard
import com.greenart7c3.nostrsigner.commonsui.encrypt_and_copy_to_clipboard
import com.greenart7c3.nostrsigner.commonsui.failed_to_encrypt_key
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.password_is_required
import com.greenart7c3.nostrsigner.commonsui.secret_key_copied_to_clipboard
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.Biometrics.authenticate
import com.greenart7c3.nostrsigner.ui.LocalAppScope
import com.greenart7c3.nostrsigner.ui.setSensitiveClip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NSecCopyButton(
    account: Account,
    password: String,
    onLoading: (Boolean) -> Unit,
    onBackupFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appScope = LocalAppScope.current
    val appContext = LocalContext.current.applicationContext
    val clipboardManager = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val keyguardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (password.isNotBlank()) {
                encryptCopyNSec(password, appContext, account, clipboardManager, appScope, onLoading, onBackupFinish)
            } else {
                copyNSec(account, clipboardManager, onBackupFinish)
            }
        }
    }

    val text = stringResource(if (password.isBlank()) Res.string.copy_to_clipboard else Res.string.encrypt_and_copy_to_clipboard)

    BackupIconButton(
        modifier = modifier,
        icon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
        text = text,
        onClick = {
            authenticate(
                title = text,
                context = context,
                keyguardLauncher = keyguardLauncher,
                onApproved = {
                    scope.launch(Dispatchers.IO) {
                        account.didBackup = true
                        onBackupFinish()
                        if (password.isNotBlank()) {
                            encryptCopyNSec(password, appContext, account, clipboardManager, appScope, onLoading, onBackupFinish)
                        } else {
                            copyNSec(account, clipboardManager, onBackupFinish)
                        }
                    }
                },
                onError = { _, message ->
                    scope.launch {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        },
    )
}

private fun copyNSec(
    account: Account,
    clipboardManager: Clipboard,
    onBackupFinish: () -> Unit,
) {
    account.keyExport.copyToClipboard(clipboardManager)
    onBackupFinish()
}

private fun encryptCopyNSec(
    password: String,
    context: Context,
    account: Account,
    clipboardManager: Clipboard,
    appScope: CoroutineScope,
    onLoading: (Boolean) -> Unit,
    onBackupFinish: () -> Unit,
) {
    if (password.isBlank()) {
        appScope.launch(Dispatchers.Main) {
            Toast.makeText(context, getStringBlocking(Res.string.password_is_required), Toast.LENGTH_SHORT).show()
        }
    } else {
        appScope.launch(Dispatchers.IO) {
            onLoading(true)
            try {
                val key = account.keyExport.nip49Encrypt(password)
                account.didBackup = true
                onBackupFinish()
                appScope.launch(Dispatchers.Main) {
                    clipboardManager.setSensitiveClip(context, key)
                    Toast.makeText(context, getStringBlocking(Res.string.secret_key_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                appScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, getStringBlocking(Res.string.failed_to_encrypt_key), Toast.LENGTH_SHORT).show()
                }
            } finally {
                onLoading(false)
            }
        }
    }
}
