package com.greenart7c3.nostrsigner.ui.actions

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEFAULT_WEBDAV_FILENAME = "amber_backup.txt"

@OptIn(ExperimentalComposeUiApi::class)
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
    var webDavFilename by remember { mutableStateOf(TextFieldValue(DEFAULT_WEBDAV_FILENAME)) }
    var webDavError by remember { mutableStateOf("") }

    // Google Drive state
    var gdriveFolderUri by remember { mutableStateOf<Uri?>(null) }
    var gdriveFolderName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            webDavUrl = TextFieldValue(LocalPreferences.getWebDavUrl(context))
            webDavUsername = TextFieldValue(LocalPreferences.getWebDavUsername(context))
            webDavPassword = TextFieldValue(LocalPreferences.getWebDavPassword(context))
            webDavFilename = TextFieldValue(LocalPreferences.getWebDavFilename(context))

            val savedUri = LocalPreferences.getGdriveFolderUri(context)
            if (savedUri.isNotBlank()) {
                val uri = Uri.parse(savedUri)
                gdriveFolderUri = uri
                gdriveFolderName = uri.lastPathSegment ?: context.getString(R.string.cloud_backup_folder_selected)
            }
        }
    }

    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    // Google Drive: pick folder
    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: SecurityException) { }
            gdriveFolderUri = it
            gdriveFolderName = it.lastPathSegment ?: context.getString(R.string.cloud_backup_folder_selected)
            LocalPreferences.saveGdriveFolderUri(context, it.toString())
        }
    }

    // Google Drive: pick a backup file for restore (reads ncryptsec lines)
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

    Surface(modifier = modifier.fillMaxSize()) {
        if (isLoading) {
            CenterCircularProgressIndicator(Modifier, statusText)
            return@Surface
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
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

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // --- Google Drive section ---
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

            if (gdriveFolderName.isNotBlank()) {
                Text(
                    text = stringResource(R.string.cloud_backup_folder_label, gdriveFolderName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            AmberButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(
                    if (gdriveFolderUri == null) {
                        R.string.cloud_backup_gdrive_pick_folder
                    } else {
                        R.string.cloud_backup_gdrive_change_folder
                    },
                ),
                onClick = { folderPickerLauncher.launch(null) },
            )

            if (gdriveFolderUri != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AmberButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.cloud_backup_gdrive_backup),
                        onClick = {
                            when {
                                password.text.isBlank() -> passwordError = context.getString(R.string.cloud_backup_password_required)
                                password.text.length < 8 -> passwordError = context.getString(R.string.password_too_short)
                                else -> {
                                    authenticate(
                                        title = context.getString(R.string.cloud_backup_gdrive_backup),
                                        context = context,
                                        keyguardLauncher = keyguardLauncher,
                                        onApproved = {
                                            Amber.instance.applicationIOScope.launch {
                                                isLoading = true
                                                statusText = context.getString(R.string.preparing_export)
                                                val result = AccountExportService.exportAllAsNcryptsec(
                                                    context = context,
                                                    password = password.text,
                                                    onProgress = { cur, tot ->
                                                        statusText = context.getString(R.string.exporting_accounts_progress, cur, tot)
                                                    },
                                                )
                                                result.onSuccess { data ->
                                                    try {
                                                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                                        val fileName = "amber_backup_$timestamp.txt"
                                                        val folderUri = gdriveFolderUri!!
                                                        val docUri = DocumentsContract.createDocument(
                                                            context.contentResolver,
                                                            DocumentsContract.buildDocumentUriUsingTree(
                                                                folderUri,
                                                                DocumentsContract.getTreeDocumentId(folderUri),
                                                            ),
                                                            "text/plain",
                                                            fileName,
                                                        )
                                                        docUri?.let { fileUri ->
                                                            context.contentResolver.openOutputStream(fileUri)?.use { stream ->
                                                                stream.write(data.toByteArray(Charsets.UTF_8))
                                                            }
                                                        }
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, context.getString(R.string.cloud_backup_success, fileName), Toast.LENGTH_LONG).show()
                                                            isLoading = false
                                                            statusText = ""
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, context.getString(R.string.cloud_backup_failed, e.message), Toast.LENGTH_LONG).show()
                                                            isLoading = false
                                                            statusText = ""
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
                                }
                            }
                        },
                    )

                    AmberButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.cloud_backup_gdrive_restore),
                        onClick = {
                            if (password.text.isBlank()) {
                                passwordError = context.getString(R.string.cloud_backup_password_required)
                            } else {
                                filePickerLauncher.launch("*/*")
                            }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

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

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
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
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                singleLine = true,
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = webDavFilename,
                onValueChange = {
                    webDavFilename = it
                    webDavError = ""
                },
                label = { Text(text = stringResource(R.string.cloud_backup_webdav_filename)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                singleLine = true,
            )

            if (webDavError.isNotBlank()) {
                Text(text = webDavError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AmberButton(
                    modifier = Modifier,
                    text = stringResource(R.string.cloud_backup_webdav_save),
                    onClick = {
                        Amber.instance.applicationIOScope.launch {
                            LocalPreferences.saveWebDavSettings(
                                context = context,
                                url = webDavUrl.text,
                                username = webDavUsername.text,
                                password = webDavPassword.text,
                                fileName = webDavFilename.text.ifBlank { DEFAULT_WEBDAV_FILENAME },
                            )
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, context.getString(R.string.cloud_backup_webdav_saved), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AmberButton(
                    modifier = Modifier.weight(1f),
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
                                                fileName = webDavFilename.text.ifBlank { DEFAULT_WEBDAV_FILENAME },
                                            )

                                            val exportResult = AccountExportService.exportAllAsNcryptsec(
                                                context = context,
                                                password = password.text,
                                                onProgress = { cur, tot ->
                                                    statusText = context.getString(R.string.exporting_accounts_progress, cur, tot)
                                                },
                                            )

                                            exportResult.onSuccess { data ->
                                                statusText = context.getString(R.string.cloud_backup_uploading)
                                                val uploadResult = withContext(Dispatchers.IO) {
                                                    WebDavService.uploadFile(
                                                        serverUrl = webDavUrl.text,
                                                        username = webDavUsername.text,
                                                        password = webDavPassword.text,
                                                        fileName = webDavFilename.text.ifBlank { DEFAULT_WEBDAV_FILENAME },
                                                        content = data,
                                                    )
                                                }
                                                withContext(Dispatchers.Main) {
                                                    if (uploadResult.isSuccess) {
                                                        Toast.makeText(context, context.getString(R.string.cloud_backup_success, webDavFilename.text), Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(context, context.getString(R.string.cloud_backup_failed, uploadResult.exceptionOrNull()?.message), Toast.LENGTH_LONG).show()
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

                AmberButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.cloud_backup_webdav_restore),
                    onClick = {
                        when {
                            password.text.isBlank() -> passwordError = context.getString(R.string.cloud_backup_password_required)
                            webDavUrl.text.isBlank() -> webDavError = context.getString(R.string.cloud_backup_webdav_url_required)
                            else -> {
                                Amber.instance.applicationIOScope.launch {
                                    isLoading = true
                                    statusText = context.getString(R.string.cloud_backup_downloading)

                                    val downloadResult = withContext(Dispatchers.IO) {
                                        WebDavService.downloadLatestFile(
                                            serverUrl = webDavUrl.text,
                                            username = webDavUsername.text,
                                            password = webDavPassword.text,
                                            fileName = webDavFilename.text.ifBlank { DEFAULT_WEBDAV_FILENAME },
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

            AmberButton(
                modifier = Modifier.fillMaxWidth(),
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

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
