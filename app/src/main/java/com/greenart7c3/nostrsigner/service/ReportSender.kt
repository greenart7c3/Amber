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
package com.greenart7c3.nostrsigner.service

import android.content.ClipData
import android.content.Intent
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.core.net.toUri
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ReportSender {
    fun send(
        account: Account?,
        report: String,
        clipboard: Clipboard,
        onDone: () -> Unit,
    ) {
        if (report.isBlank()) {
            onDone()
            return
        }
        val useOffline = BuildFlavorChecker.isOfflineFlavor() || account == null
        if (useOffline) {
            sendOffline(report, clipboard, onDone)
        } else {
            sendOnline(account, report, onDone)
        }
    }

    private fun sendOffline(
        report: String,
        clipboard: Clipboard,
        onDone: () -> Unit,
    ) {
        Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
            try {
                clipboard.setClipEntry(
                    ClipEntry(ClipData.newPlainText("", report)),
                )
                val intent = Intent(Intent.ACTION_VIEW)
                val npub = Hex.decode(Amber.DEVELOPER_HEX_KEY).toNpub()
                intent.data = "nostr:$npub".toUri()
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                Amber.instance.startActivity(intent)
                onDone()
            } catch (_: Exception) {
                onDone()
            }
        }
    }

    private fun sendOnline(
        account: Account,
        report: String,
        onDone: () -> Unit,
    ) {
        Amber.instance.applicationIOScope.launch {
            val client = NostrClient(Amber.instance.factory, Amber.instance.applicationIOScope)
            client.connect()

            val template = ChatMessageEvent.build(
                msg = report,
                to = listOf(PTag(Amber.DEVELOPER_HEX_KEY)),
                createdAt = System.currentTimeMillis() / 1000,
            ) {
                val thirtyDaysInSeconds = 30L * 86_400
                expiration(TimeUtils.now() + thirtyDaysInSeconds)
            }
            val signedEvents = account.createMessageNIP17(template)
            signedEvents.wraps.forEach { wrap ->
                client.publish(
                    event = wrap,
                    relayList = setOf(
                        NormalizedRelayUrl(url = "wss://inbox.nostr.wine"),
                        NormalizedRelayUrl(url = "wss://nostr.land"),
                    ),
                )
            }
            onDone()
            delay(10000)
            client.disconnect()
        }
    }
}
