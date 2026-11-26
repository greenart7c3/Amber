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
import androidx.core.net.toUri
import com.greenart7c3.nostrsigner.AccountStorage
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.SignerProviderQuery
import com.greenart7c3.nostrsigner.common.BUNKER_REPLY_ATTEMPTS
import com.greenart7c3.nostrsigner.common.EncryptionType
import com.greenart7c3.nostrsigner.common.NIP44V3_DECRYPT_FAILED
import com.greenart7c3.nostrsigner.common.NIP44V3_KIND_REQUIRED
import com.greenart7c3.nostrsigner.common.NIP46_INVALID_PARAMS
import com.greenart7c3.nostrsigner.common.Nip46Admission
import com.greenart7c3.nostrsigner.common.Nip46AdmissionReply
import com.greenart7c3.nostrsigner.common.Nip46Connection
import com.greenart7c3.nostrsigner.common.Nip46Step
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.UNVERIFIED_REPLY_ATTEMPTS
import com.greenart7c3.nostrsigner.common.admitNip46Request
import com.greenart7c3.nostrsigner.common.eventParam
import com.greenart7c3.nostrsigner.common.hasRequiredParams
import com.greenart7c3.nostrsigner.common.nip44v3Kind
import com.greenart7c3.nostrsigner.common.nip44v3Scope
import com.greenart7c3.nostrsigner.common.nip46SignerType
import com.greenart7c3.nostrsigner.common.nip46StaleReason
import com.greenart7c3.nostrsigner.common.nip46Step
import com.greenart7c3.nostrsigner.common.peerPubKeyParam
import com.greenart7c3.nostrsigner.common.relays.SenderRateLimiter
import com.greenart7c3.nostrsigner.common.reply
import com.greenart7c3.nostrsigner.common.sanitizeAppName
import com.greenart7c3.nostrsigner.common.zapRequestParam
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
            replyDeviceLocked(event, acc, relay, connectionPrivKey)
        }
    }

    private suspend fun recipient(event: Event, relay: NormalizedRelayUrl): Recipient? {
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

    private suspend fun addressee(taggedNpub: String, taggedHex: String, relay: NormalizedRelayUrl): Recipient? {
        AccountStorage.load(applicationContext, taggedNpub)?.let {
            saveLog("Direct account match for ${it.npub}", relay.url)
            return Recipient(it, connectionPrivKey = "")
        }
        val match = LocalKeyAccountIndex.lookup(taggedHex)
        val acc = match?.let { AccountStorage.load(applicationContext, it.npub) }
        return acc?.let {
            saveLog("Found connection for ${it.npub} with local pubkey $taggedHex", relay.url)
            Recipient(it, match.localPrivKey)
        }
    }

    private data class Recipient(val account: Account, val connectionPrivKey: String)

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
    @Suppress("NoComments")
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
        val params = readParams(acc, unverified, type, relay) ?: return
        val admitted = admit(event, acc, unverified, type, params, relay) ?: return
        when (val step = admitted.step) {
            is Nip46Step.SwitchRelays -> switchRelays(admitted, step.result)
            Nip46Step.Logout -> logout(admitted)
            else -> if (type == SignerType.GET_PUBLIC_KEY) getPublicKey(admitted) else answerFromGrants(admitted, params.pubKey, relay.url)
        }
    }

    private class RequestParams(val pubKey: String, val amberEvent: AmberEvent?)

    private suspend fun readParams(acc: Account, request: AmberBunkerRequest, type: SignerType, relay: NormalizedRelayUrl): RequestParams? {
        val bunkerRequest = request.request
        if (type == SignerType.INVALID) {
            saveLog("Invalid request method ${bunkerRequest.method}", relay.url, acc.npub)
            refuse(acc, request, "Unrecognized method: ${bunkerRequest.method}")
            return null
        }
        val params = requestParams(bunkerRequest)?.takeIf { bunkerRequest.hasRequiredParams() }
        if (params == null) {
            saveLog("Invalid params for request ${bunkerRequest.id} method ${bunkerRequest.method}", relay.url, acc.npub)
            refuse(acc, request, NIP46_INVALID_PARAMS)
        }
        return params
    }

    private fun requestParams(bunkerRequest: BunkerRequest): RequestParams? {
        val method = bunkerRequest.method
        val amberEvent = if (method == "sign_event") bunkerRequest.eventParam()?.let { runCatching { AmberEvent.fromJson(it) }.getOrNull() } else null
        val pubKey = when {
            method.endsWith("encrypt") || method.endsWith("decrypt") -> bunkerRequest.peerPubKeyParam()
            method == "decrypt_zap_event" -> bunkerRequest.zapRequestParam()
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
     * Only for an authorized sender — a stored connection or a valid connect secret — is
     * the account key used, to sign the event or read the data the request carries.
     */
    @Suppress("NoComments")
    private suspend fun admit(
        event: Event,
        acc: Account,
        request: AmberBunkerRequest,
        type: SignerType,
        params: RequestParams,
        relay: NormalizedRelayUrl,
    ): Admitted? {
        val dao = accountRepository.daos(acc.npub).applicationDao
        val bunkerRequest = request.request
        val stored = dao.getByKey(event.pubKey)
        if (stored != null && stored.application.isExpired()) {
            saveLog("Connection expired for ${event.pubKey}", relay.url, acc.npub)
        }
        val connectSecret = (bunkerRequest as? BunkerRequestConnect)?.let { it.secret ?: UUID.randomUUID().toString() }
        val withSecret = connectSecret?.let { dao.getBySecret(it) }
        val admission = admitNip46Request(type, stored?.toNip46Connection(), request.signerPrivKey, connectSecret, withSecret?.toNip46Connection())
        if (admission !is Nip46Admission.Admit) {
            admission.reply(event.pubKey, bunkerRequest.id, stored?.application?.relays, withSecret?.application?.relays, request.relays)
                ?.let { sendAdmissionReply(it, acc, request, type, params.amberEvent, relay.url) }
            return null
        }
        val permission = stored.takeIf { admission.stored != null }
        val applicationWithSecret = withSecret.takeIf { admission.bySecret != null }
        val relays = permission?.application?.relays ?: applicationWithSecret?.application?.relays ?: request.relays
        // The common first step, as on the desktop: once the sender is admitted (an
        // unknown one got the single "no permission"), and before anything is signed.
        val step = nip46Step(bunkerRequest, type, settingsStore.settings.authWhitelist, settingsStore.settings.defaultRelays.map { it.url })
        if (step is Nip46Step.Refuse) {
            refuseUpFront(acc, request.copy(relays = relays), step, type, params.amberEvent, relay.url)
            return null
        }
        val authorized = request.copy(
            relays = relays,
            signedEvent = (bunkerRequest as? BunkerRequestSign)?.let { acc.sign(it.event) },
            encryptedData = getEncryptedDataKind(bunkerRequest, acc, relay.url),
        )
        return Admitted(acc, authorized, relays, event, type, params.amberEvent, permission, applicationWithSecret, step)
    }

    @Suppress("LongParameterList")
    private class Admitted(
        val acc: Account,
        val request: AmberBunkerRequest,
        val relays: List<NormalizedRelayUrl>,
        val event: Event,
        val type: SignerType,
        val amberEvent: AmberEvent?,
        val permission: ApplicationWithPermissions?,
        val applicationWithSecret: ApplicationWithPermissions?,
        val step: Nip46Step,
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

    private fun askUser(a: Admitted) {
        val message = generateMessage(a.type, a.amberEvent, a.request, a.event, a.permission, a.applicationWithSecret)
        notificationManager().sendNotification(message, "Bunker", "BunkerID", applicationContext, a.request)
    }

    private suspend fun switchRelays(a: Admitted, result: String?) {
        val permission = a.permission ?: return
        val defaultRelays = settingsStore.settings.defaultRelays
        reply(a, result) { published ->
            if (published) {
                scope.launch {
                    val daos = accountRepository.daos(a.acc.npub)
                    daos.applicationWriteDao.delete(permission.application.key)
                    daos.permissionWriteDao.insertApplicationWithPermissions(permission.copy(application = permission.application.copy(relays = defaultRelays)))
                    onConnectionsChanged()
                }
            }
        }
    }

    private suspend fun logout(a: Admitted) {
        val permission = a.permission ?: return
        reply(a, Nip46Step.Logout.RESULT) { published ->
            if (published) {
                scope.launch {
                    accountRepository.daos(a.acc.npub).applicationWriteDao.delete(permission.application.key)
                    accountRepository.getHistoryDatabase(a.acc.npub).dao().deleteHistory(permission.application.key)
                    BunkerRequestUtils.removeAllForClient(applicationContext, permission.application.key)
                    onConnectionsChanged()
                }
            }
        }
    }

    private suspend fun getPublicKey(a: Admitted) {
        val dao = accountRepository.daos(a.acc.npub).permissionLookupDao
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

    private suspend fun answerFromGrants(a: Admitted, pubKey: String, relayUrl: String) {
        val bunkerRequest = a.request.request
        val data = BunkerRequestUtils.getDataFromBunker(bunkerRequest)
        val projection = when (a.type) {
            SignerType.PING -> arrayOf(a.acc.npub)
            SignerType.NIP44_V3_ENCRYPT, SignerType.NIP44_V3_DECRYPT -> {
                val kind = bunkerRequest.nip44v3Kind()
                val scope = bunkerRequest.nip44v3Scope()
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

    private suspend fun refuseUpFront(
        acc: Account,
        request: AmberBunkerRequest,
        step: Nip46Step.Refuse,
        type: SignerType,
        amberEvent: AmberEvent?,
        relayUrl: String,
    ) {
        saveLog(step.log, relayUrl, acc.npub)
        if (step.recordRejected) {
            scope.launch {
                accountRepository.getHistoryDatabase(acc.npub).dao().addHistory(
                    listOf(HistoryEntity(0, request.localKey, type.toString(), amberEvent?.kind, TimeUtils.now(), false, content = amberEvent?.toEvent()?.toJson() ?: "")),
                    acc.npub,
                )
            }
        }
        refuse(acc, request, step.error, attempts = BUNKER_REPLY_ATTEMPTS)
    }

    /** Sends what the admission alone decided; the account key is not used to read anything. */
    @Suppress("NoComments")
    private suspend fun sendAdmissionReply(
        reply: Nip46AdmissionReply,
        acc: Account,
        request: AmberBunkerRequest,
        type: SignerType,
        amberEvent: AmberEvent?,
        relayUrl: String,
    ) {
        val response = when (reply) {
            is Nip46AdmissionReply.Ack -> {
                scope.launch {
                    accountRepository.getHistoryDatabase(acc.npub).dao().addHistory(
                        listOf(HistoryEntity(0, reply.connection.key, type.toString().lowercase(), amberEvent?.kind, TimeUtils.now(), true, content = amberEvent?.content ?: "")),
                        acc.npub,
                    )
                }
                Triple(BunkerResponse(request.request.id, reply.result, null), reply.relays, reply.attempts)
            }
            is Nip46AdmissionReply.Refuse -> {
                saveLog(reply.log, relayUrl, acc.npub)
                Triple(BunkerResponse(request.request.id, "", reply.error), reply.relays, reply.attempts)
            }
            is Nip46AdmissionReply.Drop -> {
                saveLog(reply.log, relayUrl, acc.npub)
                return
            }
        }
        val (bunkerResponse, relays, attempts) = response
        bunkerResponder.sendBunkerResponse(acc, request, bunkerResponse, relays, onLoading = {}, onDone = {}, attempts = attempts)
    }

    private fun generateMessage(
        type: SignerType,
        amberEvent: AmberEvent?,
        request: AmberBunkerRequest,
        event: Event,
        permission: ApplicationWithPermissions?,
        applicationWithSecret: ApplicationWithPermissions?,
    ): String {
        val name = sequenceOf(permission, applicationWithSecret)
            .map { savedNameSanitizedAgain(it) }
            .firstOrNull { it.isNotBlank() } ?: event.pubKey.toShortenHex()

        val bunkerPermission = Permission(type.toString().lowercase(), amberEvent?.kind)
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

    private fun savedNameSanitizedAgain(application: ApplicationWithPermissions?): String = sanitizeAppName(application?.application?.name ?: "")

    @Suppress("NoComments")
    private suspend fun getEncryptedDataKind(
        bunkerRequest: BunkerRequest?,
        acc: Account,
        url: String,
    ): EncryptedDataKind? {
        if (bunkerRequest == null) return null
        // V3 arrives as a generic BunkerRequest (Quartz has no dedicated type), so it
        // is matched by method name.
        if (bunkerRequest.method == "nip44v3_encrypt" || bunkerRequest.method == "nip44v3_decrypt") return nip44v3EncryptedDataKind(bunkerRequest, acc)
        if (bunkerRequest.method == "decrypt_zap_event") return privateZapPreview(bunkerRequest.zapRequestParam(), acc)
        val onParseError: (String) -> Unit = { saveLog(it, url, acc.npub) }
        return when (bunkerRequest) {
            is BunkerRequestNip44Decrypt -> acc.crypto.nip44Decrypt(bunkerRequest.ciphertext, bunkerRequest.pubKey).let {
                previewOf(it, bunkerRequest.ciphertext, it, acc, onParseError)
            }
            is BunkerRequestNip44Encrypt -> previewOf(bunkerRequest.message, bunkerRequest.message, acc.crypto.nip44Encrypt(bunkerRequest.message, bunkerRequest.pubKey), acc, onParseError)
            is BunkerRequestNip04Decrypt -> acc.crypto.nip04Decrypt(bunkerRequest.ciphertext, bunkerRequest.pubKey).let {
                previewOf(it, bunkerRequest.ciphertext, it, acc, onParseError)
            }
            is BunkerRequestNip04Encrypt -> previewOf(bunkerRequest.message, bunkerRequest.message, acc.crypto.nip04Encrypt(bunkerRequest.message, bunkerRequest.pubKey), acc, onParseError)
            else -> null
        }
    }

    private fun nip44v3EncryptedDataKind(
        bunkerRequest: BunkerRequest,
        acc: Account,
    ): EncryptedDataKind? {
        val kind = bunkerRequest.nip44v3Kind() ?: return null
        val scope = bunkerRequest.nip44v3Scope()
        val data = BunkerRequestUtils.getDataFromBunker(bunkerRequest)
        val pubKey = bunkerRequest.peerPubKeyParam() ?: return null
        return try {
            if (bunkerRequest.method == "nip44v3_encrypt") {
                val plainBytes = Base64.decode(data)
                val ciphertext = acc.crypto.nip44v3Encrypt(plainBytes, pubKey, kind, scope)
                ClearTextEncryptedDataKind(plainBytes.toString(Charsets.UTF_8), ciphertext)
            } else {
                val plainBytes = acc.crypto.nip44v3Decrypt(data, pubKey, kind, scope)
                ClearTextEncryptedDataKind(plainBytes.toString(Charsets.UTF_8), Base64.encode(plainBytes))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    @Suppress("NoComments")
    private fun validateNip44v3Request(
        type: SignerType,
        kind: Int?,
        scope: String,
        data: String,
        pubKey: String,
        acc: Account,
    ): String? {
        if (kind == null) return NIP44V3_KIND_REQUIRED
        if (type != SignerType.NIP44_V3_DECRYPT) return null
        return try {
            acc.crypto.nip44v3Decrypt(data, pubKey, kind, scope)
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // A device-lock or settings refusal is not a ciphertext problem:
            // let it through so the client gets the real condition instead of
            // being told the payload was corrupt.
            if (e is SecurityException || e is IllegalStateException) throw e
            NIP44V3_DECRYPT_FAILED
        }
    }

    fun notificationManager(): NotificationManager = ContextCompat.getSystemService(applicationContext, NotificationManager::class.java) as NotificationManager

    companion object {
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
