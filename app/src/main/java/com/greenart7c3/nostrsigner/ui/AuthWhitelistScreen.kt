package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AuthWhitelistScreen(
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboard.current
    val textFieldRelay = remember { mutableStateOf(TextFieldValue("")) }
    val whitelist = remember {
        mutableStateListOf(*Amber.instance.settings.authWhitelist.toTypedArray())
    }

    fun addEntry() {
        val url = textFieldRelay.value.text.trim()
        if (url.isNotBlank() && url !in whitelist) {
            whitelist.add(url)
            Amber.instance.settings = Amber.instance.settings.copy(authWhitelist = whitelist.toList())
            LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
            textFieldRelay.value = TextFieldValue("")
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(),
        ) {
            Text(
                text = stringResource(R.string.auth_whitelist_description),
                modifier = Modifier.padding(bottom = 8.dp),
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = textFieldRelay.value.text,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                onValueChange = {
                    textFieldRelay.value = TextFieldValue(it)
                },
                keyboardActions = KeyboardActions(
                    onDone = { addEntry() },
                ),
                label = {
                    Text(stringResource(R.string.add_relay_to_whitelist))
                },
                leadingIcon = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val text = clipboardManager.getClipEntry()?.clipData?.getItemAt(0)?.text?.toString()
                                if (!text.isNullOrBlank()) {
                                    textFieldRelay.value = TextFieldValue(text)
                                }
                            }
                        },
                    ) {
                        Icon(
                            Icons.Default.ContentPaste,
                            contentDescription = stringResource(R.string.paste_from_clipboard),
                        )
                    }
                },
                trailingIcon = {
                    IconButton(
                        colors = IconButtonDefaults.iconButtonColors().copy(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        onClick = { addEntry() },
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.add),
                        )
                    }
                },
            )

            LazyColumn(
                Modifier.weight(1f),
            ) {
                items(whitelist.size) { index ->
                    RelayCard(
                        relay = whitelist[index],
                        onClick = {
                            whitelist.removeAt(index)
                            Amber.instance.settings = Amber.instance.settings.copy(
                                authWhitelist = whitelist.toList(),
                            )
                            scope.launch(Dispatchers.IO) {
                                LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
                            }
                        },
                    )
                }
            }
        }
    }
}
