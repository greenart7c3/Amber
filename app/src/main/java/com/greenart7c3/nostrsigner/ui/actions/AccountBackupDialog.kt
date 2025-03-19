package com.greenart7c3.nostrsigner.ui.actions

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
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
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.Biometrics.authenticate
import com.greenart7c3.nostrsigner.ui.QrCodeDrawer
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.CloseButton
import com.greenart7c3.nostrsigner.ui.components.SeedWordsPage
import com.greenart7c3.nostrsigner.ui.theme.Size35dp
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.commonmark.MarkdownParseOptions
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.resolveDefaults
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AccountBackupScreen(
    modifier: Modifier,
    account: Account,
) {
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
                    ) {}
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

                if (account.seedWords.isNotEmpty()) {
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
                NSecCopyButton(account)
                NSecQrButton(account)

                Spacer(modifier = Modifier.height(30.dp))

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

                val autofillNode =
                    AutofillNode(
                        autofillTypes = listOf(AutofillType.Password),
                        onFill = { password.value = TextFieldValue(it) },
                    )
                val autofill = LocalAutofill.current
                LocalAutofillTree.current += autofillNode

                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            autofillNode.boundingBox = coordinates.boundsInWindow()
                        }
                        .onFocusChanged { focusState ->
                            autofill?.run {
                                if (focusState.isFocused) {
                                    requestAutofillForNode(autofillNode)
                                } else {
                                    cancelAutofillForNode(autofillNode)
                                }
                            }
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
                        imeAction = ImeAction.Go,
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

                EncryptNSecCopyButton(account, password)
                EncryptNSecQRButton(account, password)
            }
        }
    }
}

@Composable
private fun NSecQrButton(account: Account) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by remember {
        mutableStateOf(false)
    }

    if (showDialog) {
        QrCodeDialog(
            content = account.signer.keyPair.privKey!!.toNsec(),
        ) {
            showDialog = false
        }
    }

    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                showDialog = true
            }
        }

    AmberButton(
        onClick = {
            authenticate(
                title = context.getString(R.string.show_qr_code),
                context = context,
                keyguardLauncher = keyguardLauncher,
                onApproved = {
                    showDialog = true
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
        text = stringResource(id = R.string.show_qr_code),
    )
}

@Composable
fun QrCodeDialog(
    content: String,
    onClose: () -> Unit,
) {
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
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Size35dp),
                    ) {
                        QrCodeDrawer(content)
                    }
                }
            }
        }
    }
}

@Composable
private fun NSecCopyButton(account: Account) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                copyNSec(context, scope, account, clipboardManager)
            }
        }

    AmberButton(
        onClick = {
            authenticate(
                title = context.getString(R.string.copy_my_secret_key),
                context = context,
                keyguardLauncher = keyguardLauncher,
                onApproved = {
                    copyNSec(context, scope, account, clipboardManager)
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
        text = stringResource(id = R.string.copy_my_secret_key),
    )
}

private fun copyNSec(
    context: Context,
    scope: CoroutineScope,
    account: Account,
    clipboardManager: ClipboardManager,
) {
    account.signer.keyPair.privKey?.let {
        clipboardManager.setText(AnnotatedString(it.toNsec()))
        scope.launch {
            Toast.makeText(
                context,
                context.getString(R.string.secret_key_copied_to_clipboard),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}

@Composable
private fun EncryptNSecCopyButton(
    account: Account,
    password: MutableState<TextFieldValue>,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                encryptCopyNSec(password, context, scope, account, clipboardManager)
            }
        }

    AmberButton(
        onClick = {
            authenticate(
                title = context.getString(R.string.copy_my_secret_key),
                context = context,
                keyguardLauncher = keyguardLauncher,
                onApproved = { encryptCopyNSec(password, context, scope, account, clipboardManager) },
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
        enabled = password.value.text.isNotBlank(),
        text = stringResource(id = R.string.encrypt_and_copy_my_secret_key),
        maxLines = 1,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun EncryptNSecQRButton(
    account: Account,
    password: MutableState<TextFieldValue>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showDialog by remember {
        mutableStateOf(false)
    }

    if (showDialog) {
        QrCodeDialog(
            content = Nip49().encrypt(account.signer.keyPair.privKey!!.toHexKey(), password.value.text),
        ) {
            showDialog = false
        }
    }

    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                showDialog = true
            }
        }

    AmberButton(
        onClick = {
            authenticate(
                title = context.getString(R.string.copy_my_secret_key),
                context = context,
                keyguardLauncher = keyguardLauncher,
                onApproved = { showDialog = true },
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
        enabled = password.value.text.isNotBlank(),
        text = stringResource(id = R.string.show_qr_code),
    )
}

private fun encryptCopyNSec(
    password: MutableState<TextFieldValue>,
    context: Context,
    scope: CoroutineScope,
    account: Account,
    clipboardManager: ClipboardManager,
) {
    if (password.value.text.isBlank()) {
        scope.launch {
            Toast.makeText(
                context,
                context.getString(R.string.password_is_required),
                Toast.LENGTH_SHORT,
            )
                .show()
        }
    } else {
        account.signer.keyPair.privKey?.let {
            try {
                val key = Nip49().encrypt(it.toHexKey(), password.value.text)
                clipboardManager.setText(AnnotatedString(key))
                scope.launch {
                    Toast.makeText(
                        context,
                        context.getString(R.string.secret_key_copied_to_clipboard),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                scope.launch {
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
