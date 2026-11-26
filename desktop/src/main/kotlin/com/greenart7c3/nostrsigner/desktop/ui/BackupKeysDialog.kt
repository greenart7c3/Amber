package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.backup_keys
import com.greenart7c3.nostrsigner.commonsui.cancel
import com.greenart7c3.nostrsigner.commonsui.d_close
import com.greenart7c3.nostrsigner.commonsui.d_copy_nsec
import com.greenart7c3.nostrsigner.commonsui.d_copy_nsec_warning
import com.greenart7c3.nostrsigner.commonsui.d_encrypt_and_copy
import com.greenart7c3.nostrsigner.commonsui.d_encrypted_backup
import com.greenart7c3.nostrsigner.commonsui.d_encrypted_key_copied
import com.greenart7c3.nostrsigner.commonsui.d_nsec_copied
import com.greenart7c3.nostrsigner.commonsui.d_password
import com.greenart7c3.nostrsigner.commonsui.d_password_required
import com.greenart7c3.nostrsigner.commonsui.d_secret_key_nsec
import com.greenart7c3.nostrsigner.commonsui.d_seed_words
import com.greenart7c3.nostrsigner.commonsui.d_show
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.warning
import com.greenart7c3.nostrsigner.desktop.Session
import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BackupDialog(
    account: DesktopAccount,
    onDismiss: () -> Unit,
) {
    val copyText = rememberCopyText()
    var confirmCopy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.backup_keys)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PlainKeyBackupSection(account = account, onRequestCopy = { confirmCopy = true })
                Spacer(Modifier.height(12.dp))
                EncryptedKeyBackupSection(account = account, copyText = copyText)
            }
        },
        confirmButton = {
            AmberTextButton(text = stringResource(Res.string.d_close), onClick = onDismiss)
        },
    )
    if (confirmCopy) {
        ConfirmCopyNsecDialog(
            onConfirm = {
                confirmCopy = false
                copyText(account.getNsec())
                account.didBackup = true
                Session.saveMeta(account)
                Toaster.toast(getStringBlocking(Res.string.d_nsec_copied))
            },
            onDismiss = { confirmCopy = false },
        )
    }
}

@Composable
private fun ColumnScope.PlainKeyBackupSection(
    account: DesktopAccount,
    onRequestCopy: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showSecret by remember { mutableStateOf(false) }
    var seedWords by remember { mutableStateOf("") }

    Text(stringResource(Res.string.d_secret_key_nsec), style = MaterialTheme.typography.titleSmall)
    if (showSecret) {
        Text(account.getNsec(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        if (seedWords.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.d_seed_words), style = MaterialTheme.typography.titleSmall)
            Text(seedWords, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    } else {
        AmberTextButton(
            text = stringResource(Res.string.d_show),
            onClick = {
                showSecret = true
                scope.launch { seedWords = AccountManager.seedWords(account.npub) }
            },
        )
    }
    Row {
        AmberTextButton(text = stringResource(Res.string.d_copy_nsec), onClick = onRequestCopy)
    }
}

@Composable
private fun ColumnScope.EncryptedKeyBackupSection(
    account: DesktopAccount,
    copyText: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var ncryptsec by remember { mutableStateOf("") }

    Text(stringResource(Res.string.d_encrypted_backup), style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text(stringResource(Res.string.d_password)) },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    if (ncryptsec.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(
            ncryptsec,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.heightIn(max = 90.dp).verticalScroll(rememberScrollState()),
        )
    }
    AmberTextButton(
        text = stringResource(Res.string.d_encrypt_and_copy),
        onClick = {
            if (password.isBlank()) {
                Toaster.toast(getStringBlocking(Res.string.d_password_required))
                return@AmberTextButton
            }
            ncryptsec = account.nip49Encrypt(password)
            copyText(ncryptsec)
            account.didBackup = true
            Session.saveMeta(account)
            Toaster.toast(getStringBlocking(Res.string.d_encrypted_key_copied))
        },
    )
}

@Composable
private fun ConfirmCopyNsecDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.warning)) },
        text = { Text(stringResource(Res.string.d_copy_nsec_warning)) },
        confirmButton = {
            AmberTextButton(text = stringResource(Res.string.d_copy_nsec), onClick = onConfirm)
        },
        dismissButton = {
            AmberTextButton(text = stringResource(Res.string.cancel), onClick = onDismiss)
        },
    )
}
