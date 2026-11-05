package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.d_failed_update_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_locked
import com.greenart7c3.nostrsigner.commonsui.d_new_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_os_secret_unavailable
import com.greenart7c3.nostrsigner.commonsui.d_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_passphrase_desc
import com.greenart7c3.nostrsigner.commonsui.d_passphrase_never_stored
import com.greenart7c3.nostrsigner.commonsui.d_passphrases_no_match
import com.greenart7c3.nostrsigner.commonsui.d_repeat_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_set_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_unlock
import com.greenart7c3.nostrsigner.commonsui.d_unlocking
import com.greenart7c3.nostrsigner.commonsui.d_use_12_chars
import com.greenart7c3.nostrsigner.commonsui.d_working
import com.greenart7c3.nostrsigner.commonsui.d_wrong_passphrase
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.desktop.core.BuildVariant
import com.greenart7c3.nostrsigner.desktop.core.PassphraseLock
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun UnlockScreen() {
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val passphraseFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { passphraseFocus.requestFocus() }

    fun submit() {
        if (passphrase.isEmpty() || working) return
        working = true
        error = null
        scope.launch {
            try {
                if (PassphraseLock.unlock(passphrase.toCharArray())) {
                    passphrase = ""
                } else {
                    error = getStringBlocking(Res.string.d_wrong_passphrase)
                }
            } catch (e: PassphraseLock.OsSecretUnavailableException) {
                error = getStringBlocking(Res.string.d_os_secret_unavailable, e.storeDescription)
            }
            working = false
        }
    }

    LockScreenScaffold(
        subtitle = stringResource(Res.string.d_locked),
        onEnter = { submit() },
    ) {
        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text(stringResource(Res.string.d_passphrase)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().focusRequester(passphraseFocus),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        AmberButton(
            text = if (working) stringResource(Res.string.d_unlocking) else stringResource(Res.string.d_unlock),
            fillWidth = true,
            enabled = passphrase.isNotEmpty() && !working,
            onClick = ::submit,
        )
    }
}

/**
 * First-run gate: Amber refuses to start until a passphrase is set. There is
 * no way past this screen and no way back to unprotected key storage.
 */
@Composable
fun PassphraseSetupScreen() {
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val passphraseFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { passphraseFocus.requestFocus() }

    fun submit() {
        if (working) return
        error = null
        if (passphrase.length < PassphraseLock.MIN_PASSPHRASE_LENGTH) {
            error = getStringBlocking(Res.string.d_use_12_chars)
            return
        }
        if (passphrase != confirm) {
            error = getStringBlocking(Res.string.d_passphrases_no_match)
            return
        }
        working = true
        scope.launch {
            try {
                PassphraseLock.enable(passphrase.toCharArray())
                passphrase = ""
                confirm = ""
            } catch (e: Exception) {
                error = e.message ?: getStringBlocking(Res.string.d_failed_update_passphrase)
            }
            working = false
        }
    }

    LockScreenScaffold(
        subtitle = stringResource(Res.string.d_passphrase_desc),
        onEnter = { submit() },
    ) {
        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text(stringResource(Res.string.d_new_passphrase)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().focusRequester(passphraseFocus),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text(stringResource(Res.string.d_repeat_passphrase)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        AmberButton(
            text = if (working) stringResource(Res.string.d_working) else stringResource(Res.string.d_set_passphrase),
            fillWidth = true,
            enabled = !working,
            onClick = ::submit,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.d_passphrase_never_stored),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shared centered layout for the lock and first-run passphrase screens. */
@Composable
private fun LockScreenScaffold(
    subtitle: String,
    onEnter: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                // Enter (or numpad enter) runs the primary action, the way
                // every password prompt behaves.
                if (onEnter != null &&
                    event.type == KeyEventType.KeyUp &&
                    (event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onEnter()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 480.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                BuildVariant.appName,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            content()
        }
    }
}
