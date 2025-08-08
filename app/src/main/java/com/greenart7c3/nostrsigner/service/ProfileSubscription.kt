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
import com.greenart7c3.nostrsigner.AccountInfo
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProfileSubscription(
    val client: NostrClient,
    val appContext: Context,
    val scope: CoroutineScope,
) : IRelayClientListener {
    private val subId = UUID.randomUUID().toString()
    private val monitoringAccounts: List<AccountInfo> = emptyList()

    init {
        // listens until the app crashes.
        client.subscribe(this)
    }

    override fun onEOSE(relay: IRelayClient, subId: String, arrivalTime: Long) {
        monitoringAccounts.forEach {
            LocalPreferences.setLastCheck(Amber.instance, it.npub, TimeUtils.now())
        }
    }

    override fun onEvent(relay: IRelayClient, subId: String, event: Event, arrivalTime: Long, afterEOSE: Boolean) {
        if (this.subId == subId) {
            if (event is MetadataEvent) {
                event.contactMetaData()?.let { metadata ->
                    val npub = event.pubKey.hexToByteArray().toNpub()
                    val account = LocalPreferences.loadFromEncryptedStorageSync(appContext, npub)
                    if (account == null) return

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
                            LocalPreferences.saveToEncryptedStorage(appContext, account)
                            LocalPreferences.setLastMetadataUpdate(appContext, npub, TimeUtils.now())
                        }
                    }
                }
            }
        }
    }

    /**
     * Call this method every time the relay list or the user list changes
     */
    fun updateFilter() {
        client.sendRequest(subId, createProfileFilter())
    }

    /**
     * Call this function when you want to stop updates
     */
    fun closeSub() {
        client.close(subId)
    }

    private fun createProfileFilter(): Map<NormalizedRelayUrl, List<Filter>> {
        val relays = LocalPreferences.loadSettingsFromEncryptedStorage().defaultProfileRelays
        val monitoringAccounts = LocalPreferences.allSavedAccounts(appContext)
        val accounts = monitoringAccounts.map { it.npub.bechToBytes().toHexKey() }
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
