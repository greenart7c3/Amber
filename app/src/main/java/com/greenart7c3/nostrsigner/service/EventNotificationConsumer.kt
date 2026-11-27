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

import android.app.NotificationManager
import android.content.Context
import androidx.collection.LruCache
import androidx.core.content.ContextCompat
import com.greenart7c3.nostrsigner.AccountStorage
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.common.EncryptionType
import com.greenart7c3.nostrsigner.common.nip46SignerType
import com.greenart7c3.nostrsigner.common.nip46StaleReason
import com.greenart7c3.nostrsigner.common.relays.SenderRateLimiter
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.database.BunkerEventEntity
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.BunkerClientMetadata
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUsers
import com.vitorpamplona.quartz.nip04Dm.crypto.EncryptedInfo
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Suppress("LongParameterList")
class EventNotificationConsumer(
    private val applicationContext: Context,
    private val settingsStore: SettingsStore,
    private val accountRepository: AccountRepository,
    private val scope: CoroutineScope,
    private val notificationCache: LruCache<String, Long>,
    private val bunkerResponder: BunkerResponder,
    private val onConnectionsChanged: suspend () -> Unit,
) {
    internal fun saveLog(
        text: String,
        url: String,
        npub: String? = null,
    ) {
        AmberLog.d(Amber.TAG, "$url: $text")
        scope.launch {
            if (npub != null) {
                val dao = accountRepository.getLogDatabase(npub).dao()
                dao.insertLogIfEnabled(
                    LogEntity(
                        0,
                        url,
                        "bunker",
                        text,
                        System.currentTimeMillis(),
                    ),
                )
            } else {
                val accounts = SavedAccounts.all(applicationContext)
                accounts.forEach {
                    AccountStorage.load(applicationContext, it.npub)?.let { acc ->
                        val dao = accountRepository.getLogDatabase(acc.npub).dao()
                        dao.insertLogIfEnabled(
                            LogEntity(
                                0,
                                url,
                                "bunker",
                                text,
                                System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            }
        }
    }

    @Suppress("NoComments")
    suspend fun consume(
        event: Event,
        relay: NormalizedRelayUrl,
    ) {
        val (acc, connectionPrivKey) = recipient(event, relay) ?: return
        if (!isNewRequest(event, acc, relay)) return

        NotificationUtils.getOrCreateBunkerChannel(applicationContext)
        NotificationUtils.getOrCreateErrorsChannel(applicationContext)

        val bunkerEventDao = accountRepository.getDatabase(acc.npub).bunkerEventDao()
        // With requireUnlockedDevice on, nothing may use the account key while
        // the device is locked, and that includes opening the request.
        if (!isAccountKeyLocked(acc)) {
            bunkerEventDao.insert(BunkerEventEntity(0, event.id, event.createdAt))
            notify(event, acc, relay, connectionPrivKey)
        } else if (connectionPrivKey.isEmpty()) {
            // Legacy client: the request and any reply are encrypted with the
            // account key, so it can't even be answered. Leave the event
            // unrecorded so a re-delivery after unlock is processed.
            saveLog("Device locked: request ${event.id} from a legacy client can't be opened until unlock", relay.url, acc.npub)
        } else {
            bunkerEventDao.insert(BunkerEventEntity(0, event.id, event.createdAt))
            requestServices().replyDeviceLockedWithConnectionKeyOnly(notificationCache, event, acc, relay, connectionPrivKey)
        }
    }

    private fun requestServices() = Nip46RequestServices(settingsStore, accountRepository, scope, bunkerResponder, ::saveLog)

    private suspend fun recipient(event: Event, relay: NormalizedRelayUrl): Pair<Account, String>? {
        if (!notificationManager().areNotificationsEnabled()) {
            saveLog("notifications disabled", relay.url)
            return null
        }
        if (event.kind != NostrConnectEvent.KIND) {
            saveLog("Not a bunker request: kind ${event.kind}", relay.url)
            return null
        }
        if (!event.verify()) {
            saveLog("Invalid id hash or signature", relay.url)
            return null
        }
        val taggedKey = event.taggedUsers().firstOrNull()
        if (taggedKey == null) {
            saveLog("No tagged users found in event ${event.id}", relay.url)
            return null
        }
        return addressee(taggedKey.toNPub(), taggedKey.pubKey, relay)
            ?: null.also { saveLog("Tagged account ${taggedKey.toNPub()} not logged in", relay.url) }
    }

    @Suppress("NoComments")
    private suspend fun isNewRequest(event: Event, acc: Account, relay: NormalizedRelayUrl): Boolean {
        // Senders that are not a stored connection are rate-limited before any
        // per-event write, decrypt or signed reply, so a flood costs them
        // silence instead of the user's battery. Stored connections bypass the
        // limiter: an approved app legitimately bursts (bulk decrypts, rapid
        // signing) and must never be dropped. Checked after verify() and keyed
        // on the proven signer pubkey, so forged events cannot drain a
        // legitimate sender's budget.
        if (accountRepository.daos(acc.npub).applicationDao.getByKeyRaw(event.pubKey) == null &&
            !senderRateLimiter.allow("${relay.url}|${event.pubKey}", System.currentTimeMillis())
        ) {
            AmberLog.d(Amber.TAG, "${relay.url}: rate-limited event ${event.id} from unknown sender ${event.pubKey}")
            return false
        }
        saveLog("New event ${event.id} from ${event.pubKey}", relay.url, acc.npub)

        // Freshness is checked after the limiter: these rejections log the
        // event id, so a flood of signed stale events must be budgeted too.
        val staleReason = nip46StaleReason(event.id, event.createdAt, event.expiration(), TimeUtils.now())
        if (staleReason != null) {
            saveLog(staleReason, relay.url, acc.npub)
            return false
        }
        val seen = accountRepository.getDatabase(acc.npub).bunkerEventDao().exists(event.id)
        if (seen) saveLog("Event ${event.id} already processed (persistent cache)", relay.url)
        return !seen
    }

    private suspend fun addressee(taggedNpub: String, taggedHex: String, relay: NormalizedRelayUrl): Pair<Account, String>? {
        AccountStorage.load(applicationContext, taggedNpub)?.let {
            saveLog("Direct account match for ${it.npub}", relay.url)
            return it to ""
        }
        val match = LocalKeyAccountIndex.lookup(taggedHex)
        val acc = match?.let { AccountStorage.load(applicationContext, it.npub) }
        return acc?.let {
            saveLog("Found connection for ${it.npub} with local pubkey $taggedHex", relay.url)
            it to match.localPrivKey
        }
    }

    private fun isAccountKeyLocked(acc: Account): Boolean = try {
        acc.requirePrivateKeyAccess()
        false
    } catch (_: SecurityException) {
        true
    }

    private fun notify(
        event: Event,
        acc: Account,
        relay: NormalizedRelayUrl,
        connectionPrivKey: String = "",
    ) {
        if (event.content.isEmpty()) return

        saveLog("New event ${event.toJson()}", relay.url, acc.npub)

        val encryptionType = if (EncryptedInfo.isNIP04(event.content)) {
            EncryptionType.NIP04
        } else {
            EncryptionType.NIP44
        }

        scope.launch {
            val decrypted = try {
                if (connectionPrivKey.isNotEmpty()) {
                    val connSigner = NostrSignerInternal(KeyPair(privKey = connectionPrivKey.hexToByteArray()))
                    connSigner.decrypt(event.content, event.pubKey)
                } else {
                    acc.crypto.decrypt(event.content, event.pubKey)
                }
            } catch (e: Exception) {
                saveLog("Decryption failed for event ${event.id}: ${e.message}", relay.url, acc.npub)
                null
            }

            decrypted?.let {
                notify(event, acc, it, relay, encryptionType, connectionPrivKey)
            }
        }
    }

    @Suppress("NoComments")
    private suspend fun notify(
        event: Event,
        acc: Account,
        requestStr: String,
        relay: NormalizedRelayUrl,
        encryptionType: EncryptionType,
        connectionPrivKey: String = "",
    ) {
        if (notificationCache[event.id] != null) return
        notificationCache.put(event.id, event.createdAt)

        val bunkerRequest = JacksonMapper.mapper.readValue(requestStr, BunkerRequest::class.java)

        // Never persist the decrypted request body — it can contain plaintext
        // (e.g. the message to encrypt, or the event to sign). The encrypted event
        // is already logged above; here we record only non-sensitive metadata.
        saveLog("Decrypted request ${bunkerRequest.id} method ${bunkerRequest.method}", relay.url, acc.npub)

        // NIP-46 connect requests may carry optional client metadata as the 4th
        // param (nostr-protocol/nips#2381) so the signer can show who is
        // connecting over the bunker:// flow. It must be read from the raw request
        // JSON: Quartz's BunkerRequestConnect only keeps params[0..2] and drops the
        // metadata element when it rebuilds its params array.
        val clientMetadata = if (bunkerRequest is BunkerRequestConnect) BunkerClientMetadata.fromConnectRequest(requestStr) else null

        // Only addresses the replies sent before authorization, so it carries no
        // signed event or decrypted data: the account key is not used for a
        // sender until it is known to have a connection.
        val unverified = AmberBunkerRequest(
            request = bunkerRequest,
            localKey = event.pubKey,
            relays = listOf(relay),
            currentAccount = acc.npub,
            nostrConnectSecret = "",
            closeApplication = true,
            name = clientMetadata?.name ?: "",
            signedEvent = null,
            encryptedData = null,
            encryptionType = encryptionType,
            isNostrConnectUri = false,
            signerPrivKey = connectionPrivKey,
            clientMetadata = clientMetadata,
        )

        val type = nip46SignerType(bunkerRequest.method)
        val services = requestServices()
        val admission = Nip46RequestAdmission(services)
        val params = admission.readParams(acc, unverified, type, relay) ?: return
        val admitted = admission.admit(event, acc, unverified, type, params, relay) ?: return
        Nip46AdmittedRequests(applicationContext, services, ::notificationManager, onConnectionsChanged).answer(admitted, params.pubKey, relay.url)
    }

    fun notificationManager(): NotificationManager = ContextCompat.getSystemService(applicationContext, NotificationManager::class.java) as NotificationManager

    companion object {
        internal val senderRateLimiter = SenderRateLimiter()
    }
}
