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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.Biometrics.authenticate
import com.greenart7c3.nostrsigner.ui.QrCodeDrawer
import com.greenart7c3.nostrsigner.ui.components.CloseButton
import com.greenart7c3.nostrsigner.ui.components.SeedWordsPage
import com.greenart7c3.nostrsigner.ui.theme.ButtonBorder
import com.greenart7c3.nostrsigner.ui.theme.Size35dp
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.commonmark.MarkdownParseOptions
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.resolveDefaults
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNsec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AccountBackupDialog(
    account: Account,
    onClose: () -> Unit,
) {
    var showSeedWords by remember { mutableStateOf(false) }
    if (showSeedWords) {
        Dialog(
            onDismissRequest = {
                showSeedWords = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                modifier =
                Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize(),
            ) {
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CloseButton(onCancel = { showSeedWords = false })
                }
                SeedWordsPage(
                    seedWords = account.seedWords,
                    showNextButton = false,
                ) {}
            }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
            Modifier
                .fillMaxSize(),
        ) {
            Column(
                modifier =
                Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize(),
            ) {
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CloseButton(onCancel = onClose)
                }

                Column(
                    modifier =
                    Modifier
                        .padding(horizontal = 30.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val content = stringResource(R.string.account_backup_tips_md)

                    val astNode =
                        remember {
                            CommonmarkAstNodeParser(MarkdownParseOptions.MarkdownWithLinks).parse(content)
                        }

                    RichText(
                        style = RichTextStyle().resolveDefaults(),
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

                        Button(
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
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.List,
                                    contentDescription = stringResource(R.string.show_seed_words),
                                )
                                Text(text = stringResource(R.string.show_seed_words))
                            }
                        }
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
                            autoCorrect = false,
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
            content = account.keyPair.privKey!!.toNsec(),
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

    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
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
        shape = ButtonBorder,
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp),
    ) {
        Icon(
            tint = MaterialTheme.colorScheme.onPrimary,
            imageVector = Icons.Default.QrCode,
            contentDescription = stringResource(R.string.shows_qr_code_with_you_private_key),
            modifier = Modifier.padding(end = 5.dp),
        )
        Text(
            stringResource(id = R.string.show_qr_code),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
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

    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
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
        shape = ButtonBorder,
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp),
    ) {
        Icon(
            tint = MaterialTheme.colorScheme.onPrimary,
            imageVector = Icons.Default.Key,
            contentDescription = stringResource(R.string.copies_the_nsec_id_your_password_to_the_clipboard_for_backup),
            modifier = Modifier.padding(end = 5.dp),
        )
        Text(stringResource(id = R.string.copy_my_secret_key), color = MaterialTheme.colorScheme.onPrimary)
    }
}

private fun copyNSec(
    context: Context,
    scope: CoroutineScope,
    account: Account,
    clipboardManager: ClipboardManager,
) {
    account.keyPair.privKey?.let {
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

    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
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
        shape = ButtonBorder,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp),
        enabled = password.value.text.isNotBlank(),
    ) {
        Icon(
            tint = MaterialTheme.colorScheme.onPrimary,
            imageVector = Icons.Default.Key,
            contentDescription = stringResource(R.string.copies_the_nsec_id_your_password_to_the_clipboard_for_backup),
            modifier = Modifier.padding(end = 5.dp),
        )
        Text(
            stringResource(id = R.string.encrypt_and_copy_my_secret_key),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
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
            content = CryptoUtils.encryptNIP49(account.keyPair.privKey!!.toHexKey(), password.value.text),
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

    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
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
        shape = ButtonBorder,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp),
        enabled = password.value.text.isNotBlank(),
    ) {
        Icon(
            tint = MaterialTheme.colorScheme.onPrimary,
            imageVector = Icons.Default.QrCode,
            contentDescription = stringResource(R.string.shows_qr_code_with_you_private_key),
            modifier = Modifier.padding(end = 5.dp),
        )
        Text(
            stringResource(id = R.string.show_qr_code),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
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
        account.keyPair.privKey?.let {
            try {
                val key = CryptoUtils.encryptNIP49(it.toHexKey(), password.value.text)
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
