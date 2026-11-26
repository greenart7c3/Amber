package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.cancel
import com.greenart7c3.nostrsigner.commonsui.d_change_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_change_the_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_current_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_failed_update_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_lock_15min
import com.greenart7c3.nostrsigner.commonsui.d_lock_1hour
import com.greenart7c3.nostrsigner.commonsui.d_lock_5min
import com.greenart7c3.nostrsigner.commonsui.d_lock_after
import com.greenart7c3.nostrsigner.commonsui.d_lock_never
import com.greenart7c3.nostrsigner.commonsui.d_lock_now
import com.greenart7c3.nostrsigner.commonsui.d_new_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_os_secret_unavailable
import com.greenart7c3.nostrsigner.commonsui.d_passphrase_changed
import com.greenart7c3.nostrsigner.commonsui.d_passphrase_never_stored
import com.greenart7c3.nostrsigner.commonsui.d_passphrases_no_match
import com.greenart7c3.nostrsigner.commonsui.d_repeat_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_use_12_chars
import com.greenart7c3.nostrsigner.commonsui.d_working
import com.greenart7c3.nostrsigner.commonsui.d_wrong_current_passphrase
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.save
import com.greenart7c3.nostrsigner.desktop.core.PassphraseLock
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PassphraseField(
    label: StringResource,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ColumnScope.SecuritySection() {
    val settings by SettingsStore.settings.collectAsState()
    var showChangeDialog by remember { mutableStateOf(false) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AmberOutlinedButton(
            text = stringResource(Res.string.d_lock_now),
            onClick = { PassphraseLock.lock() },
        )
        AmberOutlinedButton(
            text = stringResource(Res.string.d_change_passphrase),
            onClick = { showChangeDialog = true },
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(stringResource(Res.string.d_lock_after), style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            0 to stringResource(Res.string.d_lock_never),
            5 to stringResource(Res.string.d_lock_5min),
            15 to stringResource(Res.string.d_lock_15min),
            60 to stringResource(Res.string.d_lock_1hour),
        ).forEach { (minutes, label) ->
            FilterChip(
                selected = settings.autoLockMinutes == minutes,
                onClick = {
                    SettingsStore.update { it.copy(autoLockMinutes = minutes) }
                    PassphraseLock.touch()
                },
                label = { Text(label) },
            )
        }
    }

    if (showChangeDialog) {
        ChangePassphraseDialog { showChangeDialog = false }
    }
}

@Composable
private fun ChangePassphraseDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var newPassphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(Res.string.d_change_the_passphrase)) },
        text = {
            Column {
                Text(stringResource(Res.string.d_passphrase_never_stored), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                PassphraseField(label = Res.string.d_current_passphrase, value = current, onValueChange = { current = it })
                Spacer(Modifier.height(8.dp))
                PassphraseField(label = Res.string.d_new_passphrase, value = newPassphrase, onValueChange = { newPassphrase = it })
                Spacer(Modifier.height(8.dp))
                PassphraseField(label = Res.string.d_repeat_passphrase, value = confirm, onValueChange = { confirm = it })
            }
        },
        confirmButton = {
            AmberTextButton(
                text = if (working) stringResource(Res.string.d_working) else stringResource(Res.string.save),
                enabled = !working,
                onClick = {
                    val error = validateNewPassphrase(newPassphrase, confirm)
                    if (error != null) {
                        Toaster.toast(getStringBlocking(error))
                        return@AmberTextButton
                    }
                    working = true
                    scope.launch {
                        try {
                            if (applyPassphraseChange(current, newPassphrase)) onDismiss()
                        } finally {
                            working = false
                        }
                    }
                },
            )
        },
        dismissButton = {
            AmberTextButton(
                text = stringResource(Res.string.cancel),
                enabled = !working,
                onClick = onDismiss,
            )
        },
    )
}

private fun validateNewPassphrase(newPassphrase: String, confirm: String): StringResource? = when {
    newPassphrase.length < PassphraseLock.MIN_PASSPHRASE_LENGTH -> Res.string.d_use_12_chars
    newPassphrase != confirm -> Res.string.d_passphrases_no_match
    else -> null
}

private suspend fun applyPassphraseChange(current: String, newPassphrase: String): Boolean = try {
    if (PassphraseLock.changePassphrase(current.toCharArray(), newPassphrase.toCharArray())) {
        Toaster.toast(getStringBlocking(Res.string.d_passphrase_changed))
        true
    } else {
        Toaster.toast(getStringBlocking(Res.string.d_wrong_current_passphrase))
        false
    }
} catch (e: PassphraseLock.OsSecretUnavailableException) {
    Toaster.toast(getStringBlocking(Res.string.d_os_secret_unavailable, e.storeDescription))
    false
} catch (e: Exception) {
    Toaster.toast(e.message ?: getStringBlocking(Res.string.d_failed_update_passphrase))
    false
}
