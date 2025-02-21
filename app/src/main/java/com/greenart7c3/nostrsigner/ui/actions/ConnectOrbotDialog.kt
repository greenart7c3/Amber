

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.theme.RichTextDefaults
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.commonmark.MarkdownParseOptions
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.ui.material3.RichText
import kotlinx.coroutines.CancellationException

@Composable
fun ConnectOrbotScreen(
    modifier: Modifier,
    account: Account,
    onPost: (Int) -> Unit,
    onError: (String) -> Unit,
) {
    val portNumber = remember { mutableStateOf(account.proxyPort.toString()) }

    Column(
        modifier = modifier,
    ) {
        val myMarkDownStyle =
            RichTextDefaults.copy(
                stringStyle = RichTextDefaults.stringStyle?.copy(
                    linkStyle =
                    SpanStyle(
                        textDecoration = TextDecoration.Underline,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                ),
            )

        Row {
            val content1 = stringResource(R.string.connect_through_your_orbot_setup_markdown)

            val astNode1 =
                remember {
                    CommonmarkAstNodeParser(MarkdownParseOptions.MarkdownWithLinks).parse(content1)
                }

            RichText(
                style = myMarkDownStyle,
                renderer = null,
            ) {
                BasicMarkdown(astNode1)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = portNumber.value,
            onValueChange = { portNumber.value = it },
            keyboardOptions = KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Number,
            ),
            label = { Text(text = stringResource(R.string.orbot_socks_port)) },
            placeholder = {
                Text(
                    text = "9050",
                )
            },
        )

        val toastMessage = stringResource(R.string.invalid_port_number)

        AmberButton(
            modifier = Modifier.padding(top = 20.dp),
            onClick = {
                try {
                    Integer.parseInt(portNumber.value)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    onError(toastMessage)
                    return@AmberButton
                }

                onPost(portNumber.value.toInt())
            },
            text = stringResource(R.string.use_orbot),
        )
    }
}
