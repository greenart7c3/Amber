package com.greenart7c3.nostrsigner.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.ui.theme.ButtonBorder
import kotlinx.coroutines.launch

@Composable
fun RawJson(
    rawJson: String,
    encryptedData: String,
    modifier: Modifier,
    label: String = stringResource(R.string.copy_raw_json),
    type: SignerType,
    onCopy: (() -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var currentContent by remember {
        mutableStateOf(rawJson)
    }

    OutlinedTextField(
        modifier = modifier
            .fillMaxWidth(),
        value = TextFieldValue(currentContent),
        onValueChange = { },
        readOnly = true,
    )
    if (type == SignerType.NIP04_DECRYPT || type == SignerType.NIP44_DECRYPT) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Button(
                shape = ButtonBorder,
                onClick = {
                    currentContent =
                        if (currentContent == encryptedData) {
                            rawJson
                        } else {
                            encryptedData
                        }
                },
            ) {
                Text(stringResource(R.string.decrypt_content))
            }
        }
    }
    Row(
        Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        AmberButton(
            onClick = {
                if (onCopy != null) {
                    onCopy()
                } else {
                    clipboardManager.setText(AnnotatedString(rawJson))

                    coroutineScope.launch {
                        Toast.makeText(
                            context,
                            context.getString(R.string.data_copied_to_the_clipboard),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            text = (stringResource(R.string.copy) + label),
        )
    }
}
