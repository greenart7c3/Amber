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
import com.greenart7c3.nostrsigner.models.ProfileFetchInterval
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
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
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
    // hexKey -> subId of the kind-0 metadata subscription
    private val subIds = mutableMapOf<String, String>()

    // hexKey -> subId of the kind-10002 relay list subscription that runs before the metadata one
    private val relayListSubIds = mutableMapOf<String, String>()
    private val relaysPerSubId = mutableMapOf<String, MutableSet<NormalizedRelayUrl>>()
    private val timeoutJobs = mutableMapOf<String, Job>()

    // hexKey -> the cached Account instance a live composable cares about. Updating the
    // StateFlows on these instances is what reflects fresh metadata in the UI.
    private val accounts = mutableMapOf<String, Account>()

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
                    unsubscribe(subId)
                    onRelayListSubFinished(subId)
                }
            }

            val hexKey = subIds.entries.firstOrNull { it.value == subId }?.key
            val account = hexKey?.let { accounts[it] }
            if (account != null) {
                scope.launch {
                    LocalPreferences.setLastCheck(Amber.instance, account.npub, TimeUtils.now())
                }
            }
        }
        if (msg is EventMessage) {
            if (this.relayListSubIds.containsValue(msg.subId)) {
                if (msg.event.kind == AdvertisedRelayListEvent.KIND && msg.event.verify()) {
                    (msg.event as? AdvertisedRelayListEvent)?.let { saveUserRelays(it) }
                }
            }
            if (this.subIds.containsValue(msg.subId)) {
                if (msg.event.kind == MetadataEvent.KIND && msg.event.verify()) {
                    val account = accounts[msg.event.pubKey] ?: return

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
     * Starts (or refreshes) the throttled, one-shot metadata fetch for [account].
     * First fetches the user's NIP-65 relay list (kind 10002), saves it locally, then
     * fetches the metadata from the default profile relays plus the user's own relays.
     * Tracks the account so incoming events update its StateFlows; safe to call from
     * any composable displaying the account.
     */
    suspend fun updateFilter(account: Account) {
        if (BuildFlavorChecker.isOfflineFlavor()) return

        val interval = Amber.instance.settings.profileFetchInterval
        if (interval == ProfileFetchInterval.NEVER) return

        accounts[account.hexKey] = account

        val shouldFetch = if (interval == ProfileFetchInterval.ALWAYS) {
            true
        } else {
            val lastMetaData = LocalPreferences.getLastMetadataUpdate(appContext, account.npub)
            val lastCheck = LocalPreferences.getLastCheck(appContext, account.npub)
            val oneDayAgo = TimeUtils.oneDayAgo()
            val fetchIntervalAgo = TimeUtils.now() - (interval.minutes * 60)
            (lastMetaData == 0L || oneDayAgo > lastMetaData) && (lastCheck == 0L || fetchIntervalAgo > lastCheck)
        }
        if (shouldFetch) {
            subscribeToUserRelayList(account)
        }
    }

    private fun subscribeToUserRelayList(account: Account) {
        val subId = relayListSubIds.getOrPut(account.hexKey) { UUID.randomUUID().toString() }
        val relayListFilter = createRelayListFilter(account)
        timeoutJobs.remove(subId)?.cancel()
        relaysPerSubId[subId] = relayListFilter.keys.toMutableSet()
        client.subscribe(subId, relayListFilter)
        timeoutJobs[subId] = scope.launch {
            delay(EOSE_TIMEOUT_MS)
            if (relaysPerSubId.containsKey(subId)) {
                unsubscribe(subId)
                // still fetch the profile with whatever relay list we have saved
                onRelayListSubFinished(subId)
            }
        }
    }

    private fun subscribeToProfile(account: Account) {
        val subId = subIds.getOrPut(account.hexKey) { UUID.randomUUID().toString() }
        val profileFilter = createProfileFilter(account)
        timeoutJobs.remove(subId)?.cancel()
        relaysPerSubId[subId] = profileFilter.keys.toMutableSet()
        client.subscribe(subId, profileFilter)
        timeoutJobs[subId] = scope.launch {
            delay(EOSE_TIMEOUT_MS)
            if (relaysPerSubId.containsKey(subId)) {
                unsubscribe(subId)
            }
        }
    }

    /**
     * Called when a relay list subscription completes (all relays sent EOSE or the timeout
     * fired). Starts the metadata fetch for the account using the just-saved relay list.
     * No-op for metadata subscription ids.
     */
    private fun onRelayListSubFinished(subId: String) {
        val hexKey = relayListSubIds.entries.firstOrNull { it.value == subId }?.key ?: return
        relayListSubIds.remove(hexKey)
        accounts[hexKey]?.let { subscribeToProfile(it) }
    }

    /**
     * Saves the newest kind-10002 write relay list locally so profile fetches can also
     * query the user's own relays.
     */
    private fun saveUserRelays(event: AdvertisedRelayListEvent) {
        val account = accounts[event.pubKey] ?: return
        val relays = event.writeRelaysNorm() ?: event.relaysNorm()
        if (relays.isEmpty()) return
        if (event.createdAt <= LocalPreferences.getUserRelaysCreatedAt(appContext, account.npub)) return
        LocalPreferences.setUserRelays(appContext, account.npub, relays, event.createdAt)
    }

    private fun unsubscribe(subId: String) {
        timeoutJobs.remove(subId)?.cancel()
        Amber.instance.intentionalDisconnectTime = System.currentTimeMillis()
        client.unsubscribe(subId)
        relaysPerSubId.remove(subId)
    }

    /**
     * Re-runs the fetch for every currently tracked account. Call when the relay list changes.
     */
    suspend fun updateFilters() {
        accounts.values.toList().forEach { updateFilter(it) }
    }

    /**
     * Stops updates for a single [account] (call when the composable leaves composition).
     */
    fun closeSub(account: Account) {
        accounts.remove(account.hexKey)
        relayListSubIds.remove(account.hexKey)?.let { unsubscribe(it) }
        subIds.remove(account.hexKey)?.let { unsubscribe(it) }
    }

    /**
     * Stops updates for all accounts (e.g. after a backup restore).
     */
    fun closeSub() {
        Amber.instance.intentionalDisconnectTime = System.currentTimeMillis()
        (subIds.values + relayListSubIds.values).forEach {
            timeoutJobs.remove(it)?.cancel()
            client.unsubscribe(it)
        }
        relaysPerSubId.clear()
        subIds.clear()
        relayListSubIds.clear()
        accounts.clear()
    }

    /** Default profile relays from the settings plus the user's own saved relay list. */
    private fun profileRelays(account: Account): Set<NormalizedRelayUrl> {
        val defaultRelays = LocalPreferences.loadSettingsFromEncryptedStorage().defaultProfileRelays
        val userRelays = LocalPreferences.getUserRelays(appContext, account.npub)
        return (defaultRelays + userRelays).toSet()
    }

    private fun createRelayListFilter(account: Account): Map<NormalizedRelayUrl, List<Filter>> = profileRelays(account).associateWith {
        listOf(
            Filter(
                kinds = listOf(AdvertisedRelayListEvent.KIND),
                authors = listOf(account.hexKey),
                limit = 1,
            ),
        )
    }

    private fun createProfileFilter(account: Account): Map<NormalizedRelayUrl, List<Filter>> = profileRelays(account).associateWith {
        listOf(
            Filter(
                kinds = listOf(MetadataEvent.KIND),
                authors = listOf(account.hexKey),
                limit = 1,
            ),
        )
    }
}
