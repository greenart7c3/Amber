package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.util.Log
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
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
import com.vitorpamplona.quartz.nip19Bech32.toNpub
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

    private data class TransientLogin(
        val localPrivKey: ByteArray,
        val remotePubkey: String,
        val secret: String,
    )

    init {
        client.addConnectionListener(this)
    }

    override fun onIncomingMessage(relay: IRelayClient, msgStr: String, msg: Message) {
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

        val taggedKey = event.taggedUsers().firstOrNull() ?: return
        val taggedNpub = taggedKey.toNPub()

        Amber.instance.applicationIOScope.launch {
            transientLogins.values.firstOrNull { tl ->
                NostrSignerInternal(KeyPair(privKey = tl.localPrivKey)).keyPair.pubKey.toNpub() == taggedNpub
            }?.let { tl ->
                handleViaSigner(event, NostrSignerInternal(KeyPair(privKey = tl.localPrivKey)), tl.remotePubkey, tl.secret, relayUrl)
                return@launch
            }

            val accounts = LocalPreferences.allSavedAccounts(appContext)
            for (info in accounts) {
                val account = LocalPreferences.loadFromEncryptedStorage(appContext, info.npub) ?: continue
                val proxy = account.proxy ?: continue
                val localPub = account.signer.keyPair.pubKey.toNpub()
                if (localPub != taggedNpub) continue
                if (event.pubKey != proxy.remotePubkey) continue
                handleViaSigner(event, account.signer, proxy.remotePubkey, "", relayUrl)
                return@launch
            }
        }
    }

    private suspend fun handleViaSigner(
        event: Event,
        signer: NostrSignerInternal,
        remotePubkey: String,
        secret: String,
        relayUrl: String,
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
            val localPub = signer.keyPair.pubKey.toHexKey()
            pendingInitialConnect[localPub]?.let { deferred ->
                val result = response.result
                if (response.error.isNullOrBlank() && (secret.isEmpty() || result == secret || result == "ack")) {
                    deferred.complete(event.pubKey)
                    pendingInitialConnect.remove(localPub)
                }
            }
            return
        }

        // Bunker-initiated `connect` request (nostrconnect:// generate flow).
        try {
            val node = JacksonMapper.mapper.readTree(decrypted)
            val method = node.get("method")?.asText() ?: return
            if (method == "connect") {
                val localPub = signer.keyPair.pubKey.toHexKey()
                pendingInitialConnect[localPub]?.let { deferred ->
                    deferred.complete(event.pubKey)
                    pendingInitialConnect.remove(localPub)
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
        val deferred = CompletableDeferred<String>()
        pendingInitialConnect[localPub] = deferred
        transientLogins[localPub] = TransientLogin(
            localPrivKey = localKeyPair.privKey!!,
            remotePubkey = "",
            secret = secret,
        )
        subscribeForLocalKey(localPub, "", relays)
        return withTimeoutOrNull(timeoutMs) { deferred.await() }
    }

    fun registerTransientLogin(localPrivKey: ByteArray, remotePubkey: String) {
        val signer = NostrSignerInternal(KeyPair(privKey = localPrivKey))
        transientLogins[signer.keyPair.pubKey.toHexKey()] = TransientLogin(
            localPrivKey = localPrivKey,
            remotePubkey = remotePubkey,
            secret = "",
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
        val since = TimeUtils.now()

        LocalPreferences.allAccounts(appContext).forEach { account ->
            val proxy = account.proxy ?: return@forEach
            if (proxy.relays.isEmpty()) return@forEach

            val localPub = account.signer.keyPair.pubKey.toHexKey()
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
    }
}
