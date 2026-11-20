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

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.data.RelayClient
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ReportSender(
    // The Application context: it opens the nostr: link outside any activity.
    private val appContext: Context,
    private val relayClient: RelayClient,
    private val scope: CoroutineScope,
) {
    /**
     * Sends [report] to the developer. With [anonymous] it is signed with a
     * throwaway key instead of [account], so the developer does not see the
     * user's public key (and cannot reply).
     */
    fun send(
        account: Account?,
        report: String,
        // Puts the report on the clipboard, for the offline flavor (and no account) to paste it into a DM.
        copyToClipboard: suspend (String) -> Unit,
        anonymous: Boolean = false,
        onDone: () -> Unit,
    ) {
        if (report.isBlank()) {
            onDone()
            return
        }
        when {
            BuildFlavorChecker.isOfflineFlavor() -> sendOffline(report, copyToClipboard, onDone)
            // The sender's own copy would be wrapped to the throwaway key, which nobody keeps.
            anonymous -> sendOnline(report, onlyToDeveloper = true, onDone) {
                NIP17Factory().createMessageNIP17(it, NostrSignerInternal(KeyPair()))
            }
            account != null -> sendOnline(report, onlyToDeveloper = false, onDone, account::createMessageNIP17)
            else -> sendOffline(report, copyToClipboard, onDone)
        }
    }

    private fun sendOffline(
        report: String,
        copyToClipboard: suspend (String) -> Unit,
        onDone: () -> Unit,
    ) {
        scope.launch(Dispatchers.Main) {
            try {
                copyToClipboard(report)
                val intent = Intent(Intent.ACTION_VIEW)
                val npub = Hex.decode(Amber.DEVELOPER_HEX_KEY).toNpub()
                intent.data = "nostr:$npub".toUri()
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                appContext.startActivity(intent)
                onDone()
            } catch (_: Exception) {
                onDone()
            }
        }
    }

    private fun sendOnline(
        report: String,
        onlyToDeveloper: Boolean,
        onDone: () -> Unit,
        createMessage: suspend (EventTemplate<ChatMessageEvent>) -> NIP17Factory.Result,
    ) {
        scope.launch {
            val client = NostrClient(relayClient.factory, scope)
            client.connect()

            val template = ChatMessageEvent.build(
                msg = report,
                to = listOf(PTag(Amber.DEVELOPER_HEX_KEY)),
                createdAt = System.currentTimeMillis() / 1000,
            ) {
                val thirtyDaysInSeconds = 30L * 86_400
                expiration(TimeUtils.now() + thirtyDaysInSeconds)
            }
            val signedEvents = createMessage(template)
            signedEvents.wraps.filter { !onlyToDeveloper || it.recipientPubKey() == Amber.DEVELOPER_HEX_KEY }.forEach { wrap ->
                client.publish(
                    event = wrap,
                    relayList = setOf(
                        NormalizedRelayUrl(url = "wss://inbox.nostr.wine"),
                        NormalizedRelayUrl(url = "wss://nostr.land"),
                    ),
                )
            }
            onDone()
            delay(10.seconds)
            client.disconnect()
        }
    }
}
