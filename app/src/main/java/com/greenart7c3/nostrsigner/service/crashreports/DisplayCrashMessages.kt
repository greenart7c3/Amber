/**
 * Copyright (c) 2025 Vitor Pamplona
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
package com.greenart7c3.nostrsigner.service.crashreports

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DisplayCrashMessages(
    account: Account,
) {
    val stackTrace = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(account) {
        withContext(Dispatchers.IO) {
            stackTrace.value = Amber.instance.crashReportCache.loadAndDelete()
        }
    }

    stackTrace.value?.let { stack ->
        AlertDialog(
            onDismissRequest = { stackTrace.value = null },
            title = { Text(stringResource(R.string.crashreport_found)) },
            text = {
                SelectionContainer {
                    Text(stringResource(R.string.would_you_like_to_send_the_recent_crash_report_to_amber_in_a_dm_no_personal_information_will_be_shared))
                }
            },
            dismissButton = {
                TextButton(onClick = { stackTrace.value = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        Amber.instance.applicationIOScope.launch {
                            val client = NostrClient(Amber.instance.factory, Amber.instance.applicationIOScope)
                            client.connect()

                            val template = ChatMessageEvent.build(
                                msg = stack,
                                to = listOf(PTag("7579076d9aff0a4cfdefa7e2045f2486c7e5d8bc63bfc6b45397233e1bbfcb19")),
                                createdAt = System.currentTimeMillis() / 1000,
                            ) {
                                val thirtyDaysInSeconds = 30L * 86_400
                                expiration(TimeUtils.now() + thirtyDaysInSeconds)
                            }
                            account.signer.signerSync.sign(template)
                            val signedEvents = NIP17Factory().createMessageNIP17(template, account.signer)
                            signedEvents.wraps.forEach { wrap ->
                                client.send(
                                    event = wrap,
                                    relayList = setOf(
                                        NormalizedRelayUrl(url = "wss://inbox.nostr.wine"),
                                        NormalizedRelayUrl(url = "wss://nostr.land"),
                                    ),
                                )
                            }
                            stackTrace.value = null
                            delay(10000)
                            client.disconnect()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Done,
                            tint = Color.Black,
                            contentDescription = stringResource(R.string.crashreport_found_send),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(R.string.crashreport_found_send), color = Color.Black)
                    }
                }
            },
        )
    }
}
