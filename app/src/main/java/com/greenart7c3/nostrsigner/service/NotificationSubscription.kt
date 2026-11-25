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
import androidx.collection.LruCache
import com.greenart7c3.nostrsigner.AccountStorage
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.common.bunkerFilters
import com.greenart7c3.nostrsigner.common.relays.RelayHealthTracker
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// The one subscription every bunker endpoint shares, keyed in subIds.
private const val BUNKER_SUB_KEY = "bunker"

// Constructor injection: one parameter per dependency.
@Suppress("LongParameterList")
class NotificationSubscription(
    val client: NostrClient,
    val appContext: Context,
    settingsStore: SettingsStore,
    private val accountRepository: AccountRepository,
    private val scope: CoroutineScope,
    private val notificationCache: LruCache<String, Long>,
    bunkerResponder: BunkerResponder,
) : RelayConnectionListener {
    private val eventNotificationConsumer =
        EventNotificationConsumer(appContext, settingsStore, accountRepository, scope, bunkerResponder) { updateFilter() }

    // Concurrent: mutated in updateFilter/closeAllSubs (coroutines) while relay I/O
    // threads iterate it in onIncomingMessage (containsValue).
    private val subIds = ConcurrentHashMap<String, String>()
    private val refreshMutex = Mutex()
    private var closeGeneration = 0L

    init {
        // listens until the app crashes.
        client.addConnectionListener(this)
    }

    override suspend fun onIncomingMessage(relay: IRelayClient, msgStr: String, msg: Message) {
        if (msg is EventMessage) {
            if (subIds.containsValue(msg.subId)) {
                scope.launch {
                    eventNotificationConsumer.consume(msg.event, relay.url)
                }
            }
        }
        super.onIncomingMessage(relay, msgStr, msg)
    }

    override fun onSent(relay: IRelayClient, cmdStr: String, cmd: Command, success: Boolean) {
        AmberLog.d("NotificationSubscription", "onSend: ${relay.url}, $cmdStr, $success")
        super.onSent(relay, cmdStr, cmd, success)
    }

    /**
     * Call this method every time the relay list or the user list changes.
     *
     * Every endpoint shares one subscription: each relay gets a single REQ whose `#p` list
     * holds the pubkey of every connection (with its own localKey) that uses that relay, plus
     * the account pubkey when the account still has legacy connections without a localKey.
     * Relays cap REQs per socket (strfry: 20), so one REQ per connection left the newest
     * ones deaf (#644). Requests are routed to their account by the tagged key.
     */
    suspend fun updateFilter() {
        // Calls queued before a close belong to the old lifecycle too.
        val generation = synchronized(subIds) { closeGeneration }
        refreshMutex.withLock {
            if (BuildFlavorChecker.isOfflineFlavor()) return@withLock
            if (generation != synchronized(subIds) { closeGeneration }) return@withLock
            val keysByRelay = linkedMapOf<NormalizedRelayUrl, MutableSet<String>>()
            val indexEntries = mutableMapOf<String, LocalKeyAccountIndex.Match>()
            val since = computeSince()

            AccountStorage.all(appContext).forEach { account ->
                // Cached dao: getAll() is served from CachingApplicationDao's per-account
                // LRU. updateFilter re-runs on every relay refresh (explicit refreshes
                // plus the periodic ConnectivityService safety net), and an uncached
                // getAll re-decrypts every application row through AndroidKeyStore
                // (a keystore2 binder round-trip per encrypted field) each cycle —
                // previously the app's dominant native allocator.
                val allConnections = accountRepository.dao(account.npub).getAll(account.hexKey)
                val connectionsWithLocalKey = allConnections.filter { it.localKey.isNotEmpty() }
                val hasLegacyConnections = allConnections.any { it.localKey.isEmpty() && it.relays.isNotEmpty() }

                // Each connection listens on its own relays
                for (conn in connectionsWithLocalKey) {
                    val connPubKey = conn.localPubKey
                    indexEntries[connPubKey] = LocalKeyAccountIndex.Match(account.npub, conn.localKey)

                    // Exclude relays that have been declared dead so Quartz stops opening
                    // a socket to them on every refresh. They are re-added once
                    // RelayHealthTracker is reset (network change / manual reconnect) or
                    // they connect successfully again.
                    val connRelays = conn.relays.ifEmpty { accountRepository.getSavedRelays(account) }
                        .filterNot { RelayHealthTracker.isDead(it) }
                    connRelays.forEach { keysByRelay.getOrPut(it) { linkedSetOf() }.add(connPubKey) }
                }

                // The account key itself only listens for legacy connections (no localKey)
                if (hasLegacyConnections) {
                    accountRepository.getSavedRelays(account)
                        .filterNot { RelayHealthTracker.isDead(it) }
                        .forEach { keysByRelay.getOrPut(it) { linkedSetOf() }.add(account.hexKey) }
                }
            }

            synchronized(subIds) {
                if (generation != closeGeneration) return@withLock
                // Make every endpoint routable before any subscription can deliver a request.
                LocalKeyAccountIndex.replaceAll(indexEntries)
                val activeSubKeys = if (keysByRelay.isEmpty()) emptySet() else setOf(BUNKER_SUB_KEY)
                if (keysByRelay.isNotEmpty()) {
                    val subId = subIds.getOrPut(BUNKER_SUB_KEY) { UUID.randomUUID().toString() }
                    client.subscribe(subId, bunkerFilters(keysByRelay, since))
                }

                // Unsubscribe once no application listens any more.
                val staleSubKeys = subIds.keys.filter { it !in activeSubKeys }
                for (subKey in staleSubKeys) {
                    subIds.remove(subKey)?.let { subId ->
                        client.unsubscribe(subId)
                    }
                }
            }
        }
    }

    fun closeAllSubs() {
        synchronized(subIds) {
            closeGeneration++
            subIds.values.forEach { client.unsubscribe(it) }
            subIds.clear()
            LocalKeyAccountIndex.replaceAll(emptyMap())
        }
    }

    private fun computeSince(): Long {
        val now = TimeUtils.now()
        val latest = if (notificationCache.size() > 0) notificationCache.snapshot().maxOf { it.value } else 0L
        // The cache holds sender-chosen created_at values recorded before any
        // authorization; never let one push `since` past the real clock, or
        // relays would drop every legitimate request until then.
        return if (latest > 0) minOf(latest, now) else now
    }
}
