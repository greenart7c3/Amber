package com.greenart7c3.nostrsigner.ui.actions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.AccountStorage
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.accounts_to_export
import com.greenart7c3.nostrsigner.commonsui.cancel
import com.greenart7c3.nostrsigner.commonsui.confirm_export_message
import com.greenart7c3.nostrsigner.commonsui.confirm_export_title
import com.greenart7c3.nostrsigner.commonsui.confirm_export_warning
import com.greenart7c3.nostrsigner.commonsui.confirm_password
import com.greenart7c3.nostrsigner.commonsui.encryption_password
import com.greenart7c3.nostrsigner.commonsui.enter_strong_password
import com.greenart7c3.nostrsigner.commonsui.export
import com.greenart7c3.nostrsigner.commonsui.export_all_accounts_button
import com.greenart7c3.nostrsigner.commonsui.export_all_accounts_title
import com.greenart7c3.nostrsigner.commonsui.export_all_accounts_warning
import com.greenart7c3.nostrsigner.commonsui.export_failed
import com.greenart7c3.nostrsigner.commonsui.export_success
import com.greenart7c3.nostrsigner.commonsui.exporting_accounts_progress
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.hide_password
import com.greenart7c3.nostrsigner.commonsui.password_is_required
import com.greenart7c3.nostrsigner.commonsui.password_too_short
import com.greenart7c3.nostrsigner.commonsui.passwords_do_not_match
import com.greenart7c3.nostrsigner.commonsui.preparing_export
import com.greenart7c3.nostrsigner.commonsui.show_password
import com.greenart7c3.nostrsigner.service.AccountExportService
import com.greenart7c3.nostrsigner.service.Biometrics.authenticate
import com.greenart7c3.nostrsigner.ui.CenterCircularProgressIndicator
import com.greenart7c3.nostrsigner.ui.LocalAppScope
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.MarkdownText
import com.greenart7c3.nostrsigner.ui.theme.amoledBorder
import com.greenart7c3.nostrsigner.ui.theme.outlineIfAny
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ExportAllAccountsScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appScope = LocalAppScope.current
    val state = remember { ExportAllAccountsState() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            state.accountCount = SavedAccounts.all(context).size
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        uri?.let { state.export(appScope, context, it) }
    }

    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                state.showConfirmationDialog = true
            }
        }

    Surface(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize()
                .padding(16.dp),
        ) {
            if (state.isLoading) {
                ExportProgress(state.exportProgress)
            } else {
                ExportForm(
                    state = state,
                    onDone = { keyboardController?.hide() },
                    onExport = { requestExport(state, context, keyguardLauncher, appScope) },
                )
            }
        }
    }

    if (state.showConfirmationDialog) {
        ConfirmExportDialog(
            accountCount = state.accountCount,
            onConfirm = {
                state.showConfirmationDialog = false
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                exportLauncher.launch("amber_backup_$timestamp.jsonl")
            },
            onDismiss = { state.showConfirmationDialog = false },
        )
    }
}

/** Once the password is fine, asks for the biometric or the device credential, then for confirmation. */
private fun requestExport(
    state: ExportAllAccountsState,
    context: Context,
    keyguardLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    appScope: CoroutineScope,
) {
    if (!state.checkPassword()) return
    authenticate(
        title = getStringBlocking(Res.string.export_all_accounts_title),
        context = context,
        keyguardLauncher = keyguardLauncher,
        onApproved = { state.showConfirmationDialog = true },
        onError = { _, message ->
            appScope.launch(Dispatchers.Main) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        },
    )
}

/** The export screen's state: the password and its confirmation, and how the export is going. */
@Stable
private class ExportAllAccountsState {
    var isLoading by mutableStateOf(false)
    var exportProgress by mutableStateOf("")
    var accountCount by mutableIntStateOf(0)
    var showConfirmationDialog by mutableStateOf(false)
    var password by mutableStateOf(TextFieldValue(""))
        private set
    var confirmation by mutableStateOf(TextFieldValue(""))
        private set
    var errorMessage by mutableStateOf("")
        private set

    fun onPasswordChange(value: TextFieldValue) {
        password = value
        if (errorMessage.isNotEmpty()) errorMessage = ""
    }

    fun onConfirmationChange(value: TextFieldValue) {
        confirmation = value
        if (errorMessage.isNotEmpty()) errorMessage = ""
    }

    /** Shows what is wrong with the password, if anything; true when it can be used. */
    fun checkPassword(): Boolean {
        val error = exportPasswordError(password.text, confirmation.text) ?: return true
        errorMessage = getStringBlocking(error)
        return false
    }

    /** Exports every account to [uri] on [scope], showing the progress, then a toast with the outcome. */
    fun export(
        scope: CoroutineScope,
        context: Context,
        uri: Uri,
    ) {
        scope.launch {
            isLoading = true
            exportProgress = getStringBlocking(Res.string.preparing_export)

            val message = exportAccounts(context, uri, password.text, accountCount) { current, total ->
                exportProgress = getStringBlocking(Res.string.exporting_accounts_progress, current, total)
            }

            launch(Dispatchers.Main) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                isLoading = false
                exportProgress = ""
            }
        }
    }
}

/** What is wrong with the export password: missing, shorter than 8 characters, or not confirmed; null when it can be used. */
internal fun exportPasswordError(
    password: String,
    confirmation: String,
): StringResource? = when {
    password.isBlank() -> Res.string.password_is_required
    password.length < 8 -> Res.string.password_too_short
    password != confirmation -> Res.string.passwords_do_not_match
    else -> null
}

/**
 * Writes every account, encrypted with [password], to [uri], and marks them backed up.
 * Returns the message to show: how many accounts were exported, or why the export failed.
 */
private suspend fun exportAccounts(
    context: Context,
    uri: Uri,
    password: String,
    accountCount: Int,
    onProgress: (current: Int, total: Int) -> Unit,
): String {
    val result = AccountExportService.exportAllAccountsEncrypted(
        context = context,
        password = password,
        onProgress = onProgress,
    )
    val encryptedData = result.getOrElse { e ->
        return getStringBlocking(Res.string.export_failed, e.message.toString())
    }
    return try {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(encryptedData.toByteArray())
        }
        SavedAccounts.all(context).forEach { accountInfo ->
            AccountStorage.load(context, accountInfo.npub)?.let { account ->
                account.didBackup = true
            }
        }
        getStringBlocking(Res.string.export_success, accountCount)
    } catch (e: Exception) {
        getStringBlocking(Res.string.export_failed, e.message.toString())
    }
}

@Composable
private fun ColumnScope.ExportProgress(progress: String) {
    CenterCircularProgressIndicator(Modifier)
    Text(
        text = progress,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .padding(top = 16.dp)
            .align(Alignment.CenterHorizontally),
    )
}

/** The notice, the password form and the Export button, pushed to the bottom of the screen. */
@Composable
private fun ColumnScope.ExportForm(
    state: ExportAllAccountsState,
    onDone: () -> Unit,
    onExport: () -> Unit,
) {
    ExportWarning(state.accountCount)

    ExportPasswordForm(
        password = state.password,
        onPasswordChange = state::onPasswordChange,
        confirmation = state.confirmation,
        onConfirmationChange = state::onConfirmationChange,
        errorMessage = state.errorMessage,
        onDone = onDone,
    )

    Spacer(modifier = Modifier.weight(1f))

    AmberButton(
        modifier = Modifier.fillMaxWidth(),
        text = stringResource(Res.string.export_all_accounts_button),
        onClick = onExport,
    )
}

/** The security notice, and how many accounts the file will hold. */
@Composable
private fun ExportWarning(accountCount: Int) {
    Column {
        MarkdownText(
            markdown = stringResource(Res.string.export_all_accounts_warning),
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Text(
            text = stringResource(Res.string.accounts_to_export, accountCount),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}

/** The export password, its confirmation, and what is wrong with them. */
@Composable
private fun ExportPasswordForm(
    password: TextFieldValue,
    onPasswordChange: (TextFieldValue) -> Unit,
    confirmation: TextFieldValue,
    onConfirmationChange: (TextFieldValue) -> Unit,
    errorMessage: String,
    onDone: () -> Unit,
) {
    Column {
        ExportPasswordField(
            value = password,
            onValueChange = onPasswordChange,
            label = Res.string.encryption_password,
            placeholder = Res.string.enter_strong_password,
            imeAction = ImeAction.Next,
        )

        Spacer(modifier = Modifier.height(16.dp))

        ExportPasswordField(
            value = confirmation,
            onValueChange = onConfirmationChange,
            label = Res.string.confirm_password,
            placeholder = null,
            imeAction = ImeAction.Done,
            onDone = onDone,
        )

        if (errorMessage.isNotBlank()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** A password field whose characters can be shown, with its own show or hide toggle. */
@Composable
private fun ExportPasswordField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: StringResource,
    placeholder: StringResource?,
    imeAction: ImeAction,
    onDone: () -> Unit = {},
) {
    var showChars by remember { mutableStateOf(false) }
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentType = ContentType.Password
            },
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        label = {
            Text(text = stringResource(label))
        },
        placeholder = placeholder?.let { { Text(text = stringResource(it)) } },
        trailingIcon = {
            IconButton(onClick = { showChars = !showChars }) {
                Icon(
                    imageVector = if (showChars) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (showChars) {
                        stringResource(Res.string.hide_password)
                    } else {
                        stringResource(Res.string.show_password)
                    },
                )
            }
        },
        visualTransformation = if (showChars) VisualTransformation.None else PasswordVisualTransformation(),
    )
}

@Composable
private fun ConfirmExportDialog(
    accountCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.outlineIfAny(amoledBorder(), AlertDialogDefaults.shape),
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(Res.string.confirm_export_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.confirm_export_message, accountCount),
                )
                Text(
                    text = stringResource(Res.string.confirm_export_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(Res.string.export))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.cancel))
            }
        },
    )
}
