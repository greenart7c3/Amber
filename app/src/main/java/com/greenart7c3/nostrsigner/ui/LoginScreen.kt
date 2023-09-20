package com.greenart7c3.nostrsigner.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.ui.theme.Font14SP
import com.greenart7c3.nostrsigner.ui.theme.Size35dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginPage(
    accountViewModel: AccountStateViewModel
) {
    val key = remember { mutableStateOf(TextFieldValue("")) }
    var errorMessage by remember { mutableStateOf("") }
    var dialogOpen by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // The first child is glued to the top.
        // Hence we have nothing at the top, an empty box is used.
        Box(modifier = Modifier.height(0.dp))

        // The second child, this column, is centered vertically.
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            var showPassword by remember {
                mutableStateOf(false)
            }

            val autofillNode = AutofillNode(
                autofillTypes = listOf(AutofillType.Password),
                onFill = { key.value = TextFieldValue(it) }
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
                value = key.value,
                onValueChange = { key.value = it },
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Go
                ),
                placeholder = {
                    Text(
                        text = "nsec.."
                    )
                },
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showPassword) {
                                    "Show password"
                                } else {
                                    "Hide password"
                                }
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
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardActions = KeyboardActions(
                    onGo = {
                        try {
                            accountViewModel.startUI(key.value.text, null)
                        } catch (e: Exception) {
                            errorMessage = "Invalid key"
                        }
                    }
                )
            )
            if (errorMessage.isNotBlank()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(modifier = Modifier.padding(40.dp, 0.dp, 40.dp, 0.dp)) {
                Button(
                    onClick = {
                        if (key.value.text.isBlank()) {
                            errorMessage = "Key is required"
                        }

                        if (key.value.text.isNotBlank()) {
                            try {
                                accountViewModel.startUI(key.value.text, null)
                            } catch (e: Exception) {
                                Log.e("Login", "Could not sign in", e)
                                errorMessage = "Invalid key"
                            }
                        }
                    },
                    shape = RoundedCornerShape(Size35dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(text = "Login")
                }
            }
        }

        // The last child is glued to the bottom.
        ClickableText(
            text = AnnotatedString("Generate a new key"),
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            onClick = {
                accountViewModel.newKey()
            },
            style = TextStyle(
                fontSize = Font14SP,
                textDecoration = TextDecoration.Underline,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        )
    }
}
