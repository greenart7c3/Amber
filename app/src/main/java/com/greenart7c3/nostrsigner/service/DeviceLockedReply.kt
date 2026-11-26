package com.greenart7c3.nostrsigner.service

import androidx.collection.LruCache
import com.greenart7c3.nostrsigner.common.EncryptionType
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip04Dm.crypto.EncryptedInfo
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun Nip46RequestServices.replyDeviceLockedWithConnectionKeyOnly(
    notificationCache: LruCache<String, Long>,
    event: Event,
    acc: Account,
    relay: NormalizedRelayUrl,
    connectionPrivKey: String,
) {
    if (event.content.isEmpty()) return
    val alreadyAnsweredFromAnotherRelay = notificationCache[event.id] != null
    if (alreadyAnsweredFromAnotherRelay) return
    notificationCache.put(event.id, event.createdAt)
    scope.launch {
        try {
            val connSigner = NostrSignerInternal(KeyPair(privKey = connectionPrivKey.hexToByteArray()))
            val requestStr = connSigner.decrypt(event.content, event.pubKey)
            val bunkerRequest = JacksonMapper.mapper.readValue(requestStr, BunkerRequest::class.java)
            log("Device locked: rejected request ${bunkerRequest.id} method ${bunkerRequest.method}", relay.url, acc.npub)
            val request = AmberBunkerRequest(
                request = bunkerRequest,
                localKey = event.pubKey,
                relays = listOf(relay),
                currentAccount = acc.npub,
                nostrConnectSecret = "",
                closeApplication = true,
                name = "",
                signedEvent = null,
                encryptedData = null,
                encryptionType = if (EncryptedInfo.isNIP04(event.content)) EncryptionType.NIP04 else EncryptionType.NIP44,
                isNostrConnectUri = false,
                signerPrivKey = connectionPrivKey,
            )
            bunkerResponder.sendBunkerResponse(
                acc,
                request,
                BunkerResponse(bunkerRequest.id, "", "Amber is locked. Unlock your device and try again."),
                listOf(relay),
                onLoading = {},
                onDone = {},
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            log("Device locked: could not reject request ${event.id}: ${e.message}", relay.url, acc.npub)
        }
    }
}
