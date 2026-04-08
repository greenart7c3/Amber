package com.greenart7c3.nostrsigner.ui.actions

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.greenart7c3.nostrsigner.service.WebDavService
import com.greenart7c3.nostrsigner.ui.CenterCircularProgressIndicator
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupScreen(modifier: Modifier) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var isLoading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    // Encryption password shared across both providers
    var password by remember { mutableStateOf(TextFieldValue("")) }
    var showPassword by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf("") }

    // WebDAV state
    var webDavUrl by remember { mutableStateOf(TextFieldValue("")) }
    var webDavUsername by remember { mutableStateOf(TextFieldValue("")) }
    var webDavPassword by remember { mutableStateOf(TextFieldValue("")) }
    var showWebDavPassword by remember { mutableStateOf(false) }
    var webDavError by remember { mutableStateOf("") }
    var webDavRestoreNpub by remember { mutableStateOf(TextFieldValue("")) }

    // Google Drive sheet state
    var showGdriveSheet by remember { mutableStateOf(false) }

    // Queue for sequential CreateDocument launches (one per account)
    var pendingExports by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var pendingIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            webDavUrl = TextFieldValue(LocalPreferences.getWebDavUrl(context))
            webDavUsername = TextFieldValue(LocalPreferences.getWebDavUsername(context))
            webDavPassword = TextFieldValue(LocalPreferences.getWebDavPassword(context))
        }
    }

    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    // CreateDocument launcher: writes the current pendingExports[pendingIndex] ncryptsec
    // directly to the URI chosen by the user (no local file created).
    val createDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val exports = pendingExports
        val idx = pendingIndex
        if (uri != null && idx < exports.size) {
            val (_, ncryptsec) = exports[idx]
            Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(ncryptsec.toByteArray(Charsets.UTF_8))
                }
            }
        }
        val next = idx + 1
        if (next < exports.size) {
            pendingIndex = next
            createDocLauncher.launch("${exports[next].first}.ncryptsec")
        } else {
            // All done
            pendingExports = emptyList()
            pendingIndex = 0
            Toast.makeText(
                context,
                context.getString(R.string.cloud_backup_success, "${exports.size} accounts"),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // Restore: pick any .ncryptsec file and import it
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { fileUri ->
            if (password.text.isBlank()) {
                passwordError = context.getString(R.string.cloud_backup_password_required)
                return@let
            }
            Amber.instance.applicationIOScope.launch {
                isLoading = true
                statusText = context.getString(R.string.cloud_backup_restoring)
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(fileUri)?.bufferedReader()?.readText() ?: ""
                }
                AccountExportService.importFromNcryptsec(
                    content = content,
                    password = password.text,
                    onLoading = { loading -> isLoading = loading },
                    onText = { t -> statusText = t },
                    onFinish = {
                        Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.cloud_backup_restore_success), Toast.LENGTH_LONG).show()
                            statusText = ""
                            isLoading = false
                        }
                    },
                )
            }
        }
    }

    if (isLoading) {
        CenterCircularProgressIndicator(modifier, statusText)
        return
    }

    Column(modifier = modifier.padding(16.dp)) {
        // --- Encryption password (shared) ---
        Text(
            text = stringResource(R.string.cloud_backup_encryption_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.cloud_backup_encryption_description),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Password },
            value = password,
            onValueChange = {
                password = it
                passwordError = ""
            },
            label = { Text(text = stringResource(R.string.encryption_password)) },
            placeholder = { Text(text = stringResource(R.string.enter_strong_password)) },
            isError = passwordError.isNotBlank(),
            supportingText = if (passwordError.isNotBlank()) {
                { Text(text = passwordError, color = MaterialTheme.colorScheme.error) }
            } else {
                null
            },
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showPassword) {
                            stringResource(R.string.hide_password)
                        } else {
                            stringResource(R.string.show_password)
                        },
                    )
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // --- Google Drive row ---
        Text(
            text = stringResource(R.string.cloud_backup_gdrive_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.cloud_backup_gdrive_description),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
        )
        Spacer(modifier = Modifier.height(8.dp))
        AmberButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.cloud_backup_gdrive_open),
            onClick = {
                if (password.text.isBlank()) {
                    passwordError = context.getString(R.string.cloud_backup_password_required)
                } else {
                    showGdriveSheet = true
                }
            },
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // --- WebDAV section ---
        Text(
            text = stringResource(R.string.cloud_backup_webdav_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.cloud_backup_webdav_description),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = webDavUrl,
            onValueChange = {
                webDavUrl = it
                webDavError = ""
            },
            label = { Text(text = stringResource(R.string.cloud_backup_webdav_url)) },
            placeholder = { Text(text = "https://dav.example.com/remote.php/dav/files/user/") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = webDavUsername,
            onValueChange = {
                webDavUsername = it
                webDavError = ""
            },
            label = { Text(text = stringResource(R.string.cloud_backup_webdav_username)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Password },
            value = webDavPassword,
            onValueChange = {
                webDavPassword = it
                webDavError = ""
            },
            label = { Text(text = stringResource(R.string.cloud_backup_webdav_password)) },
            trailingIcon = {
                IconButton(onClick = { showWebDavPassword = !showWebDavPassword }) {
                    Icon(
                        imageVector = if (showWebDavPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showWebDavPassword) {
                            stringResource(R.string.hide_password)
                        } else {
                            stringResource(R.string.show_password)
                        },
                    )
                }
            },
            visualTransformation = if (showWebDavPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
            singleLine = true,
        )

        if (webDavError.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = webDavError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AmberButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.cloud_backup_webdav_save),
                onClick = {
                    Amber.instance.applicationIOScope.launch {
                        LocalPreferences.saveWebDavSettings(
                            context = context,
                            url = webDavUrl.text,
                            username = webDavUsername.text,
                            password = webDavPassword.text,
                            fileName = "",
                        )
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.cloud_backup_webdav_saved), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )

            AmberButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.cloud_backup_webdav_test),
                onClick = {
                    if (webDavUrl.text.isBlank()) {
                        webDavError = context.getString(R.string.cloud_backup_webdav_url_required)
                    } else {
                        Amber.instance.applicationIOScope.launch {
                            isLoading = true
                            statusText = context.getString(R.string.cloud_backup_testing_connection)
                            val result = withContext(Dispatchers.IO) {
                                WebDavService.testConnection(
                                    serverUrl = webDavUrl.text,
                                    username = webDavUsername.text,
                                    password = webDavPassword.text,
                                )
                            }
                            withContext(Dispatchers.Main) {
                                if (result.isSuccess) {
                                    Toast.makeText(context, context.getString(R.string.cloud_backup_webdav_connected), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.cloud_backup_failed, result.exceptionOrNull()?.message), Toast.LENGTH_LONG).show()
                                }
                                isLoading = false
                                statusText = ""
                            }
                        }
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        AmberButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.cloud_backup_webdav_backup),
            onClick = {
                when {
                    password.text.isBlank() -> passwordError = context.getString(R.string.cloud_backup_password_required)
                    password.text.length < 8 -> passwordError = context.getString(R.string.password_too_short)
                    webDavUrl.text.isBlank() -> webDavError = context.getString(R.string.cloud_backup_webdav_url_required)
                    else -> {
                        authenticate(
                            title = context.getString(R.string.cloud_backup_webdav_backup),
                            context = context,
                            keyguardLauncher = keyguardLauncher,
                            onApproved = {
                                Amber.instance.applicationIOScope.launch {
                                    isLoading = true
                                    statusText = context.getString(R.string.preparing_export)

                                    LocalPreferences.saveWebDavSettings(
                                        context = context,
                                        url = webDavUrl.text,
                                        username = webDavUsername.text,
                                        password = webDavPassword.text,
                                        fileName = "",
                                    )

                                    val exportResult = AccountExportService.exportPerAccountNcryptsec(
                                        context = context,
                                        password = password.text,
                                        onProgress = { cur, tot ->
                                            statusText = context.getString(R.string.exporting_accounts_progress, cur, tot)
                                        },
                                    )

                                    exportResult.onSuccess { pairs ->
                                        var allOk = true
                                        pairs.forEachIndexed { index, (npub, ncryptsec) ->
                                            statusText = context.getString(R.string.cloud_backup_uploading) + " (${index + 1}/${pairs.size})"
                                            val uploadResult = withContext(Dispatchers.IO) {
                                                WebDavService.uploadFile(
                                                    serverUrl = webDavUrl.text,
                                                    username = webDavUsername.text,
                                                    password = webDavPassword.text,
                                                    fileName = "$npub.ncryptsec",
                                                    content = ncryptsec,
                                                )
                                            }
                                            if (uploadResult.isFailure) allOk = false
                                        }
                                        withContext(Dispatchers.Main) {
                                            if (allOk) {
                                                Toast.makeText(context, context.getString(R.string.cloud_backup_success, "${pairs.size} accounts"), Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, context.getString(R.string.cloud_backup_partial_failure), Toast.LENGTH_LONG).show()
                                            }
                                            isLoading = false
                                            statusText = ""
                                        }
                                    }.onFailure { e ->
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, context.getString(R.string.cloud_backup_failed, e.message), Toast.LENGTH_LONG).show()
                                            isLoading = false
                                            statusText = ""
                                        }
                                    }
                                }
                            },
                            onError = { _, msg ->
                                Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                }
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = webDavRestoreNpub,
            onValueChange = { webDavRestoreNpub = it },
            label = { Text(text = stringResource(R.string.cloud_backup_webdav_restore_npub)) },
            placeholder = { Text(text = "npub1...") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(8.dp))

        AmberButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.cloud_backup_webdav_restore),
            onClick = {
                when {
                    password.text.isBlank() -> passwordError = context.getString(R.string.cloud_backup_password_required)
                    webDavUrl.text.isBlank() -> webDavError = context.getString(R.string.cloud_backup_webdav_url_required)
                    webDavRestoreNpub.text.isBlank() -> webDavError = context.getString(R.string.cloud_backup_webdav_npub_required)
                    else -> {
                        Amber.instance.applicationIOScope.launch {
                            isLoading = true
                            statusText = context.getString(R.string.cloud_backup_downloading)

                            val downloadResult = withContext(Dispatchers.IO) {
                                WebDavService.downloadLatestFile(
                                    serverUrl = webDavUrl.text,
                                    username = webDavUsername.text,
                                    password = webDavPassword.text,
                                    fileName = "${webDavRestoreNpub.text.trim()}.ncryptsec",
                                )
                            }

                            downloadResult.onSuccess { data ->
                                AccountExportService.importFromNcryptsec(
                                    content = data,
                                    password = password.text,
                                    onLoading = { loading -> isLoading = loading },
                                    onText = { t -> statusText = t },
                                    onFinish = {
                                        Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                                            Toast.makeText(context, context.getString(R.string.cloud_backup_restore_success), Toast.LENGTH_LONG).show()
                                            statusText = ""
                                            isLoading = false
                                        }
                                    },
                                )
                            }.onFailure { e ->
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, context.getString(R.string.cloud_backup_failed, e.message), Toast.LENGTH_LONG).show()
                                    isLoading = false
                                    statusText = ""
                                }
                            }
                        }
                    }
                }
            },
        )
    }

    // Google Drive bottom sheet: sends each {npub}.ncryptsec directly via CreateDocument
    // (Android system picker — user can choose Google Drive, no local save needed)
    if (showGdriveSheet) {
        ModalBottomSheet(
            onDismissRequest = { showGdriveSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.cloud_backup_gdrive_section),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.cloud_backup_gdrive_sheet_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )

                HorizontalDivider()

                // Backup: export all accounts and launch CreateDocument for each, one at a time
                AmberButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.cloud_backup_gdrive_backup),
                    onClick = {
                        authenticate(
                            title = context.getString(R.string.cloud_backup_gdrive_backup),
                            context = context,
                            keyguardLauncher = keyguardLauncher,
                            onApproved = {
                                showGdriveSheet = false
                                Amber.instance.applicationIOScope.launch {
                                    isLoading = true
                                    statusText = context.getString(R.string.preparing_export)
                                    val result = AccountExportService.exportPerAccountNcryptsec(
                                        context = context,
                                        password = password.text,
                                        onProgress = { cur, tot ->
                                            statusText = context.getString(R.string.exporting_accounts_progress, cur, tot)
                                        },
                                    )
                                    result.onSuccess { pairs ->
                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                            statusText = ""
                                            if (pairs.isNotEmpty()) {
                                                pendingExports = pairs
                                                pendingIndex = 0
                                                // Launch the file-save picker for the first account;
                                                // the launcher callback chains the rest automatically.
                                                createDocLauncher.launch("${pairs[0].first}.ncryptsec")
                                            }
                                        }
                                    }.onFailure { e ->
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, context.getString(R.string.cloud_backup_failed, e.message), Toast.LENGTH_LONG).show()
                                            isLoading = false
                                            statusText = ""
                                        }
                                    }
                                }
                            },
                            onError = { _, msg ->
                                Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    },
                )

                // Restore: user picks a .ncryptsec file from any provider (Google Drive, etc.)
                AmberButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.cloud_backup_gdrive_restore),
                    onClick = {
                        showGdriveSheet = false
                        filePickerLauncher.launch("*/*")
                    },
                )
            }
        }
    }
}
