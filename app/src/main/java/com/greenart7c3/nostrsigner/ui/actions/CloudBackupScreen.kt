package com.greenart7c3.nostrsigner.ui.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
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
import androidx.compose.material3.BottomSheetDefaults
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.greenart7c3.nostrsigner.WebDavSettings
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_downloading
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_encryption_description
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_encryption_section
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_failed
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_gdrive_backup
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_gdrive_description
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_gdrive_open
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_gdrive_restore
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_gdrive_section
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_gdrive_sheet_description
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_partial_failure
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_password_required
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_restore_success
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_restoring
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_share_title
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_success
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_testing_connection
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_uploading
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_webdav_backup
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_webdav_connected
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_webdav_description
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_webdav_npub_required
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_webdav_password
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_webdav_restore
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_webdav_restore_npub
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_webdav_save
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_webdav_saved
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_webdav_section
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_webdav_test
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_webdav_url
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_webdav_url_required
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_webdav_username
import com.greenart7c3.nostrsigner.commonsui.encryption_password
import com.greenart7c3.nostrsigner.commonsui.enter_strong_password
import com.greenart7c3.nostrsigner.commonsui.exporting_accounts_progress
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.hide_password
import com.greenart7c3.nostrsigner.commonsui.password_too_short
import com.greenart7c3.nostrsigner.commonsui.preparing_export
import com.greenart7c3.nostrsigner.commonsui.show_password
import com.greenart7c3.nostrsigner.service.AccountExportService
import com.greenart7c3.nostrsigner.service.Biometrics.authenticate
import com.greenart7c3.nostrsigner.service.SharedFiles
import com.greenart7c3.nostrsigner.service.WebDavService
import com.greenart7c3.nostrsigner.service.WebDavTransportPolicy
import com.greenart7c3.nostrsigner.ui.CenterCircularProgressIndicator
import com.greenart7c3.nostrsigner.ui.LocalAppScope
import com.greenart7c3.nostrsigner.ui.LocalRelayClient
import com.greenart7c3.nostrsigner.ui.LocalSettingsStore
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.theme.amoledBorder
import com.greenart7c3.nostrsigner.ui.theme.outlineIfAny
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun CloudBackupScreen(modifier: Modifier = Modifier) {
    val webDavService = WebDavService(LocalSettingsStore.current, LocalRelayClient.current)
    val appScope = LocalAppScope.current
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val state = remember { CloudBackupState() }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            state.loadWebDavSettings(context)
        }
    }

    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val actions = CloudBackupActions(state, context, appScope, webDavService, keyguardLauncher)

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { fileUri ->
            if (state.requirePassword()) actions.restoreFromFile(fileUri)
        }
    }

    if (state.isLoading) {
        CenterCircularProgressIndicator(modifier, state.statusText)
        return
    }

    Column(modifier = modifier.padding(16.dp)) {
        EncryptionPasswordSection(state = state, onDone = { keyboardController?.hide() })

        SectionDivider()

        BackupSection(Res.string.cloud_backup_gdrive_section, Res.string.cloud_backup_gdrive_description)
        AmberButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(Res.string.cloud_backup_gdrive_open),
            onClick = {
                if (state.requirePassword()) state.showGdriveSheet = true
            },
        )

        SectionDivider()

        WebDavSection(state = state, actions = actions, onDone = { keyboardController?.hide() })
    }

    if (state.showGdriveSheet) {
        GoogleDriveSheet(
            onBackup = actions::backupToGoogleDrive,
            onRestore = {
                state.showGdriveSheet = false
                filePickerLauncher.launch("*/*")
            },
            onDismiss = { state.showGdriveSheet = false },
        )
    }
}

@Stable
private class CloudBackupState {
    var isLoading by mutableStateOf(false)
    var statusText by mutableStateOf("")

    var password by mutableStateOf(TextFieldValue(""))
    var passwordError by mutableStateOf("")

    var webDavUrl by mutableStateOf(TextFieldValue(""))
    var webDavUsername by mutableStateOf(TextFieldValue(""))
    var webDavPassword by mutableStateOf(TextFieldValue(""))
    var webDavError by mutableStateOf("")
    var webDavRestoreNpub by mutableStateOf(TextFieldValue(""))

    var showGdriveSheet by mutableStateOf(false)

    suspend fun loadWebDavSettings(context: Context) {
        webDavUrl = TextFieldValue(WebDavSettings.url(context))
        webDavUsername = TextFieldValue(WebDavSettings.username(context))
        webDavPassword = TextFieldValue(WebDavSettings.password(context))
    }

    fun requirePassword(): Boolean {
        if (password.text.isNotBlank()) return true
        passwordError = getStringBlocking(Res.string.cloud_backup_password_required)
        return false
    }

    fun doneLoading() {
        isLoading = false
        statusText = ""
    }
}

private class CloudBackupActions(
    private val state: CloudBackupState,
    private val context: Context,
    private val appScope: CoroutineScope,
    private val webDavService: WebDavService,
    private val keyguardLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
) {
    fun saveWebDavSettings() {
        appScope.launch {
            try {
                WebDavTransportPolicy.requireSecureServerUrl(state.webDavUrl.text)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    state.webDavError = e.message ?: getStringBlocking(Res.string.cloud_backup_webdav_url_required)
                }
                return@launch
            }
            saveWebDav()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, getStringBlocking(Res.string.cloud_backup_webdav_saved), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun testWebDav() {
        if (state.webDavUrl.text.isBlank()) {
            state.webDavError = getStringBlocking(Res.string.cloud_backup_webdav_url_required)
            return
        }
        appScope.launch {
            state.isLoading = true
            state.statusText = getStringBlocking(Res.string.cloud_backup_testing_connection)
            val result = withContext(Dispatchers.IO) {
                webDavService.testConnection(
                    serverUrl = state.webDavUrl.text,
                    username = state.webDavUsername.text,
                    password = state.webDavPassword.text,
                )
            }
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    Toast.makeText(context, getStringBlocking(Res.string.cloud_backup_webdav_connected), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, getStringBlocking(Res.string.cloud_backup_failed, result.exceptionOrNull()?.message.toString()), Toast.LENGTH_LONG).show()
                }
                state.doneLoading()
            }
        }
    }

    fun backupToWebDav() {
        when {
            state.password.text.isBlank() -> state.passwordError = getStringBlocking(Res.string.cloud_backup_password_required)
            state.password.text.length < 8 -> state.passwordError = getStringBlocking(Res.string.password_too_short)
            state.webDavUrl.text.isBlank() -> state.webDavError = getStringBlocking(Res.string.cloud_backup_webdav_url_required)
            else -> authenticated(Res.string.cloud_backup_webdav_backup) { appScope.launch { uploadToWebDav() } }
        }
    }

    private suspend fun uploadToWebDav() {
        state.isLoading = true
        state.statusText = getStringBlocking(Res.string.preparing_export)

        try {
            WebDavTransportPolicy.requireSecureServerUrl(state.webDavUrl.text)
        } catch (e: Exception) {
            state.isLoading = false
            state.statusText = e.message ?: getStringBlocking(Res.string.cloud_backup_webdav_url_required)
            return
        }

        saveWebDav()

        exportAccounts().onSuccess { pairs ->
            val allOk = uploadAll(pairs)
            withContext(Dispatchers.Main) {
                if (allOk) {
                    Toast.makeText(context, getStringBlocking(Res.string.cloud_backup_success, "${pairs.size} accounts"), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, getStringBlocking(Res.string.cloud_backup_partial_failure), Toast.LENGTH_LONG).show()
                }
                state.doneLoading()
            }
        }.onFailure { e -> failed(e) }
    }

    private suspend fun uploadAll(pairs: List<Pair<String, String>>): Boolean {
        var allOk = true
        pairs.forEachIndexed { index, (npub, ncryptsec) ->
            state.statusText = getStringBlocking(Res.string.cloud_backup_uploading) + " (${index + 1}/${pairs.size})"
            val uploadResult = withContext(Dispatchers.IO) {
                webDavService.uploadFile(
                    serverUrl = state.webDavUrl.text,
                    username = state.webDavUsername.text,
                    password = state.webDavPassword.text,
                    fileName = "$npub.ncryptsec",
                    content = ncryptsec,
                )
            }
            if (uploadResult.isFailure) allOk = false
        }
        return allOk
    }

    fun restoreFromWebDav() {
        when {
            state.password.text.isBlank() -> state.passwordError = getStringBlocking(Res.string.cloud_backup_password_required)
            state.webDavUrl.text.isBlank() -> state.webDavError = getStringBlocking(Res.string.cloud_backup_webdav_url_required)
            state.webDavRestoreNpub.text.isBlank() -> state.webDavError = getStringBlocking(Res.string.cloud_backup_webdav_npub_required)
            else -> appScope.launch { downloadFromWebDav() }
        }
    }

    private suspend fun downloadFromWebDav() {
        state.isLoading = true
        state.statusText = getStringBlocking(Res.string.cloud_backup_downloading)

        val downloadResult = withContext(Dispatchers.IO) {
            webDavService.downloadLatestFile(
                serverUrl = state.webDavUrl.text,
                username = state.webDavUsername.text,
                password = state.webDavPassword.text,
                fileName = "${state.webDavRestoreNpub.text.trim()}.ncryptsec",
            )
        }

        downloadResult.onSuccess { data -> import(data) }.onFailure { e -> failed(e) }
    }

    fun restoreFromFile(fileUri: Uri) {
        appScope.launch {
            state.isLoading = true
            state.statusText = getStringBlocking(Res.string.cloud_backup_restoring)
            val content = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(fileUri)?.bufferedReader()?.readText() ?: ""
            }
            import(content)
        }
    }

    fun backupToGoogleDrive() {
        authenticated(Res.string.cloud_backup_gdrive_backup) {
            state.showGdriveSheet = false
            appScope.launch {
                state.isLoading = true
                state.statusText = getStringBlocking(Res.string.preparing_export)
                exportAccounts().onSuccess { pairs ->
                    withContext(Dispatchers.Main) {
                        state.doneLoading()
                        if (pairs.isNotEmpty()) {
                            shareNcryptsecFiles(pairs)
                        }
                    }
                }.onFailure { e -> failed(e) }
            }
        }
    }

    private fun shareNcryptsecFiles(pairs: List<Pair<String, String>>) {
        val dir = SharedFiles.freshShareDir(context)
        val uris = pairs.map { (npub, ncryptsec) ->
            val file = File(dir, "$npub.ncryptsec")
            file.writeText(ncryptsec)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val shareIntent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uris[0])
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "text/plain"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(shareIntent, getStringBlocking(Res.string.cloud_backup_share_title)))
    }

    private suspend fun exportAccounts() = AccountExportService.exportPerAccountNcryptsec(
        context = context,
        password = state.password.text,
        onProgress = { cur, tot ->
            state.statusText = getStringBlocking(Res.string.exporting_accounts_progress, cur, tot)
        },
    )

    private suspend fun import(content: String) {
        AccountExportService.importFromNcryptsec(
            context = context.applicationContext,
            scope = appScope,
            content = content,
            password = state.password.text,
            onLoading = { loading -> state.isLoading = loading },
            onText = { t -> state.statusText = t },
            onFinish = {
                appScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, getStringBlocking(Res.string.cloud_backup_restore_success), Toast.LENGTH_LONG).show()
                    state.doneLoading()
                }
            },
        )
    }

    private suspend fun saveWebDav() {
        WebDavSettings.save(
            context = context,
            url = state.webDavUrl.text,
            username = state.webDavUsername.text,
            password = state.webDavPassword.text,
            fileName = "",
        )
    }

    private suspend fun failed(e: Throwable) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, getStringBlocking(Res.string.cloud_backup_failed, e.message.toString()), Toast.LENGTH_LONG).show()
            state.doneLoading()
        }
    }

    private fun authenticated(
        title: StringResource,
        onApproved: () -> Unit,
    ) {
        authenticate(
            title = getStringBlocking(title),
            context = context,
            keyguardLauncher = keyguardLauncher,
            onApproved = onApproved,
            onError = { _, msg ->
                appScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@Composable
private fun SectionDivider() {
    Column {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun BackupSection(
    title: StringResource,
    description: StringResource,
) {
    Column {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(description),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun EncryptionPasswordSection(
    state: CloudBackupState,
    onDone: () -> Unit,
) {
    Column {
        BackupSection(Res.string.cloud_backup_encryption_section, Res.string.cloud_backup_encryption_description)
        PasswordField(
            value = state.password,
            onValueChange = {
                state.password = it
                state.passwordError = ""
            },
            label = Res.string.encryption_password,
            placeholder = Res.string.enter_strong_password,
            error = state.passwordError,
            onDone = onDone,
        )
    }
}

@Composable
private fun PasswordField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: StringResource,
    placeholder: StringResource?,
    error: String,
    onDone: () -> Unit,
) {
    var showPassword by remember { mutableStateOf(false) }
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentType = ContentType.Password },
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = stringResource(label)) },
        placeholder = placeholder?.let { { Text(text = stringResource(it)) } },
        isError = error.isNotBlank(),
        supportingText = if (error.isNotBlank()) {
            { Text(text = error, color = MaterialTheme.colorScheme.error) }
        } else {
            null
        },
        trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
                Icon(
                    imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (showPassword) {
                        stringResource(Res.string.hide_password)
                    } else {
                        stringResource(Res.string.show_password)
                    },
                )
            }
        },
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
    )
}

@Composable
private fun WebDavSection(
    state: CloudBackupState,
    actions: CloudBackupActions,
    onDone: () -> Unit,
) {
    Column {
        BackupSection(Res.string.cloud_backup_webdav_section, Res.string.cloud_backup_webdav_description)

        WebDavServerFields(state = state, onDone = onDone)

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AmberButton(
                modifier = Modifier.weight(1f),
                text = stringResource(Res.string.cloud_backup_webdav_save),
                onClick = actions::saveWebDavSettings,
            )

            AmberButton(
                modifier = Modifier.weight(1f),
                text = stringResource(Res.string.cloud_backup_webdav_test),
                onClick = actions::testWebDav,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        AmberButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(Res.string.cloud_backup_webdav_backup),
            onClick = actions::backupToWebDav,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.webDavRestoreNpub,
            onValueChange = { state.webDavRestoreNpub = it },
            label = { Text(text = stringResource(Res.string.cloud_backup_webdav_restore_npub)) },
            placeholder = { Text(text = "npub1...") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(8.dp))

        AmberButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(Res.string.cloud_backup_webdav_restore),
            onClick = actions::restoreFromWebDav,
        )
    }
}

@Composable
private fun WebDavServerFields(
    state: CloudBackupState,
    onDone: () -> Unit,
) {
    Column {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.webDavUrl,
            onValueChange = {
                state.webDavUrl = it
                state.webDavError = ""
            },
            label = { Text(text = stringResource(Res.string.cloud_backup_webdav_url)) },
            placeholder = { Text(text = "https://dav.example.com/remote.php/dav/files/user/") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.webDavUsername,
            onValueChange = {
                state.webDavUsername = it
                state.webDavError = ""
            },
            label = { Text(text = stringResource(Res.string.cloud_backup_webdav_username)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(8.dp))

        PasswordField(
            value = state.webDavPassword,
            onValueChange = {
                state.webDavPassword = it
                state.webDavError = ""
            },
            label = Res.string.cloud_backup_webdav_password,
            placeholder = null,
            error = "",
            onDone = onDone,
        )

        if (state.webDavError.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = state.webDavError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoogleDriveSheet(
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        modifier = Modifier.outlineIfAny(amoledBorder(), BottomSheetDefaults.ExpandedShape),
        onDismissRequest = onDismiss,
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
                text = stringResource(Res.string.cloud_backup_gdrive_section),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(Res.string.cloud_backup_gdrive_sheet_description),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )

            HorizontalDivider()

            AmberButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(Res.string.cloud_backup_gdrive_backup),
                onClick = onBackup,
            )

            AmberButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(Res.string.cloud_backup_gdrive_restore),
                onClick = onRestore,
            )
        }
    }
}
