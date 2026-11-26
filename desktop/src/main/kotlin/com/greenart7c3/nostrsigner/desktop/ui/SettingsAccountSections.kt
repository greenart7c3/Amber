package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.backup_keys
import com.greenart7c3.nostrsigner.commonsui.cancel
import com.greenart7c3.nostrsigner.commonsui.d_active
import com.greenart7c3.nostrsigner.commonsui.d_add_an_account
import com.greenart7c3.nostrsigner.commonsui.d_log_out
import com.greenart7c3.nostrsigner.commonsui.d_log_out_confirm
import com.greenart7c3.nostrsigner.commonsui.d_log_out_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_log_out_q
import com.greenart7c3.nostrsigner.commonsui.d_os_secret_unavailable
import com.greenart7c3.nostrsigner.commonsui.d_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_policy_approve
import com.greenart7c3.nostrsigner.commonsui.d_policy_basic
import com.greenart7c3.nostrsigner.commonsui.d_policy_sign_all
import com.greenart7c3.nostrsigner.commonsui.d_public_key
import com.greenart7c3.nostrsigner.commonsui.d_saved
import com.greenart7c3.nostrsigner.commonsui.d_switch
import com.greenart7c3.nostrsigner.commonsui.d_working
import com.greenart7c3.nostrsigner.commonsui.d_wrong_passphrase
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.name
import com.greenart7c3.nostrsigner.commonsui.save
import com.greenart7c3.nostrsigner.desktop.Session
import com.greenart7c3.nostrsigner.desktop.core.AccountRecord
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.PassphraseLock
import com.greenart7c3.nostrsigner.desktop.core.toShortenHex
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ColumnScope.AccountSection(
    account: DesktopAccount,
    onOpenBackup: () -> Unit,
) {
    val accountName by account.name.collectAsState()
    var name by remember(account.npub, accountName) { mutableStateOf(accountName) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(Res.string.name)) },
            singleLine = true,
            modifier = Modifier.widthIn(max = 420.dp).weight(1f, fill = false),
        )
        AmberOutlinedButton(
            text = stringResource(Res.string.save),
            onClick = {
                account.name.value = name
                Session.saveMeta(account)
                Toaster.toast(getStringBlocking(Res.string.d_saved))
            },
        )
    }
    Text(
        stringResource(Res.string.d_public_key, account.npub),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    AmberButton(text = stringResource(Res.string.backup_keys), onClick = onOpenBackup)
}

@Composable
internal fun SignPolicySection(account: DesktopAccount) {
    val policies = listOf(
        0 to stringResource(Res.string.d_policy_basic),
        1 to stringResource(Res.string.d_policy_approve),
        2 to stringResource(Res.string.d_policy_sign_all),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        policies.forEach { (value, label) ->
            FilterChip(
                selected = account.signPolicy == value,
                onClick = {
                    account.signPolicy = value
                    Session.saveMeta(account)
                },
                label = { Text(label) },
            )
        }
    }
}

@Composable
internal fun ColumnScope.AccountsSection(
    accounts: List<AccountRecord>,
    activeNpub: String,
    onLogout: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    accounts.forEach { record ->
        AccountRow(
            record = record,
            isActive = record.npub == activeNpub,
            onSwitch = { scope.launch { Session.switchTo(record.npub) } },
            onLogout = { onLogout(record.npub) },
        )
        HorizontalDivider()
    }
    Spacer(Modifier.height(8.dp))
    AmberOutlinedButton(text = stringResource(Res.string.d_add_an_account), onClick = { Session.addingAccount.value = true })
}

@Composable
private fun AccountRow(
    record: AccountRecord,
    isActive: Boolean,
    onSwitch: () -> Unit,
    onLogout: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatar(record.npub)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                record.name.ifBlank { record.npub.toShortenHex() },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (isActive) {
                Text(
                    stringResource(Res.string.d_active),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!isActive) {
            AmberTextButton(
                text = stringResource(Res.string.d_switch),
                onClick = onSwitch,
            )
        }
        AmberTextButton(
            text = stringResource(Res.string.d_log_out),
            onClick = onLogout,
        )
    }
}

@Composable
internal fun LogoutConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var loggingOut by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!loggingOut) onDismiss() },
        title = { Text(stringResource(Res.string.d_log_out_q)) },
        text = {
            Column {
                Text(stringResource(Res.string.d_log_out_confirm))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.d_log_out_passphrase))
                Spacer(Modifier.height(4.dp))
                PassphraseField(
                    label = Res.string.d_passphrase,
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    enabled = !loggingOut,
                )
            }
        },
        confirmButton = {
            AmberTextButton(
                text = if (loggingOut) stringResource(Res.string.d_working) else stringResource(Res.string.d_log_out),
                enabled = !loggingOut,
                onClick = {
                    loggingOut = true
                    scope.launch {
                        try {
                            if (verifyPassphrase(passphrase)) onConfirm()
                        } finally {
                            loggingOut = false
                        }
                    }
                },
            )
        },
        dismissButton = {
            AmberTextButton(
                text = stringResource(Res.string.cancel),
                enabled = !loggingOut,
                onClick = onDismiss,
            )
        },
    )
}

private suspend fun verifyPassphrase(passphrase: String): Boolean = try {
    val verified = PassphraseLock.verify(passphrase.toCharArray())
    if (!verified) Toaster.toast(getStringBlocking(Res.string.d_wrong_passphrase))
    verified
} catch (e: PassphraseLock.OsSecretUnavailableException) {
    Toaster.toast(getStringBlocking(Res.string.d_os_secret_unavailable, e.storeDescription))
    false
}
