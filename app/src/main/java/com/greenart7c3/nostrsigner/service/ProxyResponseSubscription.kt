package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.util.Log
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUsers
import com.vitorpamplona.quartz.nip04Dm.crypto.EncryptedInfo
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Subscribes to NIP-46 response events sent by remote bunkers to any of our
 * proxy accounts. Each proxy account's local proxy pubkey gets a filter
 * `kinds=[24133], #p=[localProxyPub], authors=[remoteBunkerPub]` on the configured
 * relays. Incoming events are decrypted with the local proxy keypair and routed
 * to [RemoteBunkerClient.deliverResponse].
 *
 * Also supports transient subscriptions used during login (before an account
 * exists), see [subscribeForLocalKey] and [awaitInitialConnect].
 */
class ProxyResponseSubscription(
    val client: NostrClient,
    val appContext: Context,
) : RelayConnectionListener {
    private val subIds = mutableMapOf<String, String>()

    private val transientLogins = ConcurrentHashMap<String, TransientLogin>()
    private val pendingInitialConnect = ConcurrentHashMap<String, CompletableDeferred<String>>()

    /**
     * In-memory index of proxy accounts by their local proxy pubkey (hex), filled by
     * [updateFilter]. Keeps the Keystore-backed account loading out of the per-event
     * hot path; local proxy keys are freshly random per account, so the mapping is
     * collision-free.
     */
    private val proxyAccountsByLocalPub = ConcurrentHashMap<String, Account>()

    internal fun resolveForTaggedKey(localPubHex: String): Account? = proxyAccountsByLocalPub[localPubHex]

    private data class TransientLogin(
        val localPrivKey: ByteArray,
        val remotePubkey: String,
        val secret: String,
        val signer: NostrSignerInternal,
        val localPubHex: String,
    )

    init {
        client.addConnectionListener(this)
    }

    override suspend fun onIncomingMessage(relay: IRelayClient, msgStr: String, msg: Message) {
        if (msg is EventMessage) {
            if (subIds.containsValue(msg.subId)) {
                handleEvent(msg.event, relay.url.url)
            }
        }
        super.onIncomingMessage(relay, msgStr, msg)
    }

    private fun handleEvent(event: Event, relayUrl: String) {
        if (event.kind != NostrConnectEvent.KIND) return
        if (!event.verify()) return

        val taggedKey = event.taggedUsers().firstOrNull()?.pubKey ?: return

        Amber.instance.applicationIOScope.launch {
            transientLogins.values.firstOrNull { it.localPubHex == taggedKey }?.let { tl ->
                handleViaSigner(event, tl.signer, tl.remotePubkey, tl.secret, relayUrl, tl.localPubHex)
                return@launch
            }

            val account = resolveForTaggedKey(taggedKey) ?: return@launch
            val proxy = account.proxy ?: return@launch
            if (event.pubKey != proxy.remotePubkey) return@launch
            handleViaSigner(event, account.signer, proxy.remotePubkey, "", relayUrl, taggedKey)
        }
    }

    private suspend fun handleViaSigner(
        event: Event,
        signer: NostrSignerInternal,
        remotePubkey: String,
        secret: String,
        relayUrl: String,
        localPubHex: String,
    ) {
        val decrypted = try {
            val isNip04 = EncryptedInfo.isNIP04(event.content)
            if (isNip04) {
                signer.nip04Decrypt(event.content, event.pubKey)
            } else {
                signer.nip44Decrypt(event.content, event.pubKey)
            }
        } catch (e: Exception) {
            Log.w(Amber.TAG, "Proxy response decrypt failed on $relayUrl: ${e.message}", e)
            return
        }

        val response = try {
            JacksonMapper.mapper.readValue(decrypted, BunkerResponse::class.java)
        } catch (_: Exception) {
            null
        }

        if (response != null) {
            RemoteBunkerClient.deliverResponse(response)
            pendingInitialConnect[localPubHex]?.let { deferred ->
                val result = response.result
                if (response.error.isNullOrBlank() && (secret.isEmpty() || result == secret || result == "ack")) {
                    deferred.complete(event.pubKey)
                    pendingInitialConnect.remove(localPubHex)
                }
            }
            return
        }

        // Bunker-initiated `connect` request (nostrconnect:// generate flow).
        try {
            val node = JacksonMapper.mapper.readTree(decrypted)
            val method = node.get("method")?.asText() ?: return
            if (method == "connect") {
                pendingInitialConnect[localPubHex]?.let { deferred ->
                    deferred.complete(event.pubKey)
                    pendingInitialConnect.remove(localPubHex)
                }
            }
        } catch (_: Exception) {
            // ignored
        }
    }

    suspend fun subscribeForLocalKey(localPubHex: String, remotePubkey: String, relays: List<NormalizedRelayUrl>) {
        if (BuildFlavorChecker.isOfflineFlavor() || relays.isEmpty()) return
        val subKey = "transient_$localPubHex"
        if (!subIds.containsKey(subKey)) {
            subIds[subKey] = UUID.randomUUID().toString()
        }
        client.subscribe(
            subIds[subKey]!!,
            relays.associateWith {
                listOf(
                    Filter(
                        kinds = listOf(NostrConnectEvent.KIND),
                        tags = mapOf("p" to listOf(localPubHex)),
                        since = TimeUtils.now() - 60,
                    ),
                )
            },
        )
    }

    suspend fun awaitInitialConnect(
        localKeyPair: KeyPair,
        relays: List<NormalizedRelayUrl>,
        secret: String,
        timeoutMs: Long,
    ): String? {
        val localPub = localKeyPair.pubKey.toHexKey()
        val signer = NostrSignerInternal(localKeyPair)
        val deferred = CompletableDeferred<String>()
        pendingInitialConnect[localPub] = deferred
        transientLogins[localPub] = TransientLogin(
            localPrivKey = localKeyPair.privKey!!,
            remotePubkey = "",
            secret = secret,
            signer = signer,
            localPubHex = localPub,
        )
        subscribeForLocalKey(localPub, "", relays)
        return withTimeoutOrNull(timeoutMs) { deferred.await() }
    }

    fun registerTransientLogin(localPrivKey: ByteArray, remotePubkey: String) {
        val signer = NostrSignerInternal(KeyPair(privKey = localPrivKey))
        val localPubHex = signer.keyPair.pubKey.toHexKey()
        transientLogins[localPubHex] = TransientLogin(
            localPrivKey = localPrivKey,
            remotePubkey = remotePubkey,
            secret = "",
            signer = signer,
            localPubHex = localPubHex,
        )
    }

    fun clearTransient(localPubHex: String) {
        transientLogins.remove(localPubHex)
        pendingInitialConnect.remove(localPubHex)
        val subKey = "transient_$localPubHex"
        subIds.remove(subKey)?.let { client.unsubscribe(it) }
    }

    suspend fun updateFilter() {
        if (BuildFlavorChecker.isOfflineFlavor()) return
        val activeSubKeys = mutableSetOf<String>()
        activeSubKeys += subIds.keys.filter { it.startsWith("transient_") }
        val activeLocalPubs = mutableSetOf<String>()
        val since = TimeUtils.now()

        LocalPreferences.allAccounts(appContext).forEach { account ->
            val proxy = account.proxy ?: return@forEach
            if (proxy.relays.isEmpty()) return@forEach

            val localPub = account.signer.keyPair.pubKey.toHexKey()
            proxyAccountsByLocalPub[localPub] = account
            activeLocalPubs.add(localPub)
            val subKey = "proxy_${account.npub}"
            activeSubKeys.add(subKey)
            if (!subIds.containsKey(subKey)) {
                subIds[subKey] = UUID.randomUUID().toString()
            }
            client.subscribe(
                subIds[subKey]!!,
                proxy.relays.associateWith {
                    listOf(
                        Filter(
                            kinds = listOf(NostrConnectEvent.KIND),
                            tags = mapOf("p" to listOf(localPub)),
                            authors = listOf(proxy.remotePubkey),
                            since = since,
                        ),
                    )
                },
            )
        }

        val stale = subIds.keys.filter { it !in activeSubKeys }
        for (subKey in stale) {
            subIds.remove(subKey)?.let { client.unsubscribe(it) }
        }
        proxyAccountsByLocalPub.keys.retainAll(activeLocalPubs)
    }
}
