package com.greenart7c3.nostrsigner.desktop.core

import com.greenart7c3.nostrsigner.common.EncryptionType
import com.greenart7c3.nostrsigner.common.NIP44V3_DECRYPT_FAILED
import com.greenart7c3.nostrsigner.common.NIP46_INVALID_PARAMS
import com.greenart7c3.nostrsigner.common.Nip46Admission
import com.greenart7c3.nostrsigner.common.Nip46AdmissionReply
import com.greenart7c3.nostrsigner.common.Nip46Connection
import com.greenart7c3.nostrsigner.common.Nip46Step
import com.greenart7c3.nostrsigner.common.RELAY_AUTH_KIND
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.UNVERIFIED_REPLY_ATTEMPTS
import com.greenart7c3.nostrsigner.common.admitNip46Request
import com.greenart7c3.nostrsigner.common.bunkerFilters
import com.greenart7c3.nostrsigner.common.canRememberRelayAuth
import com.greenart7c3.nostrsigner.common.contentScopedSignerTypes
import com.greenart7c3.nostrsigner.common.hasRequiredParams
import com.greenart7c3.nostrsigner.common.localPubKeyFromPrivKey
import com.greenart7c3.nostrsigner.common.nip44v3Kind
import com.greenart7c3.nostrsigner.common.nip44v3Scope
import com.greenart7c3.nostrsigner.common.nip44v3SignerTypes
import com.greenart7c3.nostrsigner.common.nip46SignerType
import com.greenart7c3.nostrsigner.common.nip46StaleReason
import com.greenart7c3.nostrsigner.common.nip46Step
import com.greenart7c3.nostrsigner.common.parseRequestedPermissions
import com.greenart7c3.nostrsigner.common.permissionLookupOrder
import com.greenart7c3.nostrsigner.common.relayAuthHost
import com.greenart7c3.nostrsigner.common.relays.RelayHealthTracker
import com.greenart7c3.nostrsigner.common.relays.SenderRateLimiter
import com.greenart7c3.nostrsigner.common.reply
import com.greenart7c3.nostrsigner.common.sanitizeAppName
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.containsNip
import com.greenart7c3.nostrsigner.commonsui.kindToNip
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUsers
import com.vitorpamplona.quartz.nip04Dm.crypto.EncryptedInfo
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The saved connection that approving [req] for [accountNpub] would replace:
 * a nostrconnect:// connect whose client key is already saved. A bunker://
 * reconnect under the same key is the normal flow and is not a replacement.
 */
fun connectionToReplace(req: PendingBunkerRequest, accountNpub: String): AppWithPermissions? {
    if (!req.isNostrConnectUri || req.type != SignerType.CONNECT) return null
    return AmberDesktop.store(accountNpub).lookup.getByKey(req.localKey)
}

/**
 * A NIP-46 request waiting for the user's decision. The response payload is
 * precomputed when the request arrives (mirroring the Android flow, which
 * signs/encrypts up-front so the approval screen can preview the result).
 */
data class PendingBunkerRequest(
    val request: BunkerRequest,
    val type: SignerType,
    val account: DesktopAccount,
    val localKey: String,
    val relays: List<NormalizedRelayUrl>,
    val nostrConnectSecret: String = "",
    val appName: String = "",
    val appUrl: String = "",
    val appIcon: String = "",
    val requestedPermissions: List<Permission> = emptyList(),
    val kind: Int? = null,
    val preview: String = "",
    /** A seal in [preview], opened for the approval preview only; "" when there is none. */
    val sealPreview: String = "",
    val result: String = "",
    val encryptionType: EncryptionType = EncryptionType.NIP44,
    val isNostrConnectUri: Boolean = false,
    val signerPrivKey: String = "",
    /** What an NIP-04/NIP-44 encrypt/decrypt payload holds; drives its content-type permission. */
    val encryptedContent: EncryptedContent? = null,
    /** NIP-44 v3 context scope string (params[2]); the context kind is [kind]. */
    val nip44v3Scope: String = "",
    /** Relay auth (kind 22242) only: the "host[:port]" of the relay being authenticated to. */
    val relayHost: String = "",
    /**
     * Unix seconds after which the request is dropped from the queue, or null
     * to keep it until answered. NIP-46 gives no signal when a client stops
     * waiting, so relay requests get a generous TTL (or their NIP-40
     * expiration, if sooner) instead of lingering forever.
     */
    val expiresAt: Long? = null,
    /**
     * Set for NIP-5F local socket requests: answers the client directly over
     * its socket instead of a kind-24133 event (see [LocalSigner]).
     */
    val responder: (suspend (BunkerResponse) -> Unit)? = null,
    /** Socket connect only: hash of the secret the client receives once approved. */
    val socketSecretHash: String = "",
    /** Socket connect only: the client did not name an account, so any can be picked. */
    val allowAccountSwitch: Boolean = false,
) {
    val isLocalSocket: Boolean get() = responder != null

    /**
     * Whether a connect can be approved for a different account than the one
     * it arrived on: only when the client talks to a per-connection key (a
     * nostrconnect:// URI or a bunker:// connection key), not the account key.
     */
    val canSwitchAccount: Boolean
        get() = type == SignerType.CONNECT && (isNostrConnectUri || signerPrivKey.isNotEmpty() || allowAccountSwitch)

    /**
     * Kind a remembered choice is stored under. NIP-44 v3: this kind for
     * [EncryptionScope.SPECIFIC], or no kind (all kinds) for ALL, like Android.
     */
    fun permissionKind(scope: EncryptionScope): Int? = if (type in nip44v3SignerTypes && scope == EncryptionScope.ALL) null else kind

    /** A NIP-42 relay auth: granted per relay ([relayHost]) or for all relays, never per NIP. */
    val isRelayAuth: Boolean get() = type == SignerType.SIGN_EVENT && kind == RELAY_AUTH_KIND

    /** Whether the approval card offers a scope choice ("S" toggles it). */
    val hasScopeChoice: Boolean get() = type in contentScopedSignerTypes || type in nip44v3SignerTypes || isRelayAuth

    /**
     * Relay a remembered relay-auth choice is stored under, like Android:
     * this relay for [EncryptionScope.SPECIFIC], "*" (every relay) for ALL.
     */
    fun permissionRelay(scope: EncryptionScope): String = when {
        !isRelayAuth -> ""
        scope == EncryptionScope.ALL -> "*"
        else -> relayHost
    }

    /**
     * Scope the approval card starts on, like Android: NIP-44 v3 this kind
     * only, relay auth this relay only, NIP-04/NIP-44 the whole NIP.
     */
    val defaultScope: EncryptionScope
        get() = if (type in nip44v3SignerTypes || isRelayAuth) EncryptionScope.SPECIFIC else EncryptionScope.ALL

    /**
     * Whether a choice made with [scope] may be remembered: a "this relay
     * only" relay auth whose event names no relay is applied once only,
     * since a blank relay would read as a grant for every relay.
     */
    fun canRemember(scope: EncryptionScope): Boolean = !isRelayAuth || canRememberRelayAuth(scope == EncryptionScope.ALL, relayHost)

    /**
     * Queue identity for dedup/removal. Relay clients choose their own request
     * ids, so keying on the id alone would let one connection's queued request
     * swallow another connection's request with the same id; key on
     * (account, connection, id) instead. Socket requests already carry unique
     * `socket-…` ids.
     */
    fun isSameEntry(other: PendingBunkerRequest): Boolean = account.npub == other.account.npub && localKey == other.localKey && request.id == other.request.id

    /**
     * Stable UI identity of this queued entry: the same (account, connection,
     * id) triple [isSameEntry] dedups on. The client-chosen request id alone
     * is not unique across accounts or connections, so the UI must never key
     * selection or per-request choices on it.
     */
    val queueKey: String get() = "${account.npub}|$localKey|${request.id}"
}

private const val BUNKER_SUB_KEY = "bunker"
private const val MAX_REMEMBERED_EVENT_IDS = 512
private const val BACKOFF_RESET_AFTER_QUIET_MS = 60_000L
private val INITIAL_RECONNECT_DELAY = 5.seconds
private val MAX_RECONNECT_DELAY = 60.seconds

/**
 * Desktop port of `NotificationSubscription` + `EventNotificationConsumer` +
 * `BunkerRequestUtils`: keeps the kind-24133 subscriptions alive, auto-accepts
 * or auto-rejects per the stored permissions, and queues everything else in
 * [pending] for the approval UI.
 */
class BunkerEngine(
    val client: NostrClient,
    val scope: CoroutineScope,
) : RelayConnectionListener {
    val pending = kotlinx.coroutines.flow.MutableStateFlow<List<PendingBunkerRequest>>(emptyList())

    /** Emits when an approval's reply never reached the client and the approval was taken back. */
    val failedReplies = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val rateLimiter = SenderRateLimiter()

    private val subIds = ConcurrentHashMap<String, String>()
    private val filterMutex = Mutex()

    private var reconnectJob: Job? = null
    private var reconnectDelay = INITIAL_RECONNECT_DELAY
    private var lastDisconnectTime = 0L

    @Volatile
    private var connectionByPubKey: Map<String, Pair<String, String>> = emptyMap()

    private val seenEvents = object : LinkedHashMap<String, Long>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > MAX_REMEMBERED_EVENT_IDS
    }

    private var lockDeferredSince: Long? = null

    init {
        client.addConnectionListener(this)
    }

    override suspend fun onIncomingMessage(relay: IRelayClient, msgStr: String, msg: Message) {
        if (msg is EventMessage) {
            if (subIds.containsValue(msg.subId)) {
                scope.launch {
                    consume(msg.event, relay.url)
                }
            }
        }
        super.onIncomingMessage(relay, msgStr, msg)
    }

    override fun onCannotConnect(relay: IRelayClient, errorMessage: String) {
        AmberLogger.d("BunkerEngine", "onCannotConnect: ${relay.url.url} error: $errorMessage")
        logAll(relay, "onCannotConnect", errorMessage)

        if (System.currentTimeMillis() - RelayConnections.intentionalDisconnectTime < 2_000) {
            return super.onCannotConnect(relay, errorMessage)
        }

        scheduleReconnect(relay.url)
        return super.onCannotConnect(relay, errorMessage)
    }

    override fun onDisconnected(relay: IRelayClient) {
        AmberLogger.d("BunkerEngine", "onDisconnected: ${relay.url.url}")
        logAll(relay, "onDisconnected", "Disconnected")

        if (System.currentTimeMillis() - RelayConnections.intentionalDisconnectTime < 2_000) {
            return super.onDisconnected(relay)
        }

        scheduleReconnect(relay.url)
        return super.onDisconnected(relay)
    }

    override fun onConnected(relay: IRelayClient, pingMillis: Int, compressed: Boolean) {
        AmberLogger.d("BunkerEngine", "onConnected: ${relay.url.url} ping: ${pingMillis}ms")
        logAll(relay, "onConnected", "Connected")
        RelayHealthTracker.recordSuccess(relay.url)
        return super.onConnected(relay, pingMillis, compressed)
    }

    private fun scheduleReconnect(relay: NormalizedRelayUrl) {
        if (!RelayHealthTracker.recordFailure(relay)) {
            AmberLogger.d("BunkerEngine", "Relay ${relay.url} marked dead; skipping reconnect")
            return
        }
        reconnectWithBackoff()
    }

    private fun reconnectWithBackoff() {
        val now = System.currentTimeMillis()
        if (now - lastDisconnectTime > BACKOFF_RESET_AFTER_QUIET_MS) {
            reconnectDelay = INITIAL_RECONNECT_DELAY
        }
        lastDisconnectTime = now

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            AmberLogger.d("BunkerEngine", "Reconnecting in ${reconnectDelay.inWholeSeconds}s...")
            delay(reconnectDelay)
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY)
            RelayConnections.reconnect()
        }
    }

    private fun logAll(relay: IRelayClient, type: String, message: String) {
        scope.launch {
            AmberDesktop.accounts().forEach { account ->
                AmberDesktop.store(account.npub).addLog(relay.url.url, type, message)
            }
        }
    }

    fun start() {
        scope.launch {
            checkForNewRelaysAndUpdateAllFilters()
        }
    }

    /**
     * Mirrors `Amber.checkForNewRelaysAndUpdateAllFilters`: call after anything
     * that changes the relay set (apps added/removed, app or default relays
     * edited, accounts added/removed). Re-subscribes on the current relays —
     * Quartz then drops sockets no subscription needs any more and opens the
     * new ones — and wakes the client if it was idle.
     */
    suspend fun checkForNewRelaysAndUpdateAllFilters(shouldReconnect: Boolean = false) {
        if (PassphraseLock.isLocked()) return
        val wasActive = client.isActive()
        updateFilterLocked()
        client.connect()
        if (shouldReconnect) client.reconnect(wasActive)
    }

    /** Mirrors `NotificationSubscription.updateFilter`. */
    suspend fun updateFilter() {
        if (PassphraseLock.isLocked()) return
        updateFilterLocked()
    }

    @Suppress("NoComments")
    private suspend fun updateFilterLocked() = filterMutex.withLock {
        // One REQ per relay for every endpoint: https://github.com/greenart7c3/Amber/issues/644
        val keysByRelay = linkedMapOf<NormalizedRelayUrl, MutableSet<String>>()
        val indexEntries = mutableMapOf<String, Pair<String, String>>()
        val since = computeSince()

        AmberDesktop.accounts().forEach { account ->
            val store = AmberDesktop.store(account.npub)
            val allConnections = store.apps.value.map { it.app }
            val connectionsWithLocalKey = allConnections.filter { it.localKey.isNotEmpty() }

            for (conn in connectionsWithLocalKey) {
                val connPubKey = conn.localPubKey()
                indexEntries[connPubKey] = account.npub to conn.localKey

                conn.normalizedRelays().ifEmpty { BunkerRelays.savedRelays(account.npub).toList() }
                    .filterNot { RelayHealthTracker.isDead(it) }
                    .forEach { keysByRelay.getOrPut(it) { linkedSetOf() }.add(connPubKey) }
            }
        }

        // Routable before subscribing: a relay can deliver a new connection's first
        // request at once, and one whose key is still unknown is dropped. One
        // reference swap, so consume() never sees a half-filled index.
        connectionByPubKey = indexEntries.toMap()
        if (keysByRelay.isNotEmpty()) {
            client.subscribe(subIds.getOrPut(BUNKER_SUB_KEY) { UUID.randomUUID().toString() }, bunkerFilters(keysByRelay, since))
        }

        val staleSubKeys = subIds.keys.filter { it != BUNKER_SUB_KEY || keysByRelay.isEmpty() }
        for (subKey in staleSubKeys) {
            subIds.remove(subKey)?.let { subId ->
                client.unsubscribe(subId)
            }
        }
    }

    @Suppress("NoComments")
    internal fun computeSince(): Long = synchronized(seenEvents) {
        val now = TimeUtils.now()
        val latestSenderChosenCreatedAt = seenEvents.values.maxOrNull() ?: 0L
        // Never past the real clock: relays would drop legitimate requests.
        val since = if (latestSenderChosenCreatedAt > 0) minOf(latestSenderChosenCreatedAt, now) else now
        val deferred = lockDeferredSince
        lockDeferredSince = null
        if (deferred != null) minOf(since, deferred) else since
    }

    internal fun deferUntilUnlock(event: Event) {
        synchronized(seenEvents) {
            seenEvents.remove(event.id)
            lockDeferredSince = lockDeferredSince?.let { minOf(it, event.createdAt) } ?: event.createdAt
        }
    }

    /** Mirrors `EventNotificationConsumer.consume` + `notify`. */
    suspend fun consume(event: Event, relay: NormalizedRelayUrl) {
        if (!isNewRequest(event)) return
        val taggedKey = event.taggedUsers().firstOrNull() ?: return

        val (npub, connectionPrivKey) = connectionByPubKey[taggedKey.pubKey] ?: return
        val acc = AmberDesktop.account(npub) ?: return
        val store = AmberDesktop.store(acc.npub)
        if (isRateLimited(store, event, relay)) return
        store.addLog(relay.url, "bunker", "New event ${event.id} from ${event.pubKey}")

        val bunkerRequest = openRequest(store, event, relay, connectionPrivKey) ?: return
        store.addLog(relay.url, "bunker", "Request ${bunkerRequest.id} method ${bunkerRequest.method}")
        val encryptionType = if (EncryptedInfo.isNIP04(event.content)) EncryptionType.NIP04 else EncryptionType.NIP44
        routeRequest(bunkerRequest, event, acc, relay, encryptionType, connectionPrivKey)
    }

    private fun isNewRequest(event: Event): Boolean {
        if (PassphraseLock.isLocked() || event.kind != NostrConnectEvent.KIND) return false
        if (event.content.isEmpty() || !event.verify()) return false
        val stale = nip46StaleReason(event.id, event.createdAt, event.expiration(), TimeUtils.now())
        if (stale != null) {
            AmberLogger.d("BunkerEngine", stale)
            return false
        }
        return synchronized(seenEvents) { seenEvents.putIfAbsent(event.id, event.createdAt) == null }
    }

    /**
     * Unknown senders are rate-limited: their events force decrypt/log work and
     * signed error replies, so a flood must cost them silence. Stored connections
     * bypass the limiter entirely: an approved app legitimately bursts (bulk
     * NIP-44 decrypt batches, rapid signing) and must never be silently dropped.
     * Checked after verify() and keyed on the proven signer pubkey, so forged
     * events cannot drain a legitimate sender's budget.
     */
    @Suppress("NoComments")
    private fun isRateLimited(store: AccountStore, event: Event, relay: NormalizedRelayUrl): Boolean = store.lookup.getByKey(event.pubKey)?.takeUnless { it.app.isExpired() } == null &&
        !rateLimiter.allow("${relay.url}|${event.pubKey}", System.currentTimeMillis())

    private suspend fun openRequest(store: AccountStore, event: Event, relay: NormalizedRelayUrl, connectionPrivKey: String): BunkerRequest? {
        val decrypted = try {
            NostrSignerInternal(KeyPair(privKey = connectionPrivKey.hexToByteArray())).decrypt(event.content, event.pubKey)
        } catch (e: Exception) {
            store.addLog(relay.url, "bunker", "Decryption failed for event ${event.id}: ${e.message}")
            return null
        }
        return try {
            JacksonMapper.mapper.readValue(decrypted, BunkerRequest::class.java)
        } catch (e: Exception) {
            store.addLog(relay.url, "bunker", "Failed to parse request: ${e.message}")
            null
        }
    }

    private suspend fun routeRequest(
        bunkerRequest: BunkerRequest,
        event: Event,
        acc: DesktopAccount,
        relay: NormalizedRelayUrl,
        encryptionType: EncryptionType,
        connectionPrivKey: String,
    ) {
        val store = AmberDesktop.store(acc.npub)
        val responseRelay = listOf(relay)
        val type = nip46SignerType(bunkerRequest.method)
        val reply = UnadmittedReply(acc, connectionPrivKey, event.pubKey, encryptionType, bunkerRequest.id)
        if (refusedBeforeAdmission(bunkerRequest, type, reply, store, relay)) return

        val stored = store.lookup.getByKey(event.pubKey)
        if (stored != null && stored.app.isExpired()) {
            store.addLog(relay.url, "bunker", "Connection expired for ${event.pubKey}")
        }
        val connectSecret = (bunkerRequest as? BunkerRequestConnect)?.let { it.secret ?: UUID.randomUUID().toString() }
        val withSecret = connectSecret?.let { store.lookup.getBySecret(it) }
        val admission = admitNip46Request(type, stored?.toNip46Connection(), connectionPrivKey, connectSecret, withSecret?.toNip46Connection())
        if (admission is Nip46Admission.Admit) {
            handleRequest(bunkerRequest, event, acc, relay, connectionPrivKey, stored.takeIf { admission.stored != null }, withSecret.takeIf { admission.bySecret != null })
            return
        }
        when (val decided = admission.reply(event.pubKey, bunkerRequest.id, stored?.app?.normalizedRelays(), withSecret?.app?.normalizedRelays(), responseRelay)) {
            is Nip46AdmissionReply.Ack -> {
                store.addHistory(HistoryRecord(decided.connection.key, type.toString().lowercase(), null, TimeUtils.now(), true))
                val signerKey = connectionPrivKey.ifEmpty { decided.connection.localKey }
                sendResponse(acc, signerKey, event.pubKey, encryptionType, BunkerResponse(bunkerRequest.id, decided.result, null), decided.relays, decided.attempts)
            }
            is Nip46AdmissionReply.Refuse -> {
                store.addLog(relay.url, "bunker", decided.log)
                refuse(reply, decided.error, decided.relays)
            }
            is Nip46AdmissionReply.Drop -> store.addLog(relay.url, "bunker", decided.log)
            null -> Unit
        }
    }

    private class UnadmittedReply(
        val acc: DesktopAccount,
        val connectionPrivKey: String,
        val clientPubKey: String,
        val encryptionType: EncryptionType,
        val requestId: String,
    )

    private suspend fun refuse(reply: UnadmittedReply, message: String, relays: List<NormalizedRelayUrl>) {
        sendResponse(
            reply.acc,
            reply.connectionPrivKey,
            reply.clientPubKey,
            reply.encryptionType,
            BunkerResponse(reply.requestId, "", message),
            relays,
            attempts = UNVERIFIED_REPLY_ATTEMPTS,
        )
    }

    private suspend fun refusedBeforeAdmission(
        bunkerRequest: BunkerRequest,
        type: SignerType,
        reply: UnadmittedReply,
        store: AccountStore,
        relay: NormalizedRelayUrl,
    ): Boolean {
        val error = when {
            type == SignerType.INVALID -> "Unrecognized method: ${bunkerRequest.method}"
            !bunkerRequest.hasRequiredParams() -> NIP46_INVALID_PARAMS
            else -> return false
        }
        store.addLog(relay.url, "bunker", "Refusing request ${bunkerRequest.id} method ${bunkerRequest.method}: $error")
        refuse(reply, error, listOf(relay))
        return true
    }

    private suspend fun handleRequest(
        bunkerRequest: BunkerRequest,
        event: Event,
        acc: DesktopAccount,
        relay: NormalizedRelayUrl,
        connectionPrivKey: String,
        permission: AppWithPermissions?,
        applicationWithSecret: AppWithPermissions?,
    ) {
        val a = AdmittedRequest(bunkerRequest, event, acc, relay, connectionPrivKey, permission, app = permission ?: applicationWithSecret)
        when (val step = nip46Step(bunkerRequest, a.type, AmberDesktop.settings.authWhitelist, BunkerRelays.defaultRelays().map { it.url })) {
            is Nip46Step.SwitchRelays -> switchRelays(a, step.result)
            Nip46Step.Logout -> logout(a)
            is Nip46Step.Refuse -> refuseUpFront(a, step)
            Nip46Step.Decide -> answerOrQueue(a)
        }
    }

    @Suppress("LongParameterList")
    private class AdmittedRequest(
        val bunkerRequest: BunkerRequest,
        val event: Event,
        val acc: DesktopAccount,
        val relay: NormalizedRelayUrl,
        connectionPrivKey: String,
        val senderConnection: AppWithPermissions?,
        val app: AppWithPermissions?,
    ) {
        val type = nip46SignerType(bunkerRequest.method)
        val encryptionType = if (EncryptedInfo.isNIP04(event.content)) EncryptionType.NIP04 else EncryptionType.NIP44

        val signerPrivKey = connectionPrivKey.ifEmpty { app?.app?.localKey ?: "" }
        val relays = app?.app?.normalizedRelays() ?: listOf(relay)
        val store get() = AmberDesktop.store(acc.npub)

        val kind = when {
            bunkerRequest is BunkerRequestSign -> bunkerRequest.event.kind
            type in nip44v3SignerTypes -> bunkerRequest.nip44v3Kind()
            else -> null
        }
        val relayHost = if (bunkerRequest is BunkerRequestSign && kind == RELAY_AUTH_KIND) relayAuthHost(bunkerRequest.event.tags) else ""
    }

    private suspend fun reply(a: AdmittedRequest, result: String?, error: String? = null): Boolean = sendResponse(a.acc, a.signerPrivKey, a.event.pubKey, a.encryptionType, BunkerResponse(a.bunkerRequest.id, result, error), a.relays)

    private suspend fun switchRelays(a: AdmittedRequest, result: String?) {
        val current = a.senderConnection ?: return
        if (reply(a, result)) {
            a.store.upsert(current.copy(app = current.app.copy(relays = BunkerRelays.defaultRelays().map { it.url })))
            checkForNewRelaysAndUpdateAllFilters(shouldReconnect = true)
        }
    }

    private suspend fun logout(a: AdmittedRequest) {
        val current = a.senderConnection ?: return
        if (reply(a, Nip46Step.Logout.RESULT)) {
            a.store.delete(current.app.key)
            checkForNewRelaysAndUpdateAllFilters()
        }
    }

    private suspend fun refuseUpFront(a: AdmittedRequest, step: Nip46Step.Refuse) {
        a.store.addLog(a.relay.url, "bunker", step.log)
        if (step.recordRejected) a.store.addHistory(HistoryRecord(a.event.pubKey, a.type.toString(), a.kind, TimeUtils.now(), false))
        reply(a, "", step.error)
    }

    private suspend fun answerOrQueue(a: AdmittedRequest) {
        if (deferredByLockMidRequest(a.event)) return
        val computed = computeOrRefuse(a) ?: return

        val encryptedContent = if (a.type in contentScopedSignerTypes) EncryptedContent.classify(computed.preview) else null
        val permissionType = lookupPermission(a.store, a.event.pubKey, a.type, a.kind, encryptedContent, a.relayHost)
        when (storedDecision(a, permissionType)) {
            true -> {
                if (deferredByLockMidRequest(a.event)) return
                a.store.addHistory(HistoryRecord(a.event.pubKey, a.type.toString(), a.kind, TimeUtils.now(), true))
                a.app?.let { a.store.upsert(it.copy(app = it.app.copy(lastUsed = TimeUtils.now()))) }
                reply(a, computed.result)
            }
            false -> {
                a.store.addHistory(HistoryRecord(a.event.pubKey, a.type.toString(), a.kind, TimeUtils.now(), false))
                reply(a, "", "user rejected")
            }
            null -> addPending(pendingRequest(a, computed, encryptedContent))
        }
    }

    private fun deferredByLockMidRequest(event: Event): Boolean {
        if (!PassphraseLock.isLocked()) return false
        deferUntilUnlock(event)
        return true
    }

    @Suppress("NoComments")
    private fun storedDecision(a: AdmittedRequest, permissionType: AppPermissionRecord?): Boolean? {
        // A first-time connect always asks: approving it is what migrates the bunker
        // placeholder to the client key and grants the default permissions.
        if (a.type == SignerType.CONNECT) return null
        return isRemembered(a.app?.app?.signPolicy, permissionType)
    }

    @Suppress("NoComments")
    private suspend fun computeOrRefuse(a: AdmittedRequest): ComputedResult? = try {
        computeResult(a.bunkerRequest, a.type, a.acc)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        a.store.addLog(a.relay.url, "bunker", "Rejecting request that cannot be fulfilled: ${e.message}")
        // Generic, so a failed decrypt doesn't leak the ciphertext's embedded context.
        val error = if (a.type == SignerType.NIP44_V3_DECRYPT) NIP44V3_DECRYPT_FAILED else "could not process the request"
        reply(a, "", error)
        null
    }

    private fun pendingRequest(a: AdmittedRequest, computed: ComputedResult, encryptedContent: EncryptedContent?): PendingBunkerRequest {
        val request = a.bunkerRequest
        val name = a.app?.app?.name?.let(::sanitizeAppName)?.ifBlank { null } ?: a.event.pubKey.toShortenHex()
        return PendingBunkerRequest(
            request = request,
            type = a.type,
            account = a.acc,
            localKey = a.event.pubKey,
            relays = a.relays,
            appName = name,
            requestedPermissions = if (request is BunkerRequestConnect) parsePermissionsParam(request.permissions) else emptyList(),
            kind = a.kind,
            preview = computed.preview,
            sealPreview = computed.sealPreview,
            result = computed.result,
            encryptionType = a.encryptionType,
            signerPrivKey = a.signerPrivKey,
            encryptedContent = encryptedContent,
            nip44v3Scope = if (a.type in nip44v3SignerTypes) request.nip44v3Scope() else "",
            relayHost = a.relayHost,
            expiresAt = minOf(TimeUtils.now() + PENDING_TTL_SECONDS, a.event.expiration() ?: Long.MAX_VALUE),
        )
    }

    private fun parsePermissionsParam(permissions: String?): List<Permission> = parseRequestedPermissions(permissions.orEmpty()) { it.containsNip() }.map { Permission(it.type, it.kind) }

    companion object {
        /** How long a relay request waits for a decision before it is dropped. */
        const val PENDING_TTL_SECONDS = 10 * 60L
    }
}

/** Convenience accessor mirroring `ApplicationEntity.localPubKey`. */
fun AppRecord.localPubKey(): String = if (localKey.isNotEmpty()) localPubKeyFromPrivKey(localKey) else ""

private fun AppWithPermissions.toNip46Connection() = Nip46Connection(
    key = app.key,
    localKey = app.localKey,
    secret = app.secret,
    useSecret = app.useSecret,
    isConnected = app.isConnected,
    isExpired = app.isExpired(),
)

internal fun lookupPermission(
    store: AccountStore,
    appKey: String,
    type: SignerType,
    kind: Int?,
    encryptedContent: EncryptedContent?,
    relayHost: String,
): AppPermissionRecord? = when (type) {
    SignerType.SIGN_EVENT if kind == RELAY_AUTH_KIND -> store.lookup.getRelayAuthPermission(appKey, relayHost)

    else -> permissionLookupOrder(
        type,
        kind,
        contentType = if (type in contentScopedSignerTypes) type.contentPermissionType(encryptedContent) else null,
        signNip = if (type == SignerType.SIGN_EVENT) kind?.kindToNip()?.toIntOrNull() else null,
    ).firstNotNullOfOrNull { store.lookup.getPermission(appKey, it.type, it.kind) }
}
