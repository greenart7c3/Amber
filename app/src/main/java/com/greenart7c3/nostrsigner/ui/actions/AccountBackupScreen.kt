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
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
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
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.CloseButton
import com.greenart7c3.nostrsigner.ui.components.SeedWordsPage
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.Size35dp
import com.greenart7c3.nostrsigner.ui.theme.fromHex
import com.halilibo.richtext.commonmark.CommonMarkdownParseOptions
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.resolveDefaults
import com.vitorpamplona.quartz.nip06KeyDerivation.Nip06
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AccountBackupScreen(
    modifier: Modifier,
    navController: NavHostController,
) {
    var isLoading by remember { mutableStateOf(false) }

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
                    CenterCircularProgressIndicator(Modifier, text = stringResource(R.string.do_not_leave_the_app_until_the_key_is_generated))
                } else {
                    val content = stringResource(R.string.account_backup_tips_md)

                    val astNode =
                        remember {
                            CommonmarkAstNodeParser(CommonMarkdownParseOptions.MarkdownWithLinks).parse(content)
                        }

                    RichText(
                        style = RichTextStyle().resolveDefaults(),
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        BasicMarkdown(astNode)
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    val content1 = stringResource(R.string.account_backup_tips3_md_new)

                    val astNode1 =
                        remember {
                            CommonmarkAstNodeParser(CommonMarkdownParseOptions.MarkdownWithLinks).parse(content1)
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
                                text = stringResource(R.string.encryption_password),
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
                        var seedWords by remember { mutableStateOf("") }
                        LaunchedEffect(Unit) {
                            launch(Dispatchers.IO) {
                                localAccount = LocalPreferences.loadFromEncryptedStorage(Amber.instance, it.npub)
                                localAccount?.let { acc ->
                                    seedWords = acc.seedWords()
                                }
                            }
                        }

                        localAccount?.let { localAccount ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .border(
                                        2.dp,
                                        Color.LightGray,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .padding(8.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp),
                                ) {
                                    val profileUrl = localAccount.picture.collectAsState()
                                    val name = localAccount.name.collectAsState()
                                    if (profileUrl.value.isNotBlank() && !BuildFlavorChecker.isOfflineFlavor()) {
                                        SubcomposeAsyncImage(
                                            profileUrl,
                                            "profile picture",
                                            Modifier
                                                .clip(
                                                    RoundedCornerShape(50),
                                                )
                                                .height(40.dp)
                                                .width(40.dp),
                                            loading = {
                                                CenterCircularProgressIndicator(Modifier)
                                            },
                                            error = { _ ->
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
                                            modifier = Modifier
                                                .height(40.dp)
                                                .width(40.dp)
                                                .border(
                                                    2.dp,
                                                    Color.fromHex(localAccount.hexKey.slice(0..5)),
                                                    CircleShape,
                                                ),
                                        )
                                    }
                                    Text(
                                        modifier = Modifier
                                            .padding(start = 4.dp),
                                        text = name.value.ifBlank { it.npub.toShortenHex() },
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                NSecCopyButton(localAccount, password.value.text, onLoading = { value -> isLoading = value })
                                NSecQrButton(localAccount, password.value.text, onLoading = { value -> isLoading = value }, navController = navController)
                                if (Nip06().isValidMnemonic(seedWords)) {
                                    SeedWordsButton(localAccount, seedWords)
                                }
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
    val showDialog = remember {
        mutableStateOf(false)
    }

    if (showDialog.value) {
        var nsec by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            launch(Dispatchers.IO) {
                nsec = account.getNsec()
            }
        }

        if (nsec.isNotBlank()) {
            QrCodeDialog(
                content = nsec,
            ) {
                Amber.instance.applicationIOScope.launch {
                    account.didBackup = true
                    showDialog.value = false
                }
            }
        }
    }

    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                if (password.isNotBlank()) {
                    Amber.instance.applicationIOScope.launch {
                        onLoading(true)
                        val ncryptsec = account.nip49Encrypt(password)
                        account.didBackup = true
                        Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                            navController.navigate(Route.QrCode.route.replace("{content}", ncryptsec))
                        }
                        onLoading(false)
                    }
                } else {
                    showDialog.value = true
                }
            }
        }

    val text = stringResource(if (password.isBlank()) R.string.show_qr_code else R.string.encrypt_and_show_qr_code)

    AmberButton(
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
                            Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                                navController.navigate(Route.QrCode.route.replace("{content}", ncryptsec))
                            }
                            onLoading(false)
                        }
                    } else {
                        showDialog.value = true
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
        text = text,

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeedWordsButton(
    account: Account,
    seedWords: String,
) {
    val showSeedWords = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                showSeedWords.value = true
            }
        }

    if (showSeedWords.value) {
        ModalBottomSheet(
            onDismissRequest = {
                showSeedWords.value = false
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
                        seedWords = seedWords.split(" ").toSet(),
                        showNextButton = false,
                    )
                }
            }
        }
    }

    val text = stringResource(R.string.show_seed_words)

    AmberButton(
        onClick = {
            authenticate(
                title = text,
                context = context,
                keyguardLauncher = keyguardLauncher,
                onApproved = {
                    Amber.instance.applicationIOScope.launch {
                        account.didBackup = true
                        showSeedWords.value = true
                    }
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
        },
        text = text,
    )
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
                    copyNSec(account, clipboardManager)
                }
            }
        }

    val text = stringResource(if (password.isBlank()) R.string.copy_to_clipboard else R.string.encrypt_and_copy_to_clipboard)

    AmberButton(
        onClick = {
            authenticate(
                title = text,
                context = context,
                keyguardLauncher = keyguardLauncher,
                onApproved = {
                    scope.launch(Dispatchers.IO) {
                        account.didBackup = true
                        if (password.isNotBlank()) {
                            encryptCopyNSec(password, Amber.instance, account, clipboardManager, onLoading)
                        } else {
                            copyNSec(account, clipboardManager)
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
        text = text,
    )
}

private fun copyNSec(
    account: Account,
    clipboardManager: Clipboard,
) {
    account.copyToClipboard(clipboardManager)
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
            try {
                val key = account.nip49Encrypt(password)
                account.didBackup = true
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
