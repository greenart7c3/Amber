package com.greenart7c3.nostrsigner.ui.actions

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.service.AccountExportService
import com.greenart7c3.nostrsigner.service.Biometrics.authenticate
import com.greenart7c3.nostrsigner.ui.CenterCircularProgressIndicator
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.commonmark.MarkdownParseOptions
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.resolveDefaults
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ExportAllAccountsScreen(
    modifier: Modifier,
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf("") }
    var accountCount by remember { mutableIntStateOf(0) }
    var showConfirmationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            accountCount = LocalPreferences.allSavedAccounts(context).size
        }
    }

    val password = remember { mutableStateOf(TextFieldValue("")) }
    val passwordConfirm = remember { mutableStateOf(TextFieldValue("")) }
    var errorMessage by remember { mutableStateOf("") }
    var showCharsPassword by remember { mutableStateOf(false) }
    var showCharsPasswordConfirm by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // File save launcher
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        uri?.let {
            Amber.instance.applicationIOScope.launch {
                isLoading = true
                exportProgress = context.getString(R.string.preparing_export)

                val result = AccountExportService.exportAllAccountsEncrypted(
                    context = context,
                    password = password.value.text,
                    onProgress = { current, total ->
                        exportProgress = context.getString(
                            R.string.exporting_accounts_progress,
                            current,
                            total,
                        )
                    },
                )

                result.onSuccess { encryptedData ->
                    try {
                        context.contentResolver.openOutputStream(it)?.use { stream ->
                            stream.write(encryptedData.toByteArray())
                        }

                        // Mark all accounts as backed up
                        LocalPreferences.allSavedAccounts(context).forEach { accountInfo ->
                            LocalPreferences.loadFromEncryptedStorage(context, accountInfo.npub)?.let { account ->
                                account.didBackup = true
                                LocalPreferences.saveToEncryptedStorage(context, account)
                            }
                        }

                        launch(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.export_success, accountCount),
                                Toast.LENGTH_LONG,
                            ).show()
                            isLoading = false
                            exportProgress = ""
                        }
                    } catch (e: Exception) {
                        launch(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.export_failed, e.message),
                                Toast.LENGTH_LONG,
                            ).show()
                            isLoading = false
                            exportProgress = ""
                        }
                    }
                }.onFailure { e ->
                    launch(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.export_failed, e.message),
                            Toast.LENGTH_LONG,
                        ).show()
                        isLoading = false
                        exportProgress = ""
                    }
                }
            }
        }
    }

    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                showConfirmationDialog = true
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
            if (isLoading) {
                CenterCircularProgressIndicator(Modifier)
                Text(
                    text = exportProgress,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .align(Alignment.CenterHorizontally),
                )
            } else {
                // Security warning
                val warningContent = stringResource(R.string.export_all_accounts_warning)
                val astNode = remember {
                    CommonmarkAstNodeParser(MarkdownParseOptions.MarkdownWithLinks).parse(warningContent)
                }

                RichText(
                    style = RichTextStyle().resolveDefaults(),
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    BasicMarkdown(astNode)
                }

                Text(
                    text = stringResource(R.string.accounts_to_export, accountCount),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                // Password input
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentType = ContentType.Password
                        },
                    value = password.value,
                    onValueChange = {
                        password.value = it
                        if (errorMessage.isNotEmpty()) {
                            errorMessage = ""
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                    label = {
                        Text(text = stringResource(R.string.encryption_password))
                    },
                    placeholder = {
                        Text(text = stringResource(R.string.enter_strong_password))
                    },
                    trailingIcon = {
                        IconButton(onClick = { showCharsPassword = !showCharsPassword }) {
                            Icon(
                                imageVector = if (showCharsPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showCharsPassword) {
                                    stringResource(R.string.hide_password)
                                } else {
                                    stringResource(R.string.show_password)
                                },
                            )
                        }
                    },
                    visualTransformation = if (showCharsPassword) VisualTransformation.None else PasswordVisualTransformation(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password confirmation
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentType = ContentType.Password
                        },
                    value = passwordConfirm.value,
                    onValueChange = {
                        passwordConfirm.value = it
                        if (errorMessage.isNotEmpty()) {
                            errorMessage = ""
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                        },
                    ),
                    label = {
                        Text(text = stringResource(R.string.confirm_password))
                    },
                    trailingIcon = {
                        IconButton(onClick = { showCharsPasswordConfirm = !showCharsPasswordConfirm }) {
                            Icon(
                                imageVector = if (showCharsPasswordConfirm) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showCharsPasswordConfirm) {
                                    stringResource(R.string.hide_password)
                                } else {
                                    stringResource(R.string.show_password)
                                },
                            )
                        }
                    },
                    visualTransformation = if (showCharsPasswordConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                )

                if (errorMessage.isNotBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Export button
                AmberButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.export_all_accounts_button),
                    onClick = {
                        // Validate password
                        when {
                            password.value.text.isBlank() -> {
                                errorMessage = context.getString(R.string.password_is_required)
                            }
                            password.value.text.length < 8 -> {
                                errorMessage = context.getString(R.string.password_too_short)
                            }
                            password.value.text != passwordConfirm.value.text -> {
                                errorMessage = context.getString(R.string.passwords_do_not_match)
                            }
                            else -> {
                                // Require biometric authentication
                                authenticate(
                                    title = context.getString(R.string.export_all_accounts_title),
                                    context = context,
                                    keyguardLauncher = keyguardLauncher,
                                    onApproved = {
                                        showConfirmationDialog = true
                                    },
                                    onError = { _, message ->
                                        Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                message,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    // Confirmation dialog
    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = {
                Text(text = stringResource(R.string.confirm_export_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.confirm_export_message, accountCount),
                    )
                    Text(
                        text = stringResource(R.string.confirm_export_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmationDialog = false
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        exportLauncher.launch("amber_backup_$timestamp.jsonl")
                    },
                ) {
                    Text(text = stringResource(R.string.export))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmationDialog = false },
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }
}
