package com.greenart7c3.nostrsigner.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.service.PackageUtils
import com.greenart7c3.nostrsigner.ui.actions.ConnectOrbotDialog
import com.greenart7c3.nostrsigner.ui.theme.Size35dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginPage(accountViewModel: AccountStateViewModel) {
    val key = remember { mutableStateOf(TextFieldValue("")) }
    var errorMessage by remember { mutableStateOf("") }
    var dialogOpen by remember {
        mutableStateOf(false)
    }
    val context = LocalContext.current
    val password = remember { mutableStateOf(TextFieldValue("")) }
    val needsPassword =
        remember {
            derivedStateOf {
                key.value.text.startsWith("ncryptsec1")
            }
        }
    val useProxy = remember { mutableStateOf(false) }
    val proxyPort = remember { mutableStateOf("9050") }
    var connectOrbotDialogOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        // The first child is glued to the top.
        // Hence we have nothing at the top, an empty box is used.
        Box(modifier = Modifier.height(0.dp))

        // The second child, this column, is centered vertically.
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            var showPassword by remember {
                mutableStateOf(false)
            }

            var showCharsPassword by remember { mutableStateOf(false) }

            val autofillNodeKey =
                AutofillNode(
                    autofillTypes = listOf(AutofillType.Password),
                    onFill = { key.value = TextFieldValue(it) },
                )

            val autofillNodePassword =
                AutofillNode(
                    autofillTypes = listOf(AutofillType.Password),
                    onFill = { key.value = TextFieldValue(it) },
                )

            val autofill = LocalAutofill.current
            LocalAutofillTree.current += autofillNodeKey
            LocalAutofillTree.current += autofillNodePassword

            OutlinedTextField(
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        autofillNodeKey.boundingBox = coordinates.boundsInWindow()
                    }
                    .onFocusChanged { focusState ->
                        autofill?.run {
                            if (focusState.isFocused) {
                                requestAutofillForNode(autofillNodeKey)
                            } else {
                                cancelAutofillForNode(autofillNodeKey)
                            }
                        }
                    },
                value = key.value,
                onValueChange = { key.value = it },
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Go,
                ),
                placeholder = {
                    Text(
                        text = stringResource(R.string.nsec),
                    )
                },
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showPassword) {
                                    stringResource(R.string.show_password)
                                } else {
                                    stringResource(R.string.hide_password)
                                },
                            )
                        }
                    }
                },
                leadingIcon = {
                    if (dialogOpen) {
                        SimpleQrCodeScanner {
                            dialogOpen = false
                            if (!it.isNullOrEmpty()) {
                                key.value = TextFieldValue(it)
                            }
                        }
                    }
                    IconButton(onClick = { dialogOpen = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_qrcode),
                            null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardActions = KeyboardActions(
                    onGo = {
                        if (key.value.text.isBlank()) {
                            errorMessage = context.getString(R.string.key_is_required)
                        }

                        if (needsPassword.value && password.value.text.isBlank()) {
                            errorMessage = context.getString(R.string.password_is_required)
                        }

                        if (key.value.text.isNotBlank() && !(needsPassword.value && password.value.text.isBlank())) {
                            try {
                                accountViewModel.startUI(
                                    key.value.text,
                                    password.value.text,
                                    null,
                                    useProxy = useProxy.value,
                                    proxyPort = proxyPort.value.toInt(),
                                )
                            } catch (e: Exception) {
                                Log.e("Login", "Could not sign in", e)
                                errorMessage = context.getString(R.string.invalid_key)
                            }
                        }
                    },
                ),
            )
            if (errorMessage.isNotBlank()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (needsPassword.value) {
                OutlinedTextField(
                    modifier = Modifier
                        .onGloballyPositioned { coordinates ->
                            autofillNodePassword.boundingBox = coordinates.boundsInWindow()
                        }
                        .onFocusChanged { focusState ->
                            autofill?.run {
                                if (focusState.isFocused) {
                                    requestAutofillForNode(autofillNodePassword)
                                } else {
                                    cancelAutofillForNode(autofillNodePassword)
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
                    keyboardActions = KeyboardActions(
                        onGo = {
                            if (key.value.text.isBlank()) {
                                errorMessage = context.getString(R.string.key_is_required)
                            }

                            if (needsPassword.value && password.value.text.isBlank()) {
                                errorMessage = context.getString(R.string.password_is_required)
                            }

                            if (key.value.text.isNotBlank() && !(needsPassword.value && password.value.text.isBlank())) {
                                try {
                                    accountViewModel.startUI(
                                        key.value.text,
                                        password.value.text,
                                        null,
                                        useProxy = useProxy.value,
                                        proxyPort = proxyPort.value.toInt(),
                                    )
                                } catch (e: Exception) {
                                    Log.e("Login", "Could not sign in", e)
                                    errorMessage = context.getString(R.string.invalid_key)
                                }
                            }
                        },
                    ),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            @Suppress("KotlinConstantConditions")
            if (BuildConfig.FLAVOR != "offline" && PackageUtils.isOrbotInstalled(context)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useProxy.value,
                        onCheckedChange = {
                            if (it) {
                                connectOrbotDialogOpen = true
                            } else {
                                useProxy.value = false
                            }
                        },
                    )

                    Text("Connect through your Orbot setup")
                }

                if (connectOrbotDialogOpen) {
                    ConnectOrbotDialog(
                        onClose = { connectOrbotDialogOpen = false },
                        onPost = {
                            connectOrbotDialogOpen = false
                            useProxy.value = true
                        },
                        onError = {
                            scope.launch {
                                Toast.makeText(
                                    context,
                                    it,
                                    Toast.LENGTH_LONG,
                                )
                                    .show()
                            }
                        },
                        proxyPort,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(modifier = Modifier.padding(40.dp, 0.dp, 40.dp, 0.dp)) {
                Button(
                    onClick = {
                        if (key.value.text.isBlank()) {
                            errorMessage = context.getString(R.string.key_is_required)
                        }

                        if (needsPassword.value && password.value.text.isBlank()) {
                            errorMessage = context.getString(R.string.password_is_required)
                        }

                        if (key.value.text.isNotBlank() && !(needsPassword.value && password.value.text.isBlank())) {
                            try {
                                accountViewModel.startUI(
                                    key.value.text,
                                    password.value.text,
                                    null,
                                    useProxy = useProxy.value,
                                    proxyPort = proxyPort.value.toInt(),
                                )
                            } catch (e: Exception) {
                                Log.e("Login", "Could not sign in", e)
                                errorMessage = context.getString(R.string.invalid_key)
                            }
                        }
                    },
                    shape = RoundedCornerShape(Size35dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Text(text = stringResource(R.string.login))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                modifier = Modifier
                    .padding(30.dp)
                    .fillMaxWidth(),
                onClick = {
                    accountViewModel.newKey(useProxy.value, proxyPort.value.toInt())
                },
            ) {
                Text(stringResource(R.string.generate_a_new_key))
            }
        }
    }
}
