

/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.greenart7c3.nostrsigner.ui.actions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.builtin_tor_active
import com.greenart7c3.nostrsigner.commonsui.builtin_tor_description
import com.greenart7c3.nostrsigner.commonsui.builtin_tor_title
import com.greenart7c3.nostrsigner.commonsui.connect_through_your_orbot_setup_markdown
import com.greenart7c3.nostrsigner.commonsui.invalid_port_number
import com.greenart7c3.nostrsigner.commonsui.orbot_socks_port
import com.greenart7c3.nostrsigner.commonsui.orbot_title
import com.greenart7c3.nostrsigner.commonsui.use_builtin_tor
import com.greenart7c3.nostrsigner.commonsui.use_orbot
import com.greenart7c3.nostrsigner.models.TorMode
import com.greenart7c3.nostrsigner.ui.LocalSettingsStore
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.MarkdownText
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource

@Composable
fun ConnectOrbotScreen(
    onPost: (Int) -> Unit,
    onBuiltinTor: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsStore = LocalSettingsStore.current
    val portNumber = remember { mutableStateOf(settingsStore.settings.proxyPort.toString()) }

    Column(
        modifier = modifier,
    ) {
        BuiltinTorSection(
            isBuiltinTorActive = settingsStore.settings.torMode == TorMode.BUILTIN,
            onBuiltinTor = onBuiltinTor,
        )

        Spacer(modifier = Modifier.height(24.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(24.dp))

        val toastMessage = stringResource(Res.string.invalid_port_number)

        OrbotSection(
            portNumber = portNumber.value,
            onPortNumberChange = { portNumber.value = it },
            onUseOrbot = useOrbot@{
                try {
                    Integer.parseInt(portNumber.value)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    onError(toastMessage)
                    return@useOrbot
                }

                onPost(portNumber.value.toInt())
            },
        )
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

@Composable
private fun ColumnScope.BuiltinTorSection(
    isBuiltinTorActive: Boolean,
    onBuiltinTor: () -> Unit,
) {
    SectionTitle(stringResource(Res.string.builtin_tor_title))

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = stringResource(Res.string.builtin_tor_description),
        style = MaterialTheme.typography.bodyMedium,
    )

    Spacer(modifier = Modifier.height(8.dp))

    AmberButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onBuiltinTor,
        text = if (isBuiltinTorActive) {
            stringResource(Res.string.builtin_tor_active)
        } else {
            stringResource(Res.string.use_builtin_tor)
        },
        enabled = !isBuiltinTorActive,
    )
}

@Composable
private fun ColumnScope.OrbotSection(
    portNumber: String,
    onPortNumberChange: (String) -> Unit,
    onUseOrbot: () -> Unit,
) {
    SectionTitle(stringResource(Res.string.orbot_title))

    Spacer(modifier = Modifier.height(8.dp))

    val content1 = stringResource(Res.string.connect_through_your_orbot_setup_markdown)
    MarkdownText(content1)

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = portNumber,
        onValueChange = onPortNumberChange,
        keyboardOptions = KeyboardOptions.Default.copy(
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Number,
        ),
        label = { Text(text = stringResource(Res.string.orbot_socks_port)) },
        placeholder = {
            Text(
                text = "9050",
            )
        },
    )

    AmberButton(
        modifier = Modifier.padding(top = 20.dp),
        onClick = onUseOrbot,
        text = stringResource(Res.string.use_orbot),
    )
}
