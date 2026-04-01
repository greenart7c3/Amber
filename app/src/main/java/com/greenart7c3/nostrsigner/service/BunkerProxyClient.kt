package com.greenart7c3.nostrsigner.service

import android.util.Log
import com.fasterxml.jackson.databind.node.ObjectNode
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.BunkerProxy
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.sendAndWaitForResponse
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles outbound NIP-46 communication when Amber is acting as a bunker proxy.
 *
 * When a signing/encryption request comes in for a proxy account, instead of
 * processing it locally, Amber forwards it to the real remote bunker and relays
 * the response back to the original client application.
 *
 * Incoming responses from the remote bunker are routed here from
 * [NotificationSubscription] via [tryHandleResponse].
 */
object BunkerProxyClient {
    private const val TAG = "BunkerProxyClient"
    private const val REQUEST_TIMEOUT_MS = 30_000L

    /** Pending outbound requests: NIP-46 request ID → awaiting deferred. */
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<BunkerResponse?>>()

    /**
     * Called from [NotificationSubscription] for every incoming kind-24133 event.
     * Returns true if the event was identified as a response from a remote bunker and
     * consumed here; false if it should be processed normally as an incoming request.
     */
    fun tryHandleResponse(event: Event): Boolean {
        if (pendingRequests.isEmpty()) return false

        val proxyAccounts = LocalPreferences.allCachedAccounts().filter { it.bunkerProxy != null }
        if (proxyAccounts.isEmpty()) return false

        // The event's pubKey must match a known remote bunker pubkey
        val matchingAccount = proxyAccounts.firstOrNull { acc ->
            acc.bunkerProxy!!.remotePubKey == event.pubKey
        } ?: return false

        val clientPubKey = matchingAccount.signer.keyPair.pubKey.toHexKey()
        // Event must be tagged with our client pubkey
        if (event.tags.none { it.size >= 2 && it[0] == "p" && it[1] == clientPubKey }) return false

        Amber.instance.applicationIOScope.launch {
            try {
                val decrypted = matchingAccount.signer.decrypt(event.content, event.pubKey)
                val response = JacksonMapper.mapper.readValue(decrypted, BunkerResponse::class.java)
                Log.d(TAG, "Proxy response id=${response.id} result=${response.result} error=${response.error}")
                pendingRequests[response.id]?.complete(response)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle proxy response", e)
            }
        }
        return true
    }

    /**
     * Forwards a NIP-46 [method] request with [params] to the remote bunker for the given
     * proxy [account]. Returns the bunker's result string, or null on failure/timeout.
     */
    suspend fun sendRequest(
        account: Account,
        method: String,
        params: List<String>,
    ): String? {
        val proxy = account.bunkerProxy ?: return null
        return sendRequestDirect(account.signer, proxy, method, params)
    }

    /**
     * Initiates the NIP-46 connection handshake with a remote bunker:
     * sends `connect` followed by `get_public_key` and returns the user's hex pubkey,
     * or null on failure.
     */
    suspend fun connect(
        clientSigner: NostrSignerInternal,
        proxy: BunkerProxy,
    ): String? {
        val clientPubKey = clientSigner.keyPair.pubKey.toHexKey()

        Log.d(TAG, "Connecting to remote bunker ${proxy.remotePubKey} via ${proxy.relays.map { it.url }}")
        val connectResult = sendRequestDirect(
            signer = clientSigner,
            proxy = proxy,
            method = "connect",
            params = listOf(clientPubKey, proxy.secret, ""),
        ) ?: run {
            Log.w(TAG, "connect request failed or timed out")
            return null
        }

        Log.d(TAG, "connect result: $connectResult")

        val userPubKey = sendRequestDirect(
            signer = clientSigner,
            proxy = proxy,
            method = "get_public_key",
            params = emptyList(),
        ) ?: run {
            Log.w(TAG, "get_public_key failed or timed out")
            return null
        }

        Log.d(TAG, "remote user pubkey: $userPubKey")
        return userPubKey.trim()
    }

    private suspend fun sendRequestDirect(
        signer: NostrSignerInternal,
        proxy: BunkerProxy,
        method: String,
        params: List<String>,
    ): String? {
        val requestId = UUID.randomUUID().toString()
        val plainText = buildRequestJson(requestId, method, params)

        val encryptedContent = signer.nip44Encrypt(plainText, proxy.remotePubKey)
        val signedEvent = signer.signerSync.sign<Event>(
            TimeUtils.now(),
            NostrConnectEvent.KIND,
            arrayOf(arrayOf("p", proxy.remotePubKey)),
            encryptedContent,
        )

        val deferred = CompletableDeferred<BunkerResponse?>()
        pendingRequests[requestId] = deferred

        return try {
            Log.d(TAG, "Sending $method (id=$requestId) to ${proxy.remotePubKey}")
            val sent = Amber.instance.client.sendAndWaitForResponse(
                event = signedEvent,
                relayList = proxy.relays.toSet(),
                timeoutInSeconds = 5,
            )
            if (!sent) {
                Log.w(TAG, "Failed to publish proxy request $requestId")
                return null
            }

            val response = withTimeoutOrNull(REQUEST_TIMEOUT_MS) { deferred.await() }
            if (response == null) {
                Log.w(TAG, "Proxy request $requestId ($method) timed out")
                return null
            }
            if (response.error != null) {
                Log.w(TAG, "Remote bunker error for $requestId: ${response.error}")
                return null
            }
            response.result
        } finally {
            pendingRequests.remove(requestId)
        }
    }

    private fun buildRequestJson(id: String, method: String, params: List<String>): String {
        val node = JacksonMapper.mapper.createObjectNode() as ObjectNode
        node.put("id", id)
        node.put("method", method)
        val paramsArray = JacksonMapper.mapper.createArrayNode()
        params.forEach { paramsArray.add(it) }
        node.set<ObjectNode>("params", paramsArray)
        return JacksonMapper.mapper.writeValueAsString(node)
    }
}
