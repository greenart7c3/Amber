package com.greenart7c3.nostrsigner.desktop.core

import com.vitorpamplona.quartz.nip01Core.core.Event
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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Desktop port of the Android `ProfileSubscription`: a throttled, one-shot
 * fetch of an account's profile. It first fetches the NIP-65 relay list
 * (kind 10002) and saves its write relays, then the metadata (kind 0) from
 * the default profile relays plus those user relays, and stores the name and
 * picture on the [AccountRecord].
 *
 * Runs on a throwaway relay client, so the indexer relays never join the
 * bunker client's pool (or its reconnect logic); sockets still follow the
 * Tor routing rule.
 */
object ProfileFetcher {
    private const val EOSE_TIMEOUT_MS = 30_000L

    /** Same as the Android default `ProfileFetchInterval.FIFTEEN_MINUTES`. */
    private const val FETCH_INTERVAL_SECONDS = 15 * 60L

    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /**
     * Fetches [npub]'s profile in the background unless it was refreshed
     * recently. Safe to call from every composable that shows the account.
     */
    fun refresh(npub: String) {
        val record = AccountsStore.get(npub) ?: return
        if (!shouldFetch(record, TimeUtils.now())) return
        if (!inFlight.add(npub)) return
        AmberDesktop.applicationIOScope.launch {
            try {
                fetch(npub)
            } finally {
                inFlight.remove(npub)
            }
        }
    }

    /** Mirrors the Android throttle: metadata older than a day and no check in the last interval. */
    fun shouldFetch(record: AccountRecord, now: Long): Boolean {
        val oneDayAgo = now - 24 * 60 * 60
        val intervalAgo = now - FETCH_INTERVAL_SECONDS
        return (record.lastMetadataUpdate == 0L || oneDayAgo > record.lastMetadataUpdate) &&
            (record.lastProfileCheck == 0L || intervalAgo > record.lastProfileCheck)
    }

    private suspend fun fetch(npub: String) {
        val hexKey = decodePublicKeyAsHexOrNull(npub) ?: return
        val client = AmberDesktop.newClient()
        try {
            client.connect()

            val relayList = queryNewest(client, profileRelays(npub), Filter(kinds = listOf(AdvertisedRelayListEvent.KIND), authors = listOf(hexKey), limit = 1))
            (relayList as? AdvertisedRelayListEvent)?.let { saveUserRelays(npub, it) }

            val metadata = queryNewest(client, profileRelays(npub), Filter(kinds = listOf(MetadataEvent.KIND), authors = listOf(hexKey), limit = 1))
            (metadata as? MetadataEvent)?.let { applyMetadata(npub, it) }

            AccountsStore.update(npub) { it.copy(lastProfileCheck = TimeUtils.now()) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AmberLogger.e("ProfileFetcher", "Profile fetch failed for $npub", e)
        } finally {
            runCatching { client.disconnect() }
        }
    }

    /** Default profile relays from the settings plus the account's own saved relay list. */
    private fun profileRelays(npub: String): Set<NormalizedRelayUrl> {
        val defaults = SettingsStore.settings.value.normalizedProfileRelays()
        val userRelays = AccountsStore.get(npub)?.userRelays.orEmpty().mapNotNull {
            RelayUrlNormalizer.normalizeOrNull(it)
        }
        return (defaults + userRelays).toSet()
    }

    /** Saves the newest kind-10002 write relays so later fetches also query them. */
    private fun saveUserRelays(npub: String, event: AdvertisedRelayListEvent) {
        val relays = event.writeRelaysNorm() ?: event.relaysNorm()
        if (relays.isEmpty()) return
        AccountsStore.update(npub) {
            if (event.createdAt <= it.userRelaysCreatedAt) {
                it
            } else {
                it.copy(userRelays = relays.map { relay -> relay.url }, userRelaysCreatedAt = event.createdAt)
            }
        }
    }

    private fun applyMetadata(npub: String, event: MetadataEvent) {
        val metadata = event.contactMetaData() ?: return
        val name = (metadata.name ?: metadata.displayName)?.trim()?.takeIf { it.isNotBlank() }
        val picture = metadata.profilePicture()?.trim()?.takeIf { it.isNotBlank() }
        AccountsStore.update(npub) {
            val updated = it.copy(name = name ?: it.name, picture = picture ?: it.picture)
            if (updated == it) it else updated.copy(lastMetadataUpdate = TimeUtils.now())
        }
        // Keep a loaded account (sidebar, settings) in step with the record.
        if (name != null) AmberDesktop.loadedAccount(npub)?.name?.value = name
    }

    /**
     * Subscribes [filter] on [relays] and returns the newest verified event
     * once every relay sent EOSE (or the timeout passed).
     */
    private suspend fun queryNewest(
        client: NostrClient,
        relays: Set<NormalizedRelayUrl>,
        filter: Filter,
    ): Event? {
        if (relays.isEmpty()) return null
        val subId = UUID.randomUUID().toString()
        val pending = ConcurrentHashMap.newKeySet<NormalizedRelayUrl>().apply { addAll(relays) }
        val done = CompletableDeferred<Unit>()
        var newest: Event? = null

        val listener = object : RelayConnectionListener {
            override suspend fun onIncomingMessage(relay: IRelayClient, msgStr: String, msg: Message) {
                when {
                    msg is EventMessage && msg.subId == subId -> {
                        val event = msg.event
                        if (event.verify()) {
                            synchronized(this) {
                                if (newest == null || event.createdAt > newest!!.createdAt) newest = event
                            }
                        }
                    }

                    msg is EoseMessage && msg.subId == subId -> {
                        pending.remove(relay.url)
                        if (pending.isEmpty()) done.complete(Unit)
                    }
                }
                super.onIncomingMessage(relay, msgStr, msg)
            }

            override fun onCannotConnect(relay: IRelayClient, errorMessage: String) {
                if (pending.remove(relay.url) && pending.isEmpty()) done.complete(Unit)
                super.onCannotConnect(relay, errorMessage)
            }
        }

        client.addConnectionListener(listener)
        try {
            client.subscribe(subId, relays.associateWith { listOf(filter) })
            withTimeoutOrNull(EOSE_TIMEOUT_MS) { done.await() }
        } finally {
            runCatching { client.unsubscribe(subId) }
            client.removeConnectionListener(listener)
        }
        return synchronized(listener) { newest }
    }
}
