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
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.greenart7c3.nostrsigner.AccountStorage
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.SignerProviderQuery
import com.greenart7c3.nostrsigner.common.BUNKER_REPLY_ATTEMPTS
import com.greenart7c3.nostrsigner.common.EncryptionType
import com.greenart7c3.nostrsigner.common.Nip46Admission
import com.greenart7c3.nostrsigner.common.Nip46Connection
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.UNVERIFIED_REPLY_ATTEMPTS
import com.greenart7c3.nostrsigner.common.admitNip46Request
import com.greenart7c3.nostrsigner.common.hasAmbiguousRelayTags
import com.greenart7c3.nostrsigner.common.nip44v3Kind
import com.greenart7c3.nostrsigner.common.nip44v3Scope
import com.greenart7c3.nostrsigner.common.nip46SignerType
import com.greenart7c3.nostrsigner.common.nip46StaleReason
import com.greenart7c3.nostrsigner.common.relays.SenderRateLimiter
import com.greenart7c3.nostrsigner.common.sanitizeAppName
import com.greenart7c3.nostrsigner.common.switchRelaysResult
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.localizedBlocking
import com.greenart7c3.nostrsigner.commonsui.requests
import com.greenart7c3.nostrsigner.commonsui.wants_to_encrypt_a_list_of_tags_with
import com.greenart7c3.nostrsigner.commonsui.wants_to_encrypt_a_text_with
import com.greenart7c3.nostrsigner.commonsui.wants_to_encrypt_event_kind
import com.greenart7c3.nostrsigner.commonsui.wants_to_read_a_list_of_tags_from_encrypted_content
import com.greenart7c3.nostrsigner.commonsui.wants_to_read_a_text_from_encrypted_content
import com.greenart7c3.nostrsigner.commonsui.wants_to_read_from_encrypted_content
import com.greenart7c3.nostrsigner.commonsui.wants_you_to_sign_a
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.BunkerEventEntity
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.BunkerClientMetadata
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import com.greenart7c3.nostrsigner.service.NotificationUtils.sendNotification
import com.greenart7c3.nostrsigner.service.model.AmberEvent
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
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Encrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Encrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Constructor injection: one parameter per dependency.
@Suppress("LongParameterList")
class EventNotificationConsumer(
    private val applicationContext: Context,
    private val settingsStore: SettingsStore,
    private val accountRepository: AccountRepository,
    private val scope: CoroutineScope,
    private val notificationCache: LruCache<String, Long>,
    private val bunkerResponder: BunkerResponder,
    // Refreshes the NIP-46 subscription after a connection is added or removed.
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
            replyDeviceLocked(event, acc, relay, connectionPrivKey)
        }
    }

    /**
     * The account a verified kind-24133 [event] is for, with the private key of the
     * connection it came through ("" when tagged to the account key), or null when
     * it is not a bunker request for a logged-in account.
     */
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

    /** Whether [event] is within the sender's rate limit, fresh and not seen before. */
    private suspend fun isNewRequest(event: Event, acc: Account, relay: NormalizedRelayUrl): Boolean {
        // Senders that are not a stored connection are rate-limited before any
        // per-event write, decrypt or signed reply, so a flood costs them
        // silence instead of the user's battery. Stored connections bypass the
        // limiter: an approved app legitimately bursts (bulk decrypts, rapid
        // signing) and must never be dropped. Checked after verify() and keyed
        // on the proven signer pubkey, so forged events cannot drain a
        // legitimate sender's budget.
        if (accountRepository.dao(acc.npub).getByKeyRaw(event.pubKey) == null &&
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

    /**
     * The account a request tagged to [taggedHex] is for, with the private key of the
     * connection it came through ("" when tagged to the account key itself), or null.
     */
    private suspend fun addressee(taggedNpub: String, taggedHex: String, relay: NormalizedRelayUrl): Pair<Account, String>? {
        // First try to match the tagged key to a logged-in account (real pubkey)
        AccountStorage.load(applicationContext, taggedNpub)?.let {
            saveLog("Direct account match for ${it.npub}", relay.url)
            return it to ""
        }
        // Connection-based match via the in-memory index populated by
        // NotificationSubscription.updateFilter. This replaces an O(accounts × connections)
        // synchronous scan that previously ran inside runBlocking on the relay event thread.
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

    /**
     * Answers a request with a "locked" error instead of letting the client time
     * out. Only the connection key is used, never the account key.
     */
    private fun replyDeviceLocked(
        event: Event,
        acc: Account,
        relay: NormalizedRelayUrl,
        connectionPrivKey: String,
    ) {
        if (event.content.isEmpty()) return
        // The same event usually arrives from several relays; answer it once,
        // like notify() does.
        if (notificationCache[event.id] != null) return
        notificationCache.put(event.id, event.createdAt)
        scope.launch {
            try {
                val connSigner = NostrSignerInternal(KeyPair(privKey = connectionPrivKey.hexToByteArray()))
                val requestStr = connSigner.decrypt(event.content, event.pubKey)
                val bunkerRequest = JacksonMapper.mapper.readValue(requestStr, BunkerRequest::class.java)
                saveLog("Device locked: rejected request ${bunkerRequest.id} method ${bunkerRequest.method}", relay.url, acc.npub)
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
                saveLog("Device locked: could not reject request ${event.id}: ${e.message}", relay.url, acc.npub)
            }
        }
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
                    acc.decrypt(event.content, event.pubKey)
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
        val params = readParams(acc, unverified, type, relay) ?: return
        val admitted = admit(event, acc, unverified, type, params, relay) ?: return
        when (type) {
            SignerType.SWITCH_RELAYS -> switchRelays(admitted)
            SignerType.LOGOUT -> logout(admitted)
            SignerType.GET_PUBLIC_KEY -> getPublicKey(admitted)
            else -> answerFromGrants(admitted, params.pubKey, relay.url)
        }
    }

    // The key a request acts on (empty when none) and, for sign_event, its event.
    private class RequestParams(val pubKey: String, val amberEvent: AmberEvent?)

    /**
     * The params of [request], or null when it was refused before any key is used:
     * an auth event with more than one relay tag, an unknown method, or a missing or
     * unparsable param. The client gets an error instead of waiting for an answer.
     */
    private suspend fun readParams(acc: Account, request: AmberBunkerRequest, type: SignerType, relay: NormalizedRelayUrl): RequestParams? {
        val bunkerRequest = request.request
        // Authorization and the consent screen decide on the first relay tag
        // alone; a second relay tag would be signed without ever being granted.
        if (bunkerRequest is BunkerRequestSign && hasAmbiguousRelayTags(bunkerRequest.event.kind, bunkerRequest.event.tags)) {
            saveLog("Refusing a multi-relay auth event ${bunkerRequest.id}", relay.url, acc.npub)
            refuse(acc, request, "refusing a multi-relay auth event", attempts = BUNKER_REPLY_ATTEMPTS)
            return null
        }
        if (type == SignerType.INVALID) {
            saveLog("Invalid request method ${bunkerRequest.method}", relay.url, acc.npub)
            refuse(acc, request, "Unrecognized method: ${bunkerRequest.method}")
            return null
        }
        val params = requestParams(bunkerRequest)
        if (params == null) {
            saveLog("Invalid params for request ${bunkerRequest.id} method ${bunkerRequest.method}", relay.url, acc.npub)
            refuse(acc, request, "invalid params")
        }
        return params
    }

    // Params come straight from the client: a missing one or an unparsable event is null.
    private fun requestParams(bunkerRequest: BunkerRequest): RequestParams? {
        val method = bunkerRequest.method
        val amberEvent = if (method == "sign_event") bunkerRequest.params.firstOrNull()?.let { runCatching { AmberEvent.fromJson(it) }.getOrNull() } else null
        val pubKey = when {
            method.endsWith("encrypt") || method.endsWith("decrypt") -> bunkerRequest.params.firstOrNull()
            method == "decrypt_zap_event" -> bunkerRequest.params.getOrNull(1)
            method == "sign_event" -> amberEvent?.pubKey
            else -> ""
        }
        return pubKey?.let { RequestParams(it, amberEvent) }
    }

    private suspend fun refuse(acc: Account, request: AmberBunkerRequest, error: String, attempts: Int = UNVERIFIED_REPLY_ATTEMPTS) {
        bunkerResponder.sendBunkerResponse(
            acc,
            request,
            BunkerResponse(request.request.id, "", error),
            request.relays.ifEmpty { settingsStore.settings.defaultRelays },
            onLoading = {},
            onDone = {},
            attempts = attempts,
        )
    }

    /**
     * Admits [request] (see the common `admitNip46Request`): a repeated connect is
     * acknowledged and anything not admitted refused, both returning null. Only for an
     * authorized sender — a stored connection or a valid connect secret — is the
     * account key used, to sign the event or read the data the request carries.
     */
    private suspend fun admit(
        event: Event,
        acc: Account,
        request: AmberBunkerRequest,
        type: SignerType,
        params: RequestParams,
        relay: NormalizedRelayUrl,
    ): Admitted? {
        val dao = accountRepository.dao(acc.npub)
        val bunkerRequest = request.request
        val stored = dao.getByKey(event.pubKey)
        if (stored != null && stored.application.isExpired()) {
            // The user set a finite lifetime on this connection: answer as an
            // unknown app even before the periodic cleanup deletes the row.
            saveLog("Connection expired for ${event.pubKey}", relay.url, acc.npub)
        }
        val connectSecret = (bunkerRequest as? BunkerRequestConnect)?.let { it.secret ?: UUID.randomUUID().toString() }
        val withSecret = connectSecret?.let { dao.getBySecret(it) }
        val admission = admitNip46Request(type, stored?.toNip46Connection(), request.signerPrivKey, connectSecret, withSecret?.toNip46Connection())
        if (admission is Nip46Admission.AckRepeatedConnect) {
            ackRepeatedConnect(acc, request, admission.connection.key, type, params.amberEvent, stored?.application?.relays ?: request.relays)
            return null
        }
        if (admission !is Nip46Admission.Admit) {
            refuseUnadmitted(admission, acc, request, withSecret?.application?.relays ?: request.relays, relay.url)
            return null
        }
        val permission = stored.takeIf { admission.stored != null }
        val applicationWithSecret = withSecret.takeIf { admission.bySecret != null }
        val relays = permission?.application?.relays ?: applicationWithSecret?.application?.relays ?: request.relays
        val authorized = request.copy(
            relays = relays,
            signedEvent = (bunkerRequest as? BunkerRequestSign)?.let { acc.sign(it.event) },
            encryptedData = getEncryptedDataKind(bunkerRequest, acc, relay.url),
        )
        return Admitted(acc, authorized, relays, event, type, params.amberEvent, permission, applicationWithSecret)
    }

    // What every handler of an admitted request needs.
    private class Admitted(
        val acc: Account,
        val request: AmberBunkerRequest,
        val relays: List<NormalizedRelayUrl>,
        val event: Event,
        val type: SignerType,
        val amberEvent: AmberEvent?,
        val permission: ApplicationWithPermissions?,
        val applicationWithSecret: ApplicationWithPermissions?,
    ) {
        val id: String get() = request.request.id
    }

    private suspend fun reply(a: Admitted, result: String?, error: String? = null, onDone: (Boolean) -> Unit = { }) {
        bunkerResponder.sendBunkerResponse(
            account = a.acc,
            bunkerRequest = a.request,
            bunkerResponse = BunkerResponse(a.id, result, error),
            relays = a.relays,
            onLoading = { },
            onDone = onDone,
        )
    }

    // Leaves the decision to the user: the request notification opens the approval screen.
    private fun askUser(a: Admitted) {
        val message = generateMessage(a.type, a.amberEvent, a.request, a.event, a.permission, a.applicationWithSecret)
        notificationManager().sendNotification(message, "Bunker", "BunkerID", applicationContext, a.request)
    }

    // A stored connection moves to the default relays once the client has the answer.
    private suspend fun switchRelays(a: Admitted) {
        val permission = a.permission ?: return
        val defaultRelays = settingsStore.settings.defaultRelays
        reply(a, switchRelaysResult(defaultRelays.map { it.url })) { published ->
            if (published) {
                scope.launch {
                    val dao = accountRepository.dao(a.acc.npub)
                    dao.delete(permission.application.key)
                    dao.insertApplicationWithPermissions(permission.copy(application = permission.application.copy(relays = defaultRelays)))
                    onConnectionsChanged()
                }
            }
        }
    }

    // A stored connection is deleted, with its history and queued requests, once the client has the ack.
    private suspend fun logout(a: Admitted) {
        val permission = a.permission ?: return
        reply(a, "ack") { published ->
            if (published) {
                scope.launch {
                    accountRepository.dao(a.acc.npub).delete(permission.application.key)
                    accountRepository.getHistoryDatabase(a.acc.npub).dao().deleteHistory(permission.application.key)
                    BunkerRequestUtils.removeAllForClient(applicationContext, permission.application.key)
                    onConnectionsChanged()
                }
            }
        }
    }

    private suspend fun getPublicKey(a: Admitted) {
        val dao = accountRepository.dao(a.acc.npub)
        val accepted = isRemembered(dao.getSignPolicy(a.event.pubKey), dao.getPermission(a.event.pubKey, "GET_PUBLIC_KEY"))
        if (accepted == null) {
            askUser(a)
            return
        }
        scope.launch {
            accountRepository.getHistoryDatabase(a.acc.npub).dao().addHistory(
                listOf(HistoryEntity(0, a.event.pubKey, "GET_PUBLIC_KEY", null, TimeUtils.now(), accepted)),
                a.acc.npub,
            )
        }
        if (accepted) reply(a, a.acc.hexKey) else reply(a, "", "user rejected")
    }

    // Answers from the stored grants (through SignerProviderQuery, like an intent), else asks.
    private suspend fun answerFromGrants(a: Admitted, pubKey: String, relayUrl: String) {
        val bunkerRequest = a.request.request
        val data = BunkerRequestUtils.getDataFromBunker(bunkerRequest)
        val projection = when (a.type) {
            SignerType.PING -> arrayOf(a.acc.npub)
            SignerType.NIP44_V3_ENCRYPT, SignerType.NIP44_V3_DECRYPT -> {
                val kind = bunkerRequest.nip44v3Kind()
                val scope = bunkerRequest.nip44v3Scope()
                // A v3 request that can never succeed — missing/invalid kind, a
                // decrypt whose context (kind/scope) does not match the
                // ciphertext, a bad MAC/padding, or a non-base64 encrypt payload
                // — is rejected here, before any approval screen is shown.
                validateNip44v3Request(a.type, kind, scope, data, pubKey, a.acc)?.let {
                    saveLog("Rejecting invalid NIP-44 v3 request: $it", relayUrl, a.acc.npub)
                    reply(a, "", it)
                    return
                }
                arrayOf(data, pubKey, a.acc.npub, kind.toString(), scope)
            }
            else -> arrayOf(data, pubKey, a.acc.npub)
        }
        val cursor = SignerProviderQuery(settingsStore, accountRepository, scope).query(
            context = applicationContext,
            // NIP-46 requests are attributed to the client's pubkey, not an
            // installed app. There is no installed caller, so nothing to capture.
            requesterId = a.event.pubKey,
            callerPackageName = null,
            operationUri = "content://${BuildConfig.APPLICATION_ID}.${a.type}".toUri(),
            arguments = projection,
        )
        cursor.use { localCursor ->
            when {
                localCursor == null || !localCursor.moveToFirst() -> askUser(a)
                localCursor.getColumnIndex("rejected") > -1 -> {
                    saveLog("User rejected request ${a.id}", relayUrl, a.acc.npub)
                    reply(a, "", "user rejected")
                }
                else -> {
                    val index = localCursor.getColumnIndex("event").takeIf { it != -1 } ?: localCursor.getColumnIndex("result")
                    reply(a, localCursor.getString(index))
                }
            }
        }
    }

    // An already connected client re-sent connect: acknowledged without asking, and recorded.
    private suspend fun ackRepeatedConnect(
        acc: Account,
        request: AmberBunkerRequest,
        appKey: String,
        type: SignerType,
        amberEvent: AmberEvent?,
        relays: List<NormalizedRelayUrl>,
    ) {
        scope.launch {
            accountRepository.getHistoryDatabase(acc.npub).dao().addHistory(
                listOf(
                    HistoryEntity(
                        0,
                        appKey,
                        type.toString().toLowerCase(Locale.current),
                        amberEvent?.kind,
                        TimeUtils.now(),
                        true,
                        content = amberEvent?.content ?: "",
                    ),
                ),
                acc.npub,
            )
        }
        bunkerResponder.sendBunkerResponse(
            account = acc,
            bunkerRequest = request,
            bunkerResponse = BunkerResponse(request.request.id, request.nostrConnectSecret.ifBlank { "ack" }, null),
            relays = relays,
            onLoading = {},
            onDone = {},
        )
    }

    // Answers (or drops) a request admitNip46Request refused; the account key is not used.
    private suspend fun refuseUnadmitted(
        admission: Nip46Admission,
        acc: Account,
        request: AmberBunkerRequest,
        relays: List<NormalizedRelayUrl>,
        relayUrl: String,
    ) {
        val message = when (admission) {
            is Nip46Admission.RefuseConnect -> admission.message.also { saveLog("Connection rejected: $it", relayUrl, acc.npub) }
            Nip46Admission.Unknown -> "no permission".also { saveLog("No permission found for ${request.localKey}", relayUrl, acc.npub) }
            else -> {
                // A stored app may only use its own connection key: a request it
                // sends to another connection's key is dropped rather than answered
                // under that connection's identity. A connect is exempt, as that is
                // how a client attaches to a new key.
                saveLog("Request ${request.request.id} from ${request.localKey} reached another connection's key", relayUrl, acc.npub)
                return
            }
        }
        bunkerResponder.sendBunkerResponse(
            account = acc,
            bunkerRequest = request,
            bunkerResponse = BunkerResponse(request.request.id, "", message),
            relays = relays,
            onLoading = { },
            onDone = { },
            attempts = UNVERIFIED_REPLY_ATTEMPTS,
        )
    }

    private fun generateMessage(
        type: SignerType,
        amberEvent: AmberEvent?,
        request: AmberBunkerRequest,
        event: Event,
        permission: ApplicationWithPermissions?,
        applicationWithSecret: ApplicationWithPermissions?,
    ): String {
        // Saved names may predate sanitizing at entry.
        val name = sequenceOf(permission, applicationWithSecret)
            .map { sanitizeAppName(it?.application?.name ?: "") }
            .firstOrNull { it.isNotBlank() } ?: event.pubKey.toShortenHex()

        val bunkerPermission = Permission(type.toString().toLowerCase(Locale.current), amberEvent?.kind)
        var message = "$name  ${getStringBlocking(Res.string.requests)} ${bunkerPermission.localizedBlocking()}"
        if (type == SignerType.SIGN_EVENT) {
            message = "$name ${getStringBlocking(Res.string.wants_you_to_sign_a, bunkerPermission.localizedBlocking())}"
        }
        if (type == SignerType.CONNECT) {
            message = "$name ${bunkerPermission.localizedBlocking()}"
        }

        if (type.name.contains("ENCRYPT")) {
            when (request.encryptedData) {
                is EventEncryptedDataKind -> {
                    val permission = Permission("sign_event", request.encryptedData.event.kind)
                    message = "$name ${getStringBlocking(Res.string.wants_to_encrypt_event_kind, permission.localizedBlocking(), type.name.split("_").first())}"
                }

                is TagArrayEncryptedDataKind -> {
                    message = "$name ${getStringBlocking(Res.string.wants_to_encrypt_a_list_of_tags_with, type.name.split("_").first())}"
                }

                else -> message = "$name ${getStringBlocking(Res.string.wants_to_encrypt_a_text_with, type.name.split("_").first())}"
            }
        } else if (type.name.contains("DECRYPT")) {
            when (request.encryptedData) {
                is EventEncryptedDataKind -> {
                    val permission = Permission("sign_event", request.encryptedData.event.kind)
                    message = "$name${getStringBlocking(Res.string.wants_to_read_from_encrypted_content, permission.localizedBlocking(), type.name.split("_").first())}"
                }

                is TagArrayEncryptedDataKind -> {
                    message = "$name ${getStringBlocking(Res.string.wants_to_read_a_list_of_tags_from_encrypted_content, type.name.split("_").first())}"
                }

                else -> message = "$name ${getStringBlocking(Res.string.wants_to_read_a_text_from_encrypted_content, type.name.split("_").first())}"
            }
        }
        return message
    }

    private suspend fun getEncryptedDataKind(
        bunkerRequest: BunkerRequest?,
        acc: Account,
        url: String,
    ): EncryptedDataKind? {
        if (bunkerRequest == null) return null
        // V3 arrives as a generic BunkerRequest; build the preview the dedicated
        // approval screen renders (plaintext, like the v2 path).
        if (bunkerRequest.method == "nip44v3_encrypt" || bunkerRequest.method == "nip44v3_decrypt") return nip44v3EncryptedDataKind(bunkerRequest, acc)
        val onParseError: (String) -> Unit = { saveLog(it, url, acc.npub) }
        // The plaintext side is what the user reads: the message of an encrypt, the result of a decrypt.
        return when (bunkerRequest) {
            is BunkerRequestNip44Decrypt -> acc.nip44Decrypt(bunkerRequest.ciphertext, bunkerRequest.pubKey).let {
                previewOf(it, bunkerRequest.ciphertext, it, acc, onParseError)
            }
            is BunkerRequestNip44Encrypt -> previewOf(bunkerRequest.message, bunkerRequest.message, acc.nip44Encrypt(bunkerRequest.message, bunkerRequest.pubKey), acc, onParseError)
            is BunkerRequestNip04Decrypt -> acc.nip04Decrypt(bunkerRequest.ciphertext, bunkerRequest.pubKey).let {
                previewOf(it, bunkerRequest.ciphertext, it, acc, onParseError)
            }
            is BunkerRequestNip04Encrypt -> previewOf(bunkerRequest.message, bunkerRequest.message, acc.nip04Encrypt(bunkerRequest.message, bunkerRequest.pubKey), acc, onParseError)
            else -> null
        }
    }

    /**
     * Builds the preview [EncryptedDataKind] for a NIP-44 v3 bunker request,
     * mirroring v2: encrypt stores the plaintext as `text` and the ciphertext
     * as `result`; decrypt stores the ciphertext as `text` and the decrypted
     * plaintext as `result`. The Base64 wire payload is decoded here once so
     * history/display can use the readable plaintext. Returns null if it can't
     * be produced (the request is auto-rejected elsewhere in that case).
     */
    private fun nip44v3EncryptedDataKind(
        bunkerRequest: BunkerRequest,
        acc: Account,
    ): EncryptedDataKind? {
        val kind = bunkerRequest.nip44v3Kind() ?: return null
        val scope = bunkerRequest.nip44v3Scope()
        val data = BunkerRequestUtils.getDataFromBunker(bunkerRequest)
        val pubKey = bunkerRequest.params.firstOrNull() ?: return null
        return try {
            // text = readable plaintext (for display/history); result = wire value.
            if (bunkerRequest.method == "nip44v3_encrypt") {
                val plainBytes = Base64.decode(data)
                val ciphertext = acc.nip44v3Encrypt(plainBytes, pubKey, kind, scope)
                ClearTextEncryptedDataKind(plainBytes.toString(Charsets.UTF_8), ciphertext)
            } else {
                val plainBytes = acc.nip44v3Decrypt(data, pubKey, kind, scope)
                ClearTextEncryptedDataKind(plainBytes.toString(Charsets.UTF_8), Base64.encode(plainBytes))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    /**
     * Validates a NIP-44 v3 bunker request. Returns null when it can proceed, or
     * a generic error when it is malformed and must be rejected without
     * prompting the user (missing kind, or a decrypt whose context/MAC/padding
     * doesn't check out).
     */
    private fun validateNip44v3Request(
        type: SignerType,
        kind: Int?,
        scope: String,
        data: String,
        pubKey: String,
        acc: Account,
    ): String? {
        if (kind == null) return "kind is required for nip44v3"
        if (type != SignerType.NIP44_V3_DECRYPT) return null
        return try {
            acc.nip44v3Decrypt(data, pubKey, kind, scope)
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // A device-lock or settings refusal is not a ciphertext problem:
            // let it through so the client gets the real condition instead of
            // being told the payload was corrupt.
            if (e is SecurityException || e is IllegalStateException) throw e
            // Keep the response generic so we don't leak the ciphertext's
            // embedded context back to the requester.
            "could not decrypt the message"
        }
    }

    fun notificationManager(): NotificationManager = ContextCompat.getSystemService(applicationContext, NotificationManager::class.java) as NotificationManager

    companion object {
        // One budget per process: several call sites construct their own consumer.
        internal val senderRateLimiter = SenderRateLimiter()
    }
}

private fun ApplicationWithPermissions.toNip46Connection() = Nip46Connection(
    key = application.key,
    localKey = application.localKey,
    secret = application.secret,
    useSecret = application.useSecret,
    isConnected = application.isConnected,
    isExpired = application.isExpired(),
)
