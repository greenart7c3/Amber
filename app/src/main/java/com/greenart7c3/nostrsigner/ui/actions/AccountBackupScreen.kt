package com.greenart7c3.nostrsigner.ui.actions

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil3.compose.SubcomposeAsyncImage
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.Biometrics.authenticate
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.CenterCircularProgressIndicator
import com.greenart7c3.nostrsigner.ui.InnerQrCodeDrawer
import com.greenart7c3.nostrsigner.ui.QrCodeDrawer
import com.greenart7c3.nostrsigner.ui.components.CloseButton
import com.greenart7c3.nostrsigner.ui.components.IconRow
import com.greenart7c3.nostrsigner.ui.components.MarkdownText
import com.greenart7c3.nostrsigner.ui.components.SeedWordsPage
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.Size35dp
import com.greenart7c3.nostrsigner.ui.theme.fromHex
import com.vitorpamplona.quartz.nip06KeyDerivation.Nip06
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AccountBackupScreen(
    modifier: Modifier,
    navController: NavController,
    onShowQrCode: (String) -> Unit,
) {
    var isLoading by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(),
        ) {
            if (isLoading) {
                CenterCircularProgressIndicator(Modifier, text = stringResource(R.string.do_not_leave_the_app_until_the_key_is_generated))
            } else {
                // Collapsible tips section
                var tipsExpanded by remember { mutableStateOf(false) }
                TipsCard(expanded = tipsExpanded, onToggle = { tipsExpanded = !tipsExpanded })

                Spacer(modifier = Modifier.height(16.dp))

                // Password section
                val password = remember { mutableStateOf(TextFieldValue("")) }
                var errorMessage by remember { mutableStateOf("") }
                var showCharsPassword by remember { mutableStateOf(false) }
                val keyboardController = LocalSoftwareKeyboardController.current

                PasswordCard(
                    password = password.value,
                    errorMessage = errorMessage,
                    showCharsPassword = showCharsPassword,
                    onPasswordChange = {
                        password.value = it
                        if (errorMessage.isNotEmpty()) errorMessage = ""
                    },
                    onToggleVisibility = { showCharsPassword = !showCharsPassword },
                    onDone = {
                        if (password.value.text.isBlank()) {
                            errorMessage = Amber.instance.getString(R.string.password_is_required)
                        }
                        keyboardController?.hide()
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))

                LocalPreferences.allSavedAccounts(Amber.instance).forEach { accountInfo ->
                    var localAccount by remember { mutableStateOf<Account?>(null) }
                    var seedWords by remember { mutableStateOf("") }
                    LaunchedEffect(Unit) {
                        launch(Dispatchers.IO) {
                            localAccount = LocalPreferences.loadFromEncryptedStorage(Amber.instance, accountInfo.npub)
                            localAccount?.let { acc ->
                                seedWords = acc.seedWords()
                            }
                        }
                    }

                    localAccount?.let { account ->
                        AccountBackupCard(
                            account = account,
                            password = password.value.text,
                            seedWords = seedWords,
                            onLoading = { isLoading = it },
                            onShowQrCode = onShowQrCode,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                IconRow(
                    title = stringResource(R.string.export_all_accounts_title),
                    icon = Icons.Filled.SaveAlt,
                    tint = MaterialTheme.colorScheme.onBackground,
                    onClick = { navController.navigate(Route.ExportAllAccounts.route) },
                )

                if (!BuildFlavorChecker.isOfflineFlavor()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    IconRow(
                        title = stringResource(R.string.cloud_backup_title),
                        icon = Icons.Filled.CloudUpload,
                        tint = MaterialTheme.colorScheme.onBackground,
                        onClick = { navController.navigate(Route.CloudBackup.route) },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TipsCard(expanded: Boolean, onToggle: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.account_backup),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                TextButton(onClick = onToggle) {
                    Text(if (expanded) stringResource(R.string.hide) else stringResource(R.string.show_tips))
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    val content = stringResource(R.string.account_backup_tips_md)
                    MarkdownText(
                        markdown = content,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PasswordCard(
    password: TextFieldValue,
    errorMessage: String,
    showCharsPassword: Boolean,
    onPasswordChange: (TextFieldValue) -> Unit,
    onToggleVisibility: () -> Unit,
    onDone: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.encryption_password),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val tipsContent = stringResource(R.string.account_backup_tips3_md)
            MarkdownText(
                markdown = tipsContent,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.Password },
                value = password,
                onValueChange = onPasswordChange,
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                placeholder = { Text(text = stringResource(R.string.encryption_password)) },
                trailingIcon = {
                    IconButton(onClick = onToggleVisibility) {
                        Icon(
                            imageVector = if (showCharsPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (showCharsPassword) {
                                stringResource(R.string.show_password)
                            } else {
                                stringResource(R.string.hide_password)
                            },
                        )
                    }
                },
                visualTransformation = if (showCharsPassword) VisualTransformation.None else PasswordVisualTransformation(),
                isError = errorMessage.isNotBlank(),
                supportingText = if (errorMessage.isNotBlank()) {
                    { Text(text = errorMessage, color = MaterialTheme.colorScheme.error) }
                } else {
                    null
                },
                singleLine = true,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountBackupCard(
    account: Account,
    password: String,
    seedWords: String,
    onLoading: (Boolean) -> Unit,
    onShowQrCode: (String) -> Unit,
) {
    val profileUrl = account.picture.collectAsState()
    val name = account.name.collectAsState()
    var didBackup by remember { mutableStateOf(account.didBackup) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Account header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (profileUrl.value.isNotBlank() && !BuildFlavorChecker.isOfflineFlavor()) {
                    SubcomposeAsyncImage(
                        model = profileUrl.value,
                        contentDescription = "profile picture",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        loading = { CenterCircularProgressIndicator(Modifier) },
                        error = {
                            Icon(
                                Icons.Outlined.Person,
                                contentDescription = "profile picture",
                                modifier = Modifier
                                    .size(48.dp)
                                    .border(2.dp, Color.fromHex(account.hexKey.slice(0..5)), CircleShape),
                            )
                        },
                    )
                } else {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = "profile picture",
                        modifier = Modifier
                            .size(48.dp)
                            .border(2.dp, Color.fromHex(account.hexKey.slice(0..5)), CircleShape)
                            .clip(CircleShape),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name.value.ifBlank { account.hexKey.toShortenHex() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Backup status badge
                if (didBackup) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Backed up",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = "Not backed up",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            if (!didBackup) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.make_backup_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Copy and QR buttons side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NSecCopyButton(
                    account = account,
                    password = password,
                    modifier = Modifier.weight(1f),
                    onLoading = onLoading,
                    onBackupDone = { didBackup = true },
                )
                NSecQrButton(
                    account = account,
                    password = password,
                    modifier = Modifier.weight(1f),
                    onLoading = onLoading,
                    onBackupDone = { didBackup = true },
                    onShowQrCode = onShowQrCode,
                )
            }

            // Seed words button (full width, shown only if valid mnemonic)
            if (Nip06().isValidMnemonic(seedWords)) {
                Spacer(modifier = Modifier.height(4.dp))
                SeedWordsButton(
                    account = account,
                    seedWords = seedWords,
                    onBackupDone = { didBackup = true },
                )
            }
        }
    }
}

@Composable
private fun NSecQrButton(
    account: Account,
    password: String,
    modifier: Modifier = Modifier,
    onLoading: (Boolean) -> Unit,
    onBackupDone: () -> Unit,
    onShowQrCode: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showDialog = remember { mutableStateOf(false) }

    if (showDialog.value) {
        var nsec by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            launch(Dispatchers.IO) {
                nsec = account.getNsec()
            }
        }
        if (nsec.isNotBlank()) {
            QrCodeDialog(content = nsec) {
                Amber.instance.applicationIOScope.launch {
                    account.didBackup = true
                    onBackupDone()
                    showDialog.value = false
                }
            }
        }
    }

    val keyguardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (password.isNotBlank()) {
                Amber.instance.applicationIOScope.launch {
                    onLoading(true)
                    val ncryptsec = account.nip49Encrypt(password)
                    account.didBackup = true
                    onBackupDone()
                    onShowQrCode(ncryptsec)
                    onLoading(false)
                }
            } else {
                showDialog.value = true
            }
        }
    }

    val text = stringResource(if (password.isBlank()) R.string.show_qr_code else R.string.encrypt_and_show_qr_code)

    BackupIconButton(
        modifier = modifier,
        icon = { Icon(Icons.Outlined.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp)) },
        text = text,
        onClick = {
            authenticate(
                title = text,
                context = context,
                keyguardLauncher = keyguardLauncher,
                onApproved = {
                    if (password.isNotBlank()) {
                        Amber.instance.applicationIOScope.launch {
                            onLoading(true)
                            val ncryptsec = account.nip49Encrypt(password)
                            account.didBackup = true
                            onBackupDone()
                            onShowQrCode(ncryptsec)
                            onLoading(false)
                        }
                    } else {
                        showDialog.value = true
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

@Composable
fun QrCodeScreen(
    modifier: Modifier,
    content: String,
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Size35dp),
            ) {
                QrCodeDrawer(content, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun QrCodeDialog(
    content: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(Unit) {
        val window = activity?.window
        val originalBrightness = window?.attributes?.screenBrightness
        window?.attributes = window.attributes?.apply {
            screenBrightness = 1f
        }
        onDispose {
            window?.attributes = window.attributes?.apply {
                screenBrightness = originalBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CloseButton(onCancel = onClose)
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 30.dp),
                ) {
                    InnerQrCodeDrawer(content, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeedWordsButton(
    account: Account,
    seedWords: String,
    onBackupDone: () -> Unit,
) {
    val showSeedWords = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val keyguardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            showSeedWords.value = true
        }
    }

    if (showSeedWords.value) {
        ModalBottomSheet(
            onDismissRequest = { showSeedWords.value = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize(),
            ) {
                CompositionLocalProvider(
                    LocalDensity provides Density(LocalDensity.current.density, 1f),
                ) {
                    SeedWordsPage(
                        seedWords = seedWords.split(" ").toSet(),
                        showNextButton = false,
                    )
                }
            }
        }
    }

    val text = stringResource(R.string.show_seed_words)

    BackupIconButton(
        modifier = Modifier.fillMaxWidth(),
        icon = { Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp)) },
        text = text,
        colors = ButtonDefaults.outlinedButtonColors(),
        onClick = {
            authenticate(
                title = text,
                context = context,
                keyguardLauncher = keyguardLauncher,
                onApproved = {
                    Amber.instance.applicationIOScope.launch {
                        account.didBackup = true
                        onBackupDone()
                        showSeedWords.value = true
                    }
                },
                onError = { _, message ->
                    Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        },
    )
}

@Composable
private fun NSecCopyButton(
    account: Account,
    password: String,
    modifier: Modifier = Modifier,
    onLoading: (Boolean) -> Unit,
    onBackupDone: () -> Unit,
) {
    val clipboardManager = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val keyguardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (password.isNotBlank()) {
                encryptCopyNSec(password, Amber.instance, account, clipboardManager, onLoading, onBackupDone)
            } else {
                copyNSec(account, clipboardManager, onBackupDone)
            }
        }
    }

    val text = stringResource(if (password.isBlank()) R.string.copy_to_clipboard else R.string.encrypt_and_copy_to_clipboard)

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
                        onBackupDone()
                        if (password.isNotBlank()) {
                            encryptCopyNSec(password, Amber.instance, account, clipboardManager, onLoading, onBackupDone)
                        } else {
                            copyNSec(account, clipboardManager, onBackupDone)
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

@Composable
private fun BackupIconButton(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    text: String,
    colors: ButtonColors = ButtonDefaults.buttonColors().copy(contentColor = Color.Black),
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = colors,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        CompositionLocalProvider(
            LocalDensity provides Density(LocalDensity.current.density, 1f),
        ) {
            icon()
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

private fun copyNSec(
    account: Account,
    clipboardManager: Clipboard,
    onBackupDone: () -> Unit,
) {
    account.copyToClipboard(clipboardManager)
    onBackupDone()
}

private fun encryptCopyNSec(
    password: String,
    context: Context,
    account: Account,
    clipboardManager: Clipboard,
    onLoading: (Boolean) -> Unit,
    onBackupDone: () -> Unit,
) {
    if (password.isBlank()) {
        Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.password_is_required), Toast.LENGTH_SHORT).show()
        }
    } else {
        Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
            onLoading(true)
            try {
                val key = account.nip49Encrypt(password)
                account.didBackup = true
                onBackupDone()
                Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                    clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("", key)))
                    Toast.makeText(context, context.getString(R.string.secret_key_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                }
                onLoading(false)
            } catch (_: Exception) {
                onLoading(false)
                Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.failed_to_encrypt_key), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
