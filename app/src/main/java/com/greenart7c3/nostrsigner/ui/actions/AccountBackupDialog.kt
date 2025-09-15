package com.greenart7c3.nostrsigner.ui.actions

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import coil3.compose.SubcomposeAsyncImage
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.Biometrics.authenticate
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.CenterCircularProgressIndicator
import com.greenart7c3.nostrsigner.ui.InnerQrCodeDrawer
import com.greenart7c3.nostrsigner.ui.QrCodeDrawer
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.CloseButton
import com.greenart7c3.nostrsigner.ui.components.SeedWordsPage
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.Size35dp
import com.greenart7c3.nostrsigner.ui.theme.fromHex
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.commonmark.MarkdownParseOptions
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.resolveDefaults
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip06KeyDerivation.Nip06
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AccountBackupScreen(
    modifier: Modifier,
    account: Account,
    navController: NavHostController,
) {
    var isLoading by remember { mutableStateOf(false) }
    var showSeedWords by remember { mutableStateOf(false) }
    if (showSeedWords) {
        ModalBottomSheet(
            onDismissRequest = {
                showSeedWords = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier =
                Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize(),
            ) {
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        LocalDensity.current.density,
                        1f,
                    ),
                ) {
                    SeedWordsPage(
                        seedWords = account.seedWords,
                        showNextButton = false,
                    )
                }
            }
        }
    }

    Surface(
        modifier =
        modifier
            .fillMaxSize(),
    ) {
        Column(
            modifier =
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isLoading) {
                    CenterCircularProgressIndicator(Modifier)
                } else {
                    val content = stringResource(R.string.account_backup_tips_md)

                    val astNode =
                        remember {
                            CommonmarkAstNodeParser(MarkdownParseOptions.MarkdownWithLinks).parse(content)
                        }

                    RichText(
                        style = RichTextStyle().resolveDefaults(),
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        BasicMarkdown(astNode)
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    if (Nip06().isValidMnemonic(account.seedWords.joinToString(separator = " "))) {
                        val keyguardLauncher =
                            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                                if (result.resultCode == Activity.RESULT_OK) {
                                    showSeedWords = true
                                }
                            }
                        val context = LocalContext.current

                        AmberButton(
                            onClick = {
                                authenticate(
                                    title = context.getString(R.string.show_seed_words),
                                    context = context,
                                    keyguardLauncher = keyguardLauncher,
                                    onApproved = {
                                        showSeedWords = true
                                        account.didBackup = true
                                        Amber.instance.applicationIOScope.launch {
                                            LocalPreferences.saveToEncryptedStorage(context, account)
                                        }
                                    },
                                    onError = { _, message ->
                                        Toast.makeText(
                                            context,
                                            message,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                            },
                            text = stringResource(R.string.show_seed_words),
                        )
                    }

                    val content1 = stringResource(R.string.account_backup_tips3_md)

                    val astNode1 =
                        remember {
                            CommonmarkAstNodeParser(MarkdownParseOptions.MarkdownWithLinks).parse(content1)
                        }

                    RichText(
                        style = RichTextStyle().resolveDefaults(),
                    ) {
                        BasicMarkdown(astNode1)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val password = remember { mutableStateOf(TextFieldValue("")) }
                    var errorMessage by remember { mutableStateOf("") }
                    var showCharsPassword by remember { mutableStateOf(false) }
                    val keyboardController = LocalSoftwareKeyboardController.current

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
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (password.value.text.isBlank()) {
                                    errorMessage = Amber.instance.getString(R.string.password_is_required)
                                }
                                keyboardController?.hide()
                            },
                        ),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.ncryptsec_password),
                            )
                        },
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { showCharsPassword = !showCharsPassword }) {
                                    Icon(
                                        imageVector = if (showCharsPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = if (showCharsPassword) {
                                            stringResource(R.string.show_password)
                                        } else {
                                            stringResource(
                                                R.string.hide_password,
                                            )
                                        },
                                    )
                                }
                            }
                        },
                        visualTransformation = if (showCharsPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    )

                    if (errorMessage.isNotBlank()) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LocalPreferences.allSavedAccounts(Amber.instance).forEach {
                        var localAccount by remember { mutableStateOf<Account?>(null) }
                        LaunchedEffect(Unit) {
                            launch(Dispatchers.IO) {
                                localAccount = LocalPreferences.loadFromEncryptedStorage(Amber.instance, it.npub)
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                        ) {
                            localAccount?.let { localAccount ->
                                val profileUrl = localAccount.picture.value
                                @Suppress("KotlinConstantConditions")
                                if (profileUrl.isNotBlank() && BuildConfig.FLAVOR != "offline") {
                                    SubcomposeAsyncImage(
                                        profileUrl,
                                        "profile picture",
                                        Modifier
                                            .clip(
                                                RoundedCornerShape(50),
                                            )
                                            .height(28.dp)
                                            .width(28.dp),
                                        loading = {
                                            CenterCircularProgressIndicator(Modifier)
                                        },
                                        error = { error ->
                                            Icon(
                                                Icons.Outlined.Person,
                                                "profile picture",
                                                modifier = Modifier.border(
                                                    2.dp,
                                                    Color.fromHex(localAccount.hexKey.slice(0..5)),
                                                    CircleShape,
                                                ),
                                            )
                                        },
                                    )
                                } else {
                                    Icon(
                                        Icons.Outlined.Person,
                                        "profile picture",
                                        modifier = Modifier.border(
                                            2.dp,
                                            Color.fromHex(localAccount.hexKey.slice(0..5)),
                                            CircleShape,
                                        ),
                                    )
                                }
                                Text(
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .fillMaxWidth(0.8f),
                                    text = localAccount.name.value.ifBlank { it.npub.toShortenHex() },
                                )

                                NSecCopyButton(localAccount, password.value.text, onLoading = { value -> isLoading = value })
                                NSecQrButton(localAccount, password.value.text, onLoading = { value -> isLoading = value }, navController = navController)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NSecQrButton(
    account: Account,
    password: String,
    onLoading: (Boolean) -> Unit,
    navController: NavHostController,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by remember {
        mutableStateOf(false)
    }

    if (showDialog) {
        QrCodeDialog(
            content = account.signer.keyPair.privKey!!.toNsec(),
        ) {
            Amber.instance.applicationIOScope.launch {
                account.didBackup = true
                LocalPreferences.saveToEncryptedStorage(context, account)
                showDialog = false
            }
        }
    }

    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                if (password.isNotBlank()) {
                    Amber.instance.applicationIOScope.launch {
                        onLoading(true)
                        val ncryptsec = Nip49().encrypt(account.signer.keyPair.privKey!!.toHexKey(), password)
                        account.didBackup = true
                        LocalPreferences.saveToEncryptedStorage(context, account)
                        Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                            navController.navigate(Route.QrCode.route.replace("{content}", ncryptsec))
                        }
                        onLoading(false)
                    }
                } else {
                    showDialog = true
                }
            }
        }

    IconButton(
        onClick = {
            authenticate(
                title = context.getString(R.string.show_qr_code),
                context = context,
                keyguardLauncher = keyguardLauncher,
                onApproved = {
                    if (password.isNotBlank()) {
                        Amber.instance.applicationIOScope.launch {
                            onLoading(true)
                            val ncryptsec = Nip49().encrypt(account.signer.keyPair.privKey!!.toHexKey(), password)
                            account.didBackup = true
                            LocalPreferences.saveToEncryptedStorage(context, account)
                            Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                                navController.navigate(Route.QrCode.route.replace("{content}", ncryptsec))
                            }
                            onLoading(false)
                        }
                    } else {
                        showDialog = true
                    }
                },
                onError = { _, message ->
                    scope.launch {
                        Toast.makeText(
                            context,
                            message,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        },
        content = {
            Icon(
                imageVector = Icons.Outlined.QrCode,
                contentDescription = context.getString(R.string.show_qr_code),
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
        modifier = modifier
            .fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
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

@Composable
private fun NSecCopyButton(
    account: Account,
    password: String,
    onLoading: (Boolean) -> Unit,
) {
    val clipboardManager = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                if (password.isNotBlank()) {
                    encryptCopyNSec(password, Amber.instance, account, clipboardManager, onLoading)
                } else {
                    copyNSec(context, scope, account, clipboardManager)
                }
            }
        }

    IconButton(
        onClick = {
            authenticate(
                title = context.getString(R.string.copy_my_secret_key),
                context = context,
                keyguardLauncher = keyguardLauncher,
                onApproved = {
                    scope.launch(Dispatchers.IO) {
                        account.didBackup = true
                        LocalPreferences.saveToEncryptedStorage(context, account)
                        if (password.isNotBlank()) {
                            encryptCopyNSec(password, Amber.instance, account, clipboardManager, onLoading)
                        } else {
                            copyNSec(context, scope, account, clipboardManager)
                        }
                    }
                },
                onError = { _, message ->
                    scope.launch {
                        Toast.makeText(
                            context,
                            message,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        },
        content = {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = context.getString(R.string.copy_my_secret_key),
            )
        },
    )
}

private fun copyNSec(
    context: Context,
    scope: CoroutineScope,
    account: Account,
    clipboardManager: Clipboard,
) {
    account.signer.keyPair.privKey?.let {
        account.didBackup = true
        scope.launch(Dispatchers.IO) {
            LocalPreferences.saveToEncryptedStorage(context, account)
        }
        scope.launch {
            clipboardManager.setClipEntry(
                ClipEntry(
                    ClipData.newPlainText("", it.toNsec()),
                ),
            )

            Toast.makeText(
                context,
                context.getString(R.string.secret_key_copied_to_clipboard),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}

private fun encryptCopyNSec(
    password: String,
    context: Context,
    account: Account,
    clipboardManager: Clipboard,
    onLoading: (Boolean) -> Unit,
) {
    if (password.isBlank()) {
        Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.getString(R.string.password_is_required),
                Toast.LENGTH_SHORT,
            )
                .show()
        }
    } else {
        Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
            onLoading(true)
            account.signer.keyPair.privKey?.let {
                try {
                    val key = Nip49().encrypt(it.toHexKey(), password)
                    account.didBackup = true
                    LocalPreferences.saveToEncryptedStorage(context, account)
                    Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                        clipboardManager.setClipEntry(
                            ClipEntry(
                                ClipData.newPlainText("", key),
                            ),
                        )

                        Toast.makeText(
                            context,
                            context.getString(R.string.secret_key_copied_to_clipboard),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    onLoading(false)
                } catch (_: Exception) {
                    onLoading(false)
                    Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.failed_to_encrypt_key),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }
}
