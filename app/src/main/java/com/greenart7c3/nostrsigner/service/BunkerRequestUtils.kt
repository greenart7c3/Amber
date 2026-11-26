package com.greenart7c3.nostrsigner.service

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.SignerActivity
import com.greenart7c3.nostrsigner.common.BUNKER_REPLY_ATTEMPTS
import com.greenart7c3.nostrsigner.common.ConnectionReplyKey
import com.greenart7c3.nostrsigner.common.Grant
import com.greenart7c3.nostrsigner.common.Nip46ReplyKey
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.encryptionTextParam
import com.greenart7c3.nostrsigner.common.eventParam
import com.greenart7c3.nostrsigner.common.generateBunkerPrivKey
import com.greenart7c3.nostrsigner.common.nip44v3Kind
import com.greenart7c3.nostrsigner.common.nip44v3Payload
import com.greenart7c3.nostrsigner.common.nip44v3Scope
import com.greenart7c3.nostrsigner.common.nip44v3SignerTypes
import com.greenart7c3.nostrsigner.common.nip46ReplyLog
import com.greenart7c3.nostrsigner.common.nip46SignerType
import com.greenart7c3.nostrsigner.common.peerPubKeyParam
import com.greenart7c3.nostrsigner.common.psbtParam
import com.greenart7c3.nostrsigner.common.publishNip46Reply
import com.greenart7c3.nostrsigner.common.revertedConnectKey
import com.greenart7c3.nostrsigner.common.revertedGrants
import com.greenart7c3.nostrsigner.common.zapRequestParam
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.failed_to_connect_to_tor_orbot
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.warning
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.RelayClient
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.greenart7c3.nostrsigner.models.encryptDecryptSignerTypes
import com.greenart7c3.nostrsigner.models.encryptSignerTypes
import com.greenart7c3.nostrsigner.relays.AmberListenerSingleton
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.ToastManager
import com.greenart7c3.nostrsigner.ui.components.DecryptTypeScope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.collections.toSet
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object BunkerRequestUtils {
    val state = MutableStateFlow<ImmutableList<AmberBunkerRequest>>(persistentListOf())

    // Queued requests are identified by [AmberBunkerRequest.queueKey], never by
    // the client-chosen JSON-RPC id alone: clients using the same (e.g.
    // sequential) ids must not drop or replace each other's requests.
    fun addRequest(request: AmberBunkerRequest) {
        state.update { current ->
            if (current.any { it.queueKey == request.queueKey }) {
                current
            } else {
                (current + request).toPersistentList()
            }
        }
    }

    fun clearRequests() {
        state.tryEmit(persistentListOf())
    }

    fun remove(request: AmberBunkerRequest) {
        state.update { current ->
            current.filter { it.queueKey != request.queueKey }.toPersistentList()
        }
    }

    /**
     * Drops every queued request from [key] (a client whose app was deleted,
     * reset or logged out), so answering a stale card later cannot resurrect
     * the revoked connection. Cancels the request notifications when the
     * queue empties.
     */
    fun removeAllForClient(context: Context, key: String) {
        state.update { current ->
            current.filter { it.localKey != key }.toPersistentList()
        }
        if (state.value.isEmpty()) {
            context.getSystemService(NotificationManager::class.java).cancelAll()
        }
    }

    fun getBunkerRequests(): List<AmberBunkerRequest> = state.value

    /**
     * Relays to answer [bunkerRequest] on.
     *
     * A nostrconnect:// client listens only on the relays named in its URI, so those
     * always win, merged with any relays already saved for an app with the same key.
     * Otherwise, a saved app keeps its relays. A brand-new relay-delivered request
     * falls back to the default relays: it carries just the single relay that
     * delivered the event, which may be a write-restricted indexer/profile relay.
     */
    fun responseRelays(
        bunkerRequest: AmberBunkerRequest,
        savedRelays: List<NormalizedRelayUrl>?,
        defaultRelays: List<NormalizedRelayUrl>,
    ): List<NormalizedRelayUrl> {
        if (bunkerRequest.isNostrConnectUri && bunkerRequest.relays.isNotEmpty()) {
            return (bunkerRequest.relays + savedRelays.orEmpty()).distinct()
        }
        return savedRelays?.ifEmpty { defaultRelays } ?: defaultRelays
    }

    fun getDataFromBunker(bunkerRequest: BunkerRequest): String = when (bunkerRequest.method) {
        "connect" -> "ack"
        // Client-supplied: a missing or unparsable param yields "" rather than throwing.
        "sign_event" -> bunkerRequest.eventParam()?.let { runCatching { AmberEvent.fromJson(it).toEvent().toJson() }.getOrNull() } ?: ""
        "nip04_encrypt", "nip04_decrypt", "nip44_encrypt", "nip44_decrypt" -> bunkerRequest.encryptionTextParam()
        "decrypt_zap_event" -> bunkerRequest.zapRequestParam() ?: ""
        // NIP-44 v3 NIP-46 layout: [pubkey, kind, scope, payload]. For
        // `nip44v3_encrypt` the payload is base64-encoded plaintext; for
        // `nip44v3_decrypt` it is the v3 ciphertext.
        "nip44v3_encrypt", "nip44v3_decrypt" -> bunkerRequest.nip44v3Payload()
        "ping" -> "pong"
        "sign_psbt" -> bunkerRequest.psbtParam() ?: ""
        "logout" -> "ack"
        else -> ""
    }
}

/**
 * Answers bunker requests: signs or refuses, saves the decision, records history and
 * logs, and publishes the reply. Built from the app dependencies; the request queue
 * itself stays in [BunkerRequestUtils].
 */
// Constructor injection: one parameter per dependency.
@Suppress("LongParameterList")
class BunkerResponder(
    // The Application context: notifications and the error notification.
    private val appContext: Context,
    private val settingsStore: SettingsStore,
    private val accountRepository: AccountRepository,
    private val relayClient: RelayClient,
    private val scope: CoroutineScope,
    private val localBunkerRelay: LocalBunkerRelayServer,
    // The activity showing the requests, if any; finished once the queue empties.
    private val currentActivity: () -> Activity?,
) {
    /**
     * Removes [bunkerRequest] from the queue once it has been answered. Other requests
     * that are still waiting for approval are kept, so the notifications and the
     * activity are only dismissed when the queue is empty.
     */
    fun finishRequest(bunkerRequest: AmberBunkerRequest, closeApplication: Boolean) {
        BunkerRequestUtils.remove(bunkerRequest)
        if (BunkerRequestUtils.state.value.isNotEmpty()) return

        appContext.getSystemService(NotificationManager::class.java).cancelAll()
        val activity = currentActivity()
        activity?.intent = null
        if (closeApplication || activity is SignerActivity) {
            activity?.finishAndRemoveTask()
        }
    }

    suspend fun sendBunkerResponse(
        account: Account,
        bunkerRequest: AmberBunkerRequest,
        bunkerResponse: BunkerResponse,
        relays: List<NormalizedRelayUrl>,
        onLoading: (Boolean) -> Unit,
        onDone: (Boolean) -> Unit,
        attempts: Int = BUNKER_REPLY_ATTEMPTS,
    ) {
        if (relays.isEmpty()) {
            onDone(true)
            onLoading(false)
            accountRepository.getLogDatabase(account.npub).dao().insertLogIfEnabled(
                LogEntity(
                    id = 0,
                    url = bunkerRequest.localKey,
                    type = "bunker response",
                    message = "No permission created for this client. Please logout from the client and create a new bunker connection",
                    time = System.currentTimeMillis(),
                ),
            )
            return
        }

        if (settingsStore.settings.useProxy) {
            val isProxyWorking = relayClient.isSocksProxyAlive("127.0.0.1", settingsStore.settings.proxyPort)
            if (!isProxyWorking) {
                ToastManager.toast(getStringBlocking(Res.string.warning), getStringBlocking(Res.string.failed_to_connect_to_tor_orbot))
                onDone(false)
                onLoading(false)
                return
            }
        }

        scope.launch {
            // Never the result: for a decrypt request it is the plaintext.
            val sanitizedResponse = nip46ReplyLog(bunkerResponse)
            relays.forEach { relay ->
                accountRepository.getLogDatabase(account.npub).dao().insertLogIfEnabled(
                    LogEntity(
                        id = 0,
                        url = relay.url,
                        type = "bunker response",
                        message = sanitizedResponse,
                        time = System.currentTimeMillis(),
                    ),
                )
            }
        }

        AmberLog.d(Amber.TAG, "Sending response to relays ${relays.map { relay -> relay.url }} type ${bunkerRequest.request.method}")
        // The connection's own key when the request has one, else the account key.
        val key = bunkerRequest.signerPrivKey.takeIf { it.isNotEmpty() }?.let { runCatching { ConnectionReplyKey(it) }.getOrNull() } ?: account.replyKey()
        val success = publishNip46Reply(bunkerResponse, bunkerRequest.localKey, bunkerRequest.encryptionType, key, attempts) { event ->
            relayClient.client.publishAndConfirm(event = event, relayList = relays.toSet(), timeoutInSeconds = 5, untilFirstAccept = true)
        }

        if (success) {
            AmberLog.d(Amber.TAG, "Success response to relays ${relays.map { relay -> relay.url }} type ${bunkerRequest.request.method}")
            onDone(true)
        } else {
            onDone(false)
            AmberListenerSingleton.showErrorMessage(appContext)
            AmberLog.d(Amber.TAG, "Failed response to relays ${relays.map { relay -> relay.url }} type ${bunkerRequest.request.method}")
        }
        onLoading(false)
    }

    // The account key, for a connection from before per-connection keys; it checks key access first.
    private fun Account.replyKey(): Nip46ReplyKey = object : Nip46ReplyKey {
        override suspend fun nip04Encrypt(plainText: String, toPubKey: String): String = this@replyKey.crypto.nip04Encrypt(plainText, toPubKey)

        override suspend fun nip44Encrypt(plainText: String, toPubKey: String): String = this@replyKey.crypto.nip44Encrypt(plainText, toPubKey)

        override fun sign(createdAt: Long, kind: Int, tags: Array<Array<String>>, content: String): Event = signSync(createdAt, kind, tags, content)
    }

    fun sendResult(
        account: Account,
        key: String,
        response: String,
        bunkerRequest: AmberBunkerRequest,
        kind: Int?,
        onLoading: (Boolean) -> Unit,
        permissions: List<Permission>? = null,
        appName: String? = null,
        signPolicy: Int? = null,
        shouldCloseApplication: Boolean? = null,
        rememberType: RememberType,
        oldKey: String = "",
        deleteAfter: Long = 0L,
        relay: String = "",
        encryptedData: EncryptedDataKind? = null,
        decryptTypeScope: DecryptTypeScope = DecryptTypeScope.ALL,
    ) {
        onLoading(true)
        scope.launch {
            val dao = accountRepository.daos(account.npub).permissionWriteDao
            val savedApplication = savedConnection(account, key, bunkerRequest, oldKey, deleteAfter)
            val relays = replyRelays(savedApplication, bunkerRequest)
            // Approving changes the grants in place; kept to take the approval back.
            val grantsBefore = savedApplication?.permissions?.map { it.toGrant() }.orEmpty()

            if (savedApplication == null && bunkerRequest.request !is BunkerRequestConnect) {
                // A queued request whose app row is gone was revoked while it
                // waited: answer with an error instead of recreating the
                // revoked connection with fresh grants.
                finishRequest(bunkerRequest, bunkerRequest.closeApplication)
                AmberUtils.sendBunkerError(this@BunkerResponder, account, bunkerRequest, relays, closeApplication = true, onLoading = onLoading)
                return@launch
            }

            var application = savedApplication ?: newApplication(
                key,
                appName ?: "",
                relays,
                account,
                bunkerRequest,
                shouldCloseApplication ?: bunkerRequest.closeApplication,
                deleteAfter,
            )
            application.application.isConnected = true
            application = withConnectDetails(application, bunkerRequest, relays, isNew = savedApplication == null)

            finishRequest(bunkerRequest, application.application.closeApplication)

            delay(500.milliseconds)

            if (signPolicy != null) {
                AmberUtils.configureSignPolicy(application, signPolicy, key, permissions)
            }

            val type = nip46SignerType(bunkerRequest.request.method)
            if (rememberType != RememberType.NEVER) {
                AmberUtils.acceptPermission(
                    application = application,
                    key = key,
                    type = type,
                    kind = kind,
                    rememberType = rememberType,
                    relay = relay,
                    encryptedData = encryptedData ?: bunkerRequest.encryptedData,
                    decryptTypeScope = decryptTypeScope,
                )
            }

            val didChangeRelays = type == SignerType.CONNECT && applyConnect(account, application, key, shouldCloseApplication, relays)

            // assume that everything worked and try to revert it if it fails
            val grantsAfter = application.permissions.map { it.toGrant() }
            dao.insertApplicationWithPermissions(application)
            recordApproval(account, key, type, kind, response, bunkerRequest)
            publishApproval(account, application, bunkerRequest, BunkerResponse(bunkerRequest.request.id, response, null), relays, didChangeRelays, onLoading) { published ->
                if (!published) {
                    scope.launch {
                        if (rememberType != RememberType.NEVER) revertApproval(account, application, bunkerRequest, grantsBefore, grantsAfter)
                        onLoading(false)
                    }
                }
            }
        }
    }

    // The saved connection an approval of [bunkerRequest] updates, after dropping the one it
    // replaces ([oldKey]) and attaching a bunker:// connection to the client's key.
    private suspend fun savedConnection(
        account: Account,
        key: String,
        bunkerRequest: AmberBunkerRequest,
        oldKey: String,
        deleteAfter: Long,
    ): ApplicationWithPermissions? {
        val daos = accountRepository.daos(account.npub)
        if (oldKey.isNotBlank()) {
            daos.applicationWriteDao.delete(oldKey)
            accountRepository.getHistoryDatabase(account.npub).dao().deleteHistory(oldKey)
        }
        attachBunkerConnection(account, key, bunkerRequest)
        val saved = daos.applicationDao.getByKey(key)
        if (bunkerRequest.request is BunkerRequestConnect) {
            saved?.application?.deleteAfter = deleteAfter
        }
        return saved
    }

    // Where the reply goes. A brand-new connection that names no relays of its own also
    // gets the local relay beside the default relays (desktop BunkerEngine does the
    // same), so clients on this device need no public relay.
    private fun replyRelays(saved: ApplicationWithPermissions?, bunkerRequest: AmberBunkerRequest): List<NormalizedRelayUrl> {
        val fallbackRelays = if (saved == null && bunkerRequest.request is BunkerRequestConnect) {
            localBunkerRelay.newBunkerRelays()
        } else {
            settingsStore.settings.defaultRelays
        }
        return BunkerRequestUtils.responseRelays(bunkerRequest, saved?.application?.relays, fallbackRelays)
    }

    // What approving a connect sets: the default grants, connected, and the close-app choice.
    // Returns whether the connection brings relays Amber does not listen on yet.
    private suspend fun applyConnect(
        account: Account,
        application: ApplicationWithPermissions,
        key: String,
        shouldCloseApplication: Boolean?,
        relays: List<NormalizedRelayUrl>,
    ): Boolean {
        accountRepository.daos(account.npub).permissionWriteDao.deletePermissions(key)
        application.application.isConnected = true
        shouldCloseApplication?.let {
            application.application.closeApplication = it
        }
        addConnectDefaults(application, key)
        // check if application has any relay not in saved relays
        val savedRelays = accountRepository.getSavedRelays(account)
        return relays.any { !savedRelays.contains(it) }
    }

    @Suppress("LongParameterList") // The approved reply and where it goes, passed through from sendResult.
    private suspend fun publishApproval(
        account: Account,
        application: ApplicationWithPermissions,
        bunkerRequest: AmberBunkerRequest,
        response: BunkerResponse,
        relays: List<NormalizedRelayUrl>,
        didChangeRelays: Boolean,
        onLoading: (Boolean) -> Unit,
        onDone: (Boolean) -> Unit,
    ) {
        // A new `nostrconnect://` approval always mints a fresh `localKey`
        // for the connection (see `generateBunkerPrivKey()` above). The
        // corresponding listening REQ (kind 24133, #p=localPubKey) must be
        // registered on the relay *before* we publish the connect response
        // — otherwise the client's follow-up `get_public_key` RPC can
        // arrive at the relay while we have no subscriber for it (the
        // event is ephemeral, so the relay drops it), and the client
        // times out waiting for a response.
        //
        // Previously this only ran when `didChangeRelays`, so CONNECTs
        // against an already-configured relay left the listening sub to
        // be picked up by the 30-second `ConnectivityService` timer — a
        // window in which the pool could idle-disconnect the relay and
        // lose any RPC published by the client in the meantime.
        if (nip46SignerType(bunkerRequest.request.method) == SignerType.CONNECT) {
            relayClient.checkForNewRelaysAndUpdateAllFilters(didChangeRelays)
        }
        relayClient.client.connect()

        val signerPrivKey = application.application.localKey
        val bunkerRequestWithKey = if (signerPrivKey.isNotEmpty() && bunkerRequest.signerPrivKey.isEmpty()) {
            bunkerRequest.copy(signerPrivKey = signerPrivKey)
        } else {
            bunkerRequest
        }
        sendBunkerResponse(account, bunkerRequestWithKey, response, application.application.relays.ifEmpty { relays }, onLoading, onDone)
    }

    // A bunker:// connection is saved under its secret until a client connects with
    // it: move that row (and its grants) to the client's key.
    private suspend fun attachBunkerConnection(account: Account, key: String, bunkerRequest: AmberBunkerRequest) {
        val daos = accountRepository.daos(account.npub)
        if (daos.applicationDao.getByKey(key) != null) return
        val secret = (bunkerRequest.request as? BunkerRequestConnect)?.secret?.takeIf { it.isNotBlank() } ?: return
        val saved = daos.applicationDao.getByKey(secret) ?: return
        val attached = ApplicationWithPermissions(
            application = saved.application.copy(key = key),
            permissions = saved.permissions.map { it.copy() }.toMutableList(),
        )
        daos.applicationWriteDao.delete(secret)
        daos.permissionWriteDao.insertApplicationWithPermissions(attached)
    }

    // The row of a connection seen for the first time.
    private fun newApplication(
        key: String,
        name: String,
        relays: List<NormalizedRelayUrl>,
        account: Account,
        bunkerRequest: AmberBunkerRequest,
        closeApplication: Boolean,
        deleteAfter: Long,
        withClientMetadata: Boolean = true,
    ): ApplicationWithPermissions {
        val secret = if (bunkerRequest.request is BunkerRequestConnect) bunkerRequest.request.secret ?: "" else ""
        val metadata = bunkerRequest.clientMetadata.takeIf { withClientMetadata }
        return ApplicationWithPermissions(
            application = ApplicationEntity(
                key,
                name,
                relays,
                metadata?.url ?: "",
                metadata?.image ?: "",
                "",
                account.hexKey,
                true,
                secret,
                secret.isNotBlank(),
                account.signPolicy,
                closeApplication,
                deleteAfter = deleteAfter,
                lastUsed = TimeUtils.now(),
            ),
            permissions = mutableListOf(),
        )
    }

    /**
     * What a `connect` sets on [application]: its own signing key when it is [isNew],
     * the relays a nostrconnect URI listens on, and the client metadata
     * (nostr-protocol/nips#2381). The bunker:// application is created up-front when
     * its URI is generated, so it is usually not new here: the metadata is updated in
     * place so it is stored on every connect.
     */
    private fun withConnectDetails(
        application: ApplicationWithPermissions,
        bunkerRequest: AmberBunkerRequest,
        relays: List<NormalizedRelayUrl>,
        isNew: Boolean,
    ): ApplicationWithPermissions {
        if (bunkerRequest.request !is BunkerRequestConnect) return application
        var result = application
        // Ensure each connection has its own unique signing key
        if (result.application.localKey.isBlank() && isNew) {
            result = result.copy(application = result.application.copy(localKey = generateBunkerPrivKey()))
        }
        // The client only listens on the relays in its URI; an app saved
        // earlier with empty or stale relays must not send the reply elsewhere.
        if (bunkerRequest.isNostrConnectUri && result.application.relays != relays) {
            result = result.copy(application = result.application.copy(relays = relays))
        }
        bunkerRequest.clientMetadata?.let { metadata ->
            result = result.copy(
                application = result.application.copy(
                    name = metadata.name.ifBlank { result.application.name },
                    url = metadata.url.ifBlank { result.application.url },
                    icon = metadata.image.ifBlank { result.application.icon },
                ),
            )
        }
        return result
    }

    // A connection may always ask for the public key and ping.
    private fun addConnectDefaults(application: ApplicationWithPermissions, key: String) {
        listOf(SignerType.GET_PUBLIC_KEY, SignerType.PING).forEach { type ->
            if (application.permissions.none { it.type == type.toString() }) {
                application.permissions.add(
                    ApplicationPermissionsEntity(null, key, type.toString(), null, true, RememberType.ALWAYS.screenCode, Long.MAX_VALUE / 1000, 0),
                )
            }
        }
    }

    private suspend fun recordApproval(
        account: Account,
        key: String,
        type: SignerType,
        kind: Int?,
        response: String,
        bunkerRequest: AmberBunkerRequest,
    ) {
        val isV3 = type in nip44v3SignerTypes
        // Persist the encrypted form (ciphertext), never the plaintext: encrypt
        // requests store their ciphertext output (`response`), decrypt requests
        // store the ciphertext input that arrived in the request. The plaintext
        // is recovered on demand in the activity/history UI.
        val historyContent = when (type) {
            SignerType.NIP04_ENCRYPT,
            SignerType.NIP44_ENCRYPT,
            SignerType.NIP44_V3_ENCRYPT,
            SignerType.SIGN_EVENT,
            -> response
            else -> BunkerRequestUtils.getDataFromBunker(bunkerRequest.request)
        }
        accountRepository.getHistoryDatabase(account.npub).dao().addHistory(
            listOf(
                HistoryEntity(
                    0,
                    key,
                    type.toString(),
                    if (isV3) bunkerRequest.request.nip44v3Kind() else kind,
                    TimeUtils.now(),
                    true,
                    content = historyContent,
                    encryptionPubKey = if (type in encryptDecryptSignerTypes && type != SignerType.DECRYPT_ZAP_EVENT) {
                        bunkerRequest.request.peerPubKeyParam() ?: ""
                    } else {
                        ""
                    },
                    encryptionScope = if (isV3) bunkerRequest.request.nip44v3Scope() else "",
                ),
            ),
            account.npub,
        )
    }

    /**
     * The reply did not reach the client: take back what approving it remembered (the
     * common `revertedConnectKey` / `revertedGrants`, as on the desktop). A connect drops
     * the connection (a bunker:// one waits again under its secret); any other request
     * removes exactly the grants it added and restores the ones it replaced, re-reading
     * them so a change made while the reply was retried survives. It used to delete every
     * grant of the request's type, which could remove a grant from before the approval
     * and keep the content-type one it had just added.
     */
    private suspend fun revertApproval(
        account: Account,
        application: ApplicationWithPermissions,
        bunkerRequest: AmberBunkerRequest,
        grantsBefore: List<Grant>,
        grantsAfter: List<Grant>,
    ) {
        val daos = accountRepository.daos(account.npub)
        val key = application.application.key
        if (nip46SignerType(bunkerRequest.request.method) == SignerType.CONNECT) {
            daos.applicationWriteDao.delete(application.application)
            revertedConnectKey(bunkerRequest.isNostrConnectUri, application.application.secret)?.let { secret ->
                daos.permissionWriteDao.insertApplicationWithPermissions(application.copy(application = application.application.copy(isConnected = false, key = secret)))
            }
            return
        }
        val current = daos.applicationDao.getByKey(key) ?: return
        val grants = revertedGrants(current.permissions.map { it.toGrant() }, grantsBefore, grantsAfter)
        daos.permissionWriteDao.insertApplicationWithPermissions(current.copy(permissions = grants.map { it.toEntity(key) }.toMutableList()))
    }

    fun sendRejection(
        key: String,
        account: Account,
        bunkerRequest: AmberBunkerRequest,
        appName: String,
        rememberType: RememberType,
        signerType: SignerType,
        kind: Int?,
        onLoading: (Boolean) -> Unit,
        relay: String = "",
        decryptTypeScope: DecryptTypeScope = DecryptTypeScope.ALL,
    ) {
        onLoading(true)
        scope.launch(Dispatchers.IO) {
            val savedApplication = accountRepository.daos(account.npub).applicationDao.getByKey(key)
            val defaultRelays = settingsStore.settings.defaultRelays
            val relays = BunkerRequestUtils.responseRelays(bunkerRequest, savedApplication?.application?.relays, defaultRelays)

            if (savedApplication == null && bunkerRequest.request !is BunkerRequestConnect) {
                // A queued request whose app row is gone was revoked while it
                // waited: reject it without recreating the connection.
                finishRequest(bunkerRequest, closeApplication = true)
                AmberUtils.sendBunkerError(this@BunkerResponder, account, bunkerRequest, relays, closeApplication = true, onLoading = onLoading)
                return@launch
            }

            val application = savedApplication
                ?: newApplication(key, appName, relays, account, bunkerRequest, bunkerRequest.closeApplication, 0L, withClientMetadata = false)

            // Use connection-specific key if available; fall back to what's in the request
            val effectiveSignerPrivKey = application.application.localKey.ifEmpty { bunkerRequest.signerPrivKey }
            val effectiveBunkerRequest = if (effectiveSignerPrivKey.isNotEmpty() && bunkerRequest.signerPrivKey != effectiveSignerPrivKey) {
                bunkerRequest.copy(signerPrivKey = effectiveSignerPrivKey)
            } else {
                bunkerRequest
            }

            finishRequest(effectiveBunkerRequest, application.application.closeApplication)

            // Bookkeeping must not cost the client its answer: a failure here
            // (e.g. a malformed request) used to skip the error reply below.
            try {
                recordRejection(account, application, key, bunkerRequest, rememberType, signerType, kind, relay, decryptTypeScope)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AmberLog.e(Amber.TAG, "Could not record the rejection of ${bunkerRequest.request.id}", e)
            }

            AmberUtils.sendBunkerError(
                this@BunkerResponder,
                account,
                effectiveBunkerRequest,
                relays,
                closeApplication = application.application.closeApplication,
                onLoading = onLoading,
            )
        }
    }

    @Suppress("LongParameterList") // The rejection's whole context, passed through from sendRejection.
    private suspend fun recordRejection(
        account: Account,
        application: ApplicationWithPermissions,
        key: String,
        bunkerRequest: AmberBunkerRequest,
        rememberType: RememberType,
        signerType: SignerType,
        kind: Int?,
        relay: String,
        decryptTypeScope: DecryptTypeScope,
    ) {
        if (rememberType != RememberType.NEVER) {
            AmberUtils.acceptOrRejectPermission(
                application,
                key,
                signerType,
                kind,
                false,
                rememberType,
                relay,
                bunkerRequest.encryptedData,
                decryptTypeScope,
                dao = accountRepository.daos(account.npub).permissionWriteDao,
            )
        }
        if (bunkerRequest.request is BunkerRequestConnect) return

        accountRepository.daos(account.npub).permissionWriteDao.insertApplicationWithPermissions(application)
        val isV3 = signerType in nip44v3SignerTypes
        // A rejected encrypt request was never performed, so no ciphertext
        // exists — store nothing rather than leaking the plaintext input.
        // Decrypt requests carry their ciphertext in the request, so it is
        // safe to persist and decrypt on demand.
        val historyContent = if (signerType in encryptSignerTypes) "" else BunkerRequestUtils.getDataFromBunker(bunkerRequest.request)
        accountRepository.getHistoryDatabase(account.npub).dao().addHistory(
            listOf(
                HistoryEntity(
                    0,
                    key,
                    signerType.toString(),
                    if (isV3) bunkerRequest.request.nip44v3Kind() else null,
                    TimeUtils.now(),
                    false,
                    content = historyContent,
                    encryptionPubKey = if (historyContent.isNotEmpty() && signerType in encryptDecryptSignerTypes && signerType != SignerType.DECRYPT_ZAP_EVENT) {
                        bunkerRequest.request.peerPubKeyParam() ?: ""
                    } else {
                        ""
                    },
                    encryptionScope = if (isV3) bunkerRequest.request.nip44v3Scope() else "",
                ),
            ),
            account.npub,
        )
    }
}
