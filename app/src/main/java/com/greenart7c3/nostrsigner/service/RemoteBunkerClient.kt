package com.greenart7c3.nostrsigner.service

import android.util.Log
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip04Dm.crypto.EncryptedInfo
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Outbound NIP-46 client. When an [Account] is configured as a bunker proxy, every
 * sign / encrypt / decrypt call is forwarded to the remote bunker through this object.
 *
 * The local proxy keypair lives inside [Account.signer]. The remote user pubkey is
 * stored in [Account.proxy].remotePubkey; that's what apps see as `account.hexKey`.
 */
object RemoteBunkerClient {
    private const val TIMEOUT_MS = 30_000L

    private val pending = ConcurrentHashMap<String, CompletableDeferred<BunkerResponse>>()

    /**
     * Called by [ProxyResponseSubscription] (or by tests) when a response from the
     * remote bunker arrives.
     */
    fun deliverResponse(response: BunkerResponse) {
        val deferred = pending.remove(response.id) ?: return
        deferred.complete(response)
    }

    /**
     * Returns the response or null on timeout / network failure.
     */
    suspend fun request(
        account: Account,
        method: String,
        params: List<String>,
        timeoutMs: Long = TIMEOUT_MS,
    ): BunkerResponse? {
        val proxy = account.proxy ?: error("request() called on a non-proxy account")
        if (proxy.relays.isEmpty()) return null

        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<BunkerResponse>()
        pending[id] = deferred

        try {
            val payload = JacksonMapper.mapper.writeValueAsString(
                mapOf(
                    "id" to id,
                    "method" to method,
                    "params" to params,
                ),
            )

            val encrypted = try {
                account.signer.nip44Encrypt(payload, proxy.remotePubkey)
            } catch (e: Exception) {
                Log.w(Amber.TAG, "NIP-44 encrypt to bunker failed; falling back to NIP-04", e)
                account.signer.nip04Encrypt(payload, proxy.remotePubkey)
            }

            val event = account.signer.signerSync.sign<Event>(
                createdAt = TimeUtils.now(),
                kind = NostrConnectEvent.KIND,
                tags = arrayOf(arrayOf("p", proxy.remotePubkey)),
                content = encrypted,
            )

            Amber.instance.applicationIOScope.let {
                Amber.instance.getLogDatabase(account.npub).dao().insertLog(
                    LogEntity(
                        id = 0,
                        url = proxy.remotePubkey,
                        type = "bunker proxy request",
                        message = "method=$method id=$id",
                        time = System.currentTimeMillis(),
                    ),
                )
            }

            val published = Amber.instance.client.publishAndConfirm(
                event = event,
                relayList = proxy.relays.toSet(),
                timeoutInSeconds = 5,
            )

            if (!published) {
                Log.w(Amber.TAG, "Failed to publish bunker proxy request id=$id")
                Amber.instance.getLogDatabase(account.npub).dao().insertLog(
                    LogEntity(
                        id = 0,
                        url = proxy.remotePubkey,
                        type = "bunker proxy request",
                        message = "publish failed for $method id=$id",
                        time = System.currentTimeMillis(),
                    ),
                )
                return null
            }

            val response = withTimeoutOrNull(timeoutMs) { deferred.await() }
            if (response == null) {
                Log.w(Amber.TAG, "Bunker proxy request timed out id=$id method=$method")
                Amber.instance.getLogDatabase(account.npub).dao().insertLog(
                    LogEntity(
                        id = 0,
                        url = proxy.remotePubkey,
                        type = "bunker proxy request",
                        message = "timeout for $method id=$id",
                        time = System.currentTimeMillis(),
                    ),
                )
            }
            return response
        } finally {
            pending.remove(id)
        }
    }

    private fun BunkerResponse.requireResult(method: String): String {
        if (!error.isNullOrBlank()) {
            throw RemoteBunkerException("bunker error for $method: $error")
        }
        return result ?: throw RemoteBunkerException("bunker returned null result for $method")
    }

    suspend fun <T : Event> remoteSignEvent(account: Account, template: EventTemplate<T>): Event {
        val proxy = account.proxy ?: error("remoteSignEvent on non-proxy account")
        val unsignedJson = JacksonMapper.mapper.writeValueAsString(
            mapOf(
                "kind" to template.kind,
                "content" to template.content,
                "tags" to template.tags,
                "created_at" to template.createdAt,
                "pubkey" to proxy.remotePubkey,
            ),
        )
        val response = request(account, "sign_event", listOf(unsignedJson))
            ?: throw RemoteBunkerException("bunker timeout: sign_event")
        val signedJson = response.requireResult("sign_event")
        return Event.fromJson(signedJson)
    }

    suspend fun remoteSignEventSync(
        account: Account,
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): Event {
        val proxy = account.proxy ?: error("remoteSignEventSync on non-proxy account")
        val unsignedJson = JacksonMapper.mapper.writeValueAsString(
            mapOf(
                "kind" to kind,
                "content" to content,
                "tags" to tags,
                "created_at" to createdAt,
                "pubkey" to proxy.remotePubkey,
            ),
        )
        val response = request(account, "sign_event", listOf(unsignedJson))
            ?: throw RemoteBunkerException("bunker timeout: sign_event")
        val signedJson = response.requireResult("sign_event")
        return Event.fromJson(signedJson)
    }

    suspend fun remoteSignString(account: Account, message: String): String {
        val response = request(account, "sign_message", listOf(message))
            ?: throw RemoteBunkerException("bunker timeout: sign_message")
        return response.requireResult("sign_message")
    }

    suspend fun remoteEncrypt(
        account: Account,
        plainText: String,
        toPublicKey: String,
        useNip44: Boolean,
    ): String {
        val method = if (useNip44) "nip44_encrypt" else "nip04_encrypt"
        val response = request(account, method, listOf(toPublicKey, plainText))
            ?: throw RemoteBunkerException("bunker timeout: $method")
        return response.requireResult(method)
    }

    suspend fun remoteDecrypt(
        account: Account,
        cipherText: String,
        fromPublicKey: String,
        useNip44: Boolean,
    ): String {
        val method = if (useNip44) "nip44_decrypt" else "nip04_decrypt"
        val response = request(account, method, listOf(fromPublicKey, cipherText))
            ?: throw RemoteBunkerException("bunker timeout: $method")
        return response.requireResult(method)
    }

    suspend fun remoteDecryptAuto(account: Account, cipherText: String, fromPublicKey: String): String {
        val useNip44 = !EncryptedInfo.isNIP04(cipherText)
        return remoteDecrypt(account, cipherText, fromPublicKey, useNip44)
    }

    suspend fun remoteDecryptZapEvent(account: Account, eventJson: String): String {
        val response = request(account, "decrypt_zap_event", listOf(account.proxy!!.remotePubkey, eventJson))
            ?: throw RemoteBunkerException("bunker timeout: decrypt_zap_event")
        return response.requireResult("decrypt_zap_event")
    }

    suspend fun remoteGetPublicKey(account: Account): String {
        val response = request(account, "get_public_key", emptyList())
            ?: throw RemoteBunkerException("bunker timeout: get_public_key")
        return response.requireResult("get_public_key")
    }

    /**
     * Sends a `connect` request without using the [Account] (used during login,
     * before the account is created).
     *
     * @param localSigner signer holding the local proxy keypair
     * @param remotePubkey pubkey of the remote bunker
     * @param relays relays to publish to
     * @param secret optional secret value sent as the second param (per NIP-46 connect)
     * @param permissions optional perms string sent as the third param
     */
    suspend fun connect(
        localSigner: com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal,
        remotePubkey: String,
        relays: List<com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl>,
        secret: String,
        permissions: String,
        timeoutMs: Long = TIMEOUT_MS,
    ): BunkerResponse? {
        val params = buildList {
            add(remotePubkey)
            if (secret.isNotEmpty() || permissions.isNotEmpty()) add(secret)
            if (permissions.isNotEmpty()) add(permissions)
        }
        return rawRequest(localSigner, remotePubkey, relays, "connect", params, timeoutMs)
    }

    /**
     * Sends a `get_public_key` request without using an [Account].
     */
    suspend fun getPublicKey(
        localSigner: com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal,
        remotePubkey: String,
        relays: List<com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl>,
        timeoutMs: Long = TIMEOUT_MS,
    ): BunkerResponse? = rawRequest(localSigner, remotePubkey, relays, "get_public_key", emptyList(), timeoutMs)

    /**
     * Low-level helper used during login. Identical to [request] but does not require an [Account].
     */
    suspend fun rawRequest(
        localSigner: com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal,
        remotePubkey: String,
        relays: List<com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl>,
        method: String,
        params: List<String>,
        timeoutMs: Long = TIMEOUT_MS,
    ): BunkerResponse? {
        if (relays.isEmpty()) return null
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<BunkerResponse>()
        pending[id] = deferred
        try {
            val payload = JacksonMapper.mapper.writeValueAsString(
                mapOf(
                    "id" to id,
                    "method" to method,
                    "params" to params,
                ),
            )
            val encrypted = try {
                localSigner.nip44Encrypt(payload, remotePubkey)
            } catch (e: Exception) {
                Log.w(Amber.TAG, "NIP-44 encrypt to bunker failed (raw); falling back to NIP-04", e)
                localSigner.nip04Encrypt(payload, remotePubkey)
            }
            val event = localSigner.signerSync.sign<Event>(
                createdAt = TimeUtils.now(),
                kind = NostrConnectEvent.KIND,
                tags = arrayOf(arrayOf("p", remotePubkey)),
                content = encrypted,
            )
            val published = Amber.instance.client.publishAndConfirm(
                event = event,
                relayList = relays.toSet(),
                timeoutInSeconds = 5,
            )
            if (!published) return null
            return withTimeoutOrNull(timeoutMs) { deferred.await() }
        } finally {
            pending.remove(id)
        }
    }
}

class RemoteBunkerException(message: String) : RuntimeException(message)
