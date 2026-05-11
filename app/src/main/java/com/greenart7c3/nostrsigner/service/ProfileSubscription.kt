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
package com.greenart7c3.nostrsigner.service

import android.content.Context
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val EOSE_TIMEOUT_MS = 30_000L

class ProfileSubscription(
    val client: NostrClient,
    val appContext: Context,
    val scope: CoroutineScope,
) : RelayConnectionListener {
    private val subIds = mutableMapOf<String, String>()
    private val relaysPerSubId = mutableMapOf<String, MutableSet<NormalizedRelayUrl>>()
    private val timeoutJobs = mutableMapOf<String, Job>()

    init {
        // listens until the app crashes.
        client.addConnectionListener(this)
    }

    override fun onIncomingMessage(relay: IRelayClient, msgStr: String, msg: Message) {
        if (msg is EoseMessage) {
            val subId = msg.subId
            val relays = relaysPerSubId[subId]
            if (relays != null) {
                relays.remove(relay.url)
                if (relays.isEmpty()) {
                    timeoutJobs.remove(subId)?.cancel()
                    Amber.instance.intentionalDisconnectTime = System.currentTimeMillis()
                    client.unsubscribe(subId)
                    relaysPerSubId.remove(subId)
                }
            }

            scope.launch {
                val account = LocalPreferences.loadFromEncryptedStorage(appContext) ?: return@launch
                if (msg.subId == subIds[account.hexKey]) {
                    LocalPreferences.setLastCheck(Amber.instance, account.npub, TimeUtils.now())
                }
            }
        }
        if (msg is EventMessage) {
            if (this.subIds.containsValue(msg.subId)) {
                if (msg.event.kind == MetadataEvent.KIND && msg.event.verify()) {
                    val account = LocalPreferences.loadFromEncryptedStorageSync(appContext) ?: return
                    if (account.hexKey != msg.event.pubKey) return

                    (msg.event as MetadataEvent).contactMetaData()?.let { metadata ->
                        val npub = account.npub
                        var atLeastOne = false

                        metadata.name?.let { name ->
                            if (account.name.value != name) {
                                account.name.update { name }
                                atLeastOne = true
                            }
                        }

                        metadata.profilePicture()?.let { url ->
                            if (account.picture.value != url) {
                                account.picture.update { url }
                                atLeastOne = true
                            }
                        }

                        if (atLeastOne) {
                            scope.launch {
                                LocalPreferences.setLastMetadataUpdate(appContext, npub, TimeUtils.now())
                            }
                        }
                    }
                }
            }
        }
        super.onIncomingMessage(relay, msgStr, msg)
    }

    /**
     * Call this method every time the relay list or the user list changes
     */
    suspend fun updateFilter() {
        if (BuildFlavorChecker.isOfflineFlavor()) return
        val account = LocalPreferences.loadFromEncryptedStorage(appContext) ?: return

        subIds.keys.filter { it != account.hexKey }.forEach {
            val subId = subIds.remove(it)
            if (subId != null) {
                timeoutJobs.remove(subId)?.cancel()
                client.unsubscribe(subId)
                relaysPerSubId.remove(subId)
            }
        }

        if (!subIds.containsKey(account.hexKey)) {
            subIds[account.hexKey] = UUID.randomUUID().toString()
        }
        val lastMetaData = LocalPreferences.getLastMetadataUpdate(appContext, account.npub)
        val lastCheck = LocalPreferences.getLastCheck(appContext, account.npub)
        val oneDayAgo = TimeUtils.oneDayAgo()
        val fifteenMinutesAgo = TimeUtils.fifteenMinutesAgo()
        if ((lastMetaData == 0L || oneDayAgo > lastMetaData) && (lastCheck == 0L || fifteenMinutesAgo > lastCheck)) {
            val subId = subIds[account.hexKey]!!
            val profileFilter = createProfileFilter(account)
            relaysPerSubId[subId] = profileFilter.keys.toMutableSet()
            client.subscribe(subId, profileFilter)
            timeoutJobs[subId] = scope.launch {
                delay(EOSE_TIMEOUT_MS)
                if (relaysPerSubId.containsKey(subId)) {
                    Amber.instance.intentionalDisconnectTime = System.currentTimeMillis()
                    client.unsubscribe(subId)
                    relaysPerSubId.remove(subId)
                    timeoutJobs.remove(subId)
                }
            }
        }
    }

    /**
     * Call this function when you want to stop updates
     */
    fun closeSub() {
        Amber.instance.intentionalDisconnectTime = System.currentTimeMillis()
        subIds.values.forEach {
            timeoutJobs.remove(it)?.cancel()
            client.unsubscribe(it)
        }
        relaysPerSubId.clear()
        subIds.clear()
    }

    private fun createProfileFilter(account: Account): Map<NormalizedRelayUrl, List<Filter>> {
        val relays = LocalPreferences.loadSettingsFromEncryptedStorage().defaultProfileRelays
        val accounts = listOf(account.hexKey)
        return relays.associateWith {
            listOf(
                Filter(
                    kinds = listOf(MetadataEvent.KIND),
                    authors = accounts,
                    limit = accounts.size,
                ),
            )
        }
    }
}
