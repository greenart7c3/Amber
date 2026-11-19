package com.greenart7c3.nostrsigner.desktop.core

import com.greenart7c3.nostrsigner.common.BUNKER_REPLY_ATTEMPTS
import com.greenart7c3.nostrsigner.common.EncryptionType
import com.greenart7c3.nostrsigner.common.Nip46Admission
import com.greenart7c3.nostrsigner.common.Nip46Connection
import com.greenart7c3.nostrsigner.common.NostrConnectUri
import com.greenart7c3.nostrsigner.common.RELAY_AUTH_KIND
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.UNVERIFIED_REPLY_ATTEMPTS
import com.greenart7c3.nostrsigner.common.admitNip46Request
import com.greenart7c3.nostrsigner.common.canRememberRelayAuth
import com.greenart7c3.nostrsigner.common.generateBunkerPrivKey
import com.greenart7c3.nostrsigner.common.hasAmbiguousRelayTags
import com.greenart7c3.nostrsigner.common.isBlockedByAuthWhitelist
import com.greenart7c3.nostrsigner.common.localPubKeyFromPrivKey
import com.greenart7c3.nostrsigner.common.nip44v3Kind
import com.greenart7c3.nostrsigner.common.nip44v3Scope
import com.greenart7c3.nostrsigner.common.nip46SignerType
import com.greenart7c3.nostrsigner.common.nip46StaleReason
import com.greenart7c3.nostrsigner.common.permissionLookupOrder
import com.greenart7c3.nostrsigner.common.relayAuthHost
import com.greenart7c3.nostrsigner.common.relays.RelayHealthTracker
import com.greenart7c3.nostrsigner.common.relays.SenderRateLimiter
import com.greenart7c3.nostrsigner.common.retryWithBackoff
import com.greenart7c3.nostrsigner.common.sanitizeAppName
import com.greenart7c3.nostrsigner.common.switchRelaysResult
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.containsNip
import com.greenart7c3.nostrsigner.commonsui.kindToNip
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
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
import kotlinx.coroutines.flow.update
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
    return AmberDesktop.store(accountNpub).getByKey(req.localKey)
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

    // An approval whose reply never reached the client, and was taken back; the UI says so.
    val failedReplies = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    // Per-sender token bucket: bounds the work (decrypt, log writes, signed
    // replies) one relay sender can force per window.
    private val rateLimiter = SenderRateLimiter()

    // Concurrent: mutated in updateFilterLocked while relay I/O threads read it
    // in onIncomingMessage (containsValue).
    private val subIds = ConcurrentHashMap<String, String>()
    private val filterMutex = Mutex()

    // Mirrors `NostrClientLoggerListener`: backoff starts at 5s, doubles up to
    // 60s, and resets after a quiet minute. A single job per engine replaces
    // any pending reconnect when a relay drops again.
    private var reconnectJob: Job? = null
    private var reconnectDelay = 5.seconds
    private var lastDisconnectTime = 0L

    /** localPubKey -> (npub, localPrivKey), mirrors `LocalKeyAccountIndex`. */
    @Volatile
    private var localKeyIndex: Map<String, Pair<String, String>> = emptyMap()

    /** Recently processed event ids -> createdAt, for dedup + `since` computation. */
    private val seenEvents = object : LinkedHashMap<String, Long>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 512
    }

    /** Oldest createdAt of a request deferred by a mid-request lock; guarded by [seenEvents]. */
    private var deferredSince: Long? = null

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

        if (System.currentTimeMillis() - AmberDesktop.intentionalDisconnectTime < 2_000) {
            return super.onCannotConnect(relay, errorMessage)
        }

        scheduleReconnect(relay.url)
        return super.onCannotConnect(relay, errorMessage)
    }

    override fun onDisconnected(relay: IRelayClient) {
        AmberLogger.d("BunkerEngine", "onDisconnected: ${relay.url.url}")
        logAll(relay, "onDisconnected", "Disconnected")

        if (System.currentTimeMillis() - AmberDesktop.intentionalDisconnectTime < 2_000) {
            return super.onDisconnected(relay)
        }

        scheduleReconnect(relay.url)
        return super.onDisconnected(relay)
    }

    override fun onConnected(relay: IRelayClient, pingMillis: Int, compressed: Boolean) {
        AmberLogger.d("BunkerEngine", "onConnected: ${relay.url.url} ping: ${pingMillis}ms")
        logAll(relay, "onConnected", "Connected")
        // Relay recovered: clear its failure streak so it is eligible for the
        // normal reconnect-with-backoff path (and the subscription) again.
        RelayHealthTracker.recordSuccess(relay.url)
        return super.onConnected(relay, pingMillis, compressed)
    }

    // Counts the failure against the relay and only schedules a reconnect
    // while it is still worth retrying. Once a relay is dead,
    // RelayHealthTracker also makes updateFilterLocked drop it from the
    // subscription relay set, so Quartz stops opening sockets to it on every
    // refresh. The streak resets on a successful connection (onConnected) or
    // a network change / manual reconnect.
    private fun scheduleReconnect(relay: NormalizedRelayUrl) {
        if (!RelayHealthTracker.recordFailure(relay)) {
            AmberLogger.d("BunkerEngine", "Relay ${relay.url} marked dead; skipping reconnect")
            return
        }
        reconnectWithBackoff()
    }

    private fun reconnectWithBackoff() {
        val now = System.currentTimeMillis()
        if (now - lastDisconnectTime > 60_000) {
            reconnectDelay = 5.seconds
        }
        lastDisconnectTime = now

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            AmberLogger.d("BunkerEngine", "Reconnecting in ${reconnectDelay.inWholeSeconds}s...")
            delay(reconnectDelay)
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(60.seconds)
            AmberDesktop.reconnect()
        }
    }

    /** Mirrors `NostrClientLoggerListener.saveLog`: every account's relay log. */
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

    private suspend fun updateFilterLocked() = filterMutex.withLock {
        val pendingSubscriptions = linkedMapOf<String, Map<NormalizedRelayUrl, List<Filter>>>()
        val indexEntries = mutableMapOf<String, Pair<String, String>>()
        // Once per update: it also hands over (and clears) a lock-deferred `since`.
        val since = computeSince()

        AmberDesktop.accounts().forEach { account ->
            val store = AmberDesktop.store(account.npub)
            val allConnections = store.apps.value.map { it.app }
            // Every NIP-46 connection has its own key; the account key itself is
            // never an endpoint, so nothing subscribes to it.
            val connectionsWithLocalKey = allConnections.filter { it.localKey.isNotEmpty() }

            for (conn in connectionsWithLocalKey) {
                val connPubKey = conn.localPubKey()
                indexEntries[connPubKey] = account.npub to conn.localKey
                val subKey = "${account.hexKey}_$connPubKey"

                val connRelays = conn.normalizedRelays().ifEmpty { AmberDesktop.savedRelays(account.npub).toList() }
                    .filterNot { RelayHealthTracker.isDead(it) }
                if (connRelays.isEmpty()) continue

                pendingSubscriptions[subKey] = connRelays.associateWith {
                    listOf(
                        Filter(
                            kinds = listOf(NostrConnectEvent.KIND),
                            tags = mapOf("p" to listOf(connPubKey)),
                            limit = 1,
                            since = since,
                        ),
                    )
                }
            }
        }

        // Mirrors NotificationSubscription: make every endpoint routable before
        // any subscription can deliver a request, otherwise the first request
        // of a new connection can arrive while its key is still unknown and is
        // dropped. One reference swap, so consume() never sees a half-filled index.
        localKeyIndex = indexEntries.toMap()
        pendingSubscriptions.forEach { (key, filters) ->
            client.subscribe(subIds.getOrPut(key) { UUID.randomUUID().toString() }, filters)
        }

        val staleSubKeys = subIds.keys.filter { it !in pendingSubscriptions }
        for (subKey in staleSubKeys) {
            subIds.remove(subKey)?.let { subId ->
                client.unsubscribe(subId)
            }
        }
    }

    internal fun computeSince(): Long = synchronized(seenEvents) {
        val now = TimeUtils.now()
        val latest = seenEvents.values.maxOrNull() ?: 0L
        // seenEvents holds sender-chosen created_at values; never let one push
        // `since` past the real clock (relays would drop legitimate requests).
        val since = if (latest > 0) minOf(latest, now) else now
        // Reach back to a request a lock deferred, so the relays redeliver it.
        val deferred = deferredSince
        deferredSince = null
        if (deferred != null) minOf(since, deferred) else since
    }

    /**
     * The passphrase lock came down while [event] was being handled. Forget it
     * and keep the next subscription's `since` at or before it, so the relays
     * redeliver it after unlock instead of the request being dropped unanswered
     * (Android likewise leaves locked requests unopened for redelivery).
     */
    internal fun deferUntilUnlock(event: Event) {
        synchronized(seenEvents) {
            seenEvents.remove(event.id)
            deferredSince = deferredSince?.let { minOf(it, event.createdAt) } ?: event.createdAt
        }
    }

    /** Mirrors `EventNotificationConsumer.consume` + `notify`. */
    suspend fun consume(event: Event, relay: NormalizedRelayUrl) {
        if (!isNewRequest(event)) return
        val taggedKey = event.taggedUsers().firstOrNull() ?: return

        // Only a connection's own key routes a request; one tagged to an
        // account key is not addressed to any connection and is ignored.
        val (npub, connectionPrivKey) = localKeyIndex[taggedKey.pubKey] ?: return
        val acc = AmberDesktop.account(npub) ?: return
        val store = AmberDesktop.store(acc.npub)
        if (isRateLimited(store, event, relay)) return
        store.addLog(relay.url, "bunker", "New event ${event.id} from ${event.pubKey}")

        val bunkerRequest = openRequest(store, event, relay, connectionPrivKey) ?: return
        store.addLog(relay.url, "bunker", "Request ${bunkerRequest.id} method ${bunkerRequest.method}")
        val encryptionType = if (EncryptedInfo.isNIP04(event.content)) EncryptionType.NIP04 else EncryptionType.NIP44
        routeRequest(bunkerRequest, event, acc, relay, encryptionType, connectionPrivKey)
    }

    /**
     * Whether [event] is a request to process now: unlocked, a verified non-empty
     * kind-24133 event within the freshness window and NIP-40 expiration shared with
     * Android, and not seen before (it is recorded here, so stale replays and
     * future-dated events are dropped before they are).
     */
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

    // Unknown senders are rate-limited: their events force decrypt/log work and
    // signed error replies, so a flood must cost them silence. Stored connections
    // bypass the limiter entirely — an approved app legitimately bursts (bulk
    // NIP-44 decrypt batches, rapid signing) and must never be silently dropped.
    // Checked after verify() and keyed on the proven signer pubkey, so forged
    // events cannot drain a legitimate sender's budget.
    private fun isRateLimited(store: AccountStore, event: Event, relay: NormalizedRelayUrl): Boolean = store.getByKey(event.pubKey)?.takeUnless { it.app.isExpired() } == null &&
        !rateLimiter.allow("${relay.url}|${event.pubKey}", System.currentTimeMillis())

    // The request [event] carries, decrypted with the connection key, or null (logged) when it cannot be read.
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

        if (type == SignerType.INVALID) {
            // Unauthenticated error reply: publish once, no retry/backoff —
            // the sender is unverified, so it must not buy amplification.
            sendResponse(
                acc,
                connectionPrivKey,
                event.pubKey,
                encryptionType,
                BunkerResponse(bunkerRequest.id, "", "Unrecognized method: ${bunkerRequest.method}"),
                responseRelay.ifEmpty { AmberDesktop.defaultRelays() },
                attempts = UNVERIFIED_REPLY_ATTEMPTS,
            )
            return
        }

        val stored = store.getByKey(event.pubKey)
        if (stored != null && stored.app.isExpired()) {
            // Mirrors EventNotificationConsumer: the user set a finite lifetime
            // on this connection, so answer as an unknown app even before the
            // prune loop deletes it.
            store.addLog(relay.url, "bunker", "Connection expired for ${event.pubKey}")
        }
        val connectSecret = (bunkerRequest as? BunkerRequestConnect)?.let { it.secret ?: UUID.randomUUID().toString() }
        val withSecret = connectSecret?.let { store.getBySecret(it) }
        val reply = UnadmittedReply(acc, connectionPrivKey, event.pubKey, encryptionType, bunkerRequest.id)
        when (val admission = admitNip46Request(type, stored?.toNip46Connection(), connectionPrivKey, connectSecret, withSecret?.toNip46Connection())) {
            is Nip46Admission.Admit -> handleRequest(
                bunkerRequest,
                event,
                acc,
                relay,
                connectionPrivKey,
                stored.takeIf { admission.stored != null },
                withSecret.takeIf { admission.bySecret != null },
            )
            is Nip46Admission.AckRepeatedConnect -> {
                // Already-connected client re-sending `connect`: ack silently.
                store.addHistory(HistoryRecord(admission.connection.key, type.toString().lowercase(), null, TimeUtils.now(), true))
                val relays = stored?.app?.normalizedRelays().orEmpty().ifEmpty { responseRelay }
                sendResponse(acc, connectionPrivKey.ifEmpty { admission.connection.localKey }, event.pubKey, encryptionType, BunkerResponse(bunkerRequest.id, "ack", null), relays)
            }
            is Nip46Admission.RefuseConnect -> {
                store.addLog(relay.url, "bunker", "Connection rejected: ${admission.message}")
                refuse(reply, admission.message, withSecret?.app?.normalizedRelays() ?: responseRelay)
            }
            Nip46Admission.Unknown -> {
                store.addLog(relay.url, "bunker", "No permission found for ${event.pubKey}")
                refuse(reply, "no permission", responseRelay)
            }
            // Mirrors EventNotificationConsumer: a stored app may only use its
            // own connection key; a request to another connection's key is
            // dropped. A connect is exempt (that is how a client attaches).
            Nip46Admission.Ignore -> store.addLog(relay.url, "bunker", "Request ${bunkerRequest.id} from ${event.pubKey} reached another connection's key")
        }
    }

    // Who an error reply to a not-admitted sender goes to, and under which key.
    private class UnadmittedReply(
        val acc: DesktopAccount,
        val connectionPrivKey: String,
        val clientPubKey: String,
        val encryptionType: EncryptionType,
        val requestId: String,
    )

    // Unauthenticated error reply: publish once, no retry/backoff.
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
        when (a.type) {
            SignerType.SWITCH_RELAYS -> switchRelays(a)
            SignerType.LOGOUT -> logout(a)
            else -> if (!refusedUpFront(a)) answerOrQueue(a)
        }
    }

    // What every handler of an admitted request needs.
    @Suppress("LongParameterList") // Bundles the request's context so the handlers take one argument.
    private class AdmittedRequest(
        val bunkerRequest: BunkerRequest,
        val event: Event,
        val acc: DesktopAccount,
        val relay: NormalizedRelayUrl,
        connectionPrivKey: String,
        // The sender's own stored connection; [app] is it, or the one a connect secret names.
        val permission: AppWithPermissions?,
        val app: AppWithPermissions?,
    ) {
        val type = nip46SignerType(bunkerRequest.method)
        val encryptionType = if (EncryptedInfo.isNIP04(event.content)) EncryptionType.NIP04 else EncryptionType.NIP44

        // The key the reply is encrypted and signed with: the connection's own.
        val signerPrivKey = connectionPrivKey.ifEmpty { app?.app?.localKey ?: "" }
        val relays = app?.app?.normalizedRelays() ?: listOf(relay)
        val store get() = AmberDesktop.store(acc.npub)

        // NIP-44 v3 carries its context kind/scope at params[1..2].
        val kind = when {
            bunkerRequest is BunkerRequestSign -> bunkerRequest.event.kind
            type in nip44v3SignerTypes -> bunkerRequest.nip44v3Kind()
            else -> null
        }
        val relayHost = if (bunkerRequest is BunkerRequestSign && kind == RELAY_AUTH_KIND) relayAuthHost(bunkerRequest.event.tags) else ""
    }

    private suspend fun reply(a: AdmittedRequest, result: String?, error: String? = null): Boolean = sendResponse(a.acc, a.signerPrivKey, a.event.pubKey, a.encryptionType, BunkerResponse(a.bunkerRequest.id, result, error), a.relays)

    // A stored connection moves to the default relays once the client has the answer.
    private suspend fun switchRelays(a: AdmittedRequest) {
        val current = a.permission ?: return
        val defaultRelays = AmberDesktop.defaultRelays()
        if (reply(a, switchRelaysResult(defaultRelays.map { it.url }))) {
            a.store.upsert(current.copy(app = current.app.copy(relays = defaultRelays.map { it.url })))
            checkForNewRelaysAndUpdateAllFilters(shouldReconnect = true)
        }
    }

    // A stored connection is deleted once the client has the ack.
    private suspend fun logout(a: AdmittedRequest) {
        val current = a.permission ?: return
        if (reply(a, "ack")) {
            a.store.delete(current.app.key)
            checkForNewRelaysAndUpdateAllFilters()
        }
    }

    /** Refuses, without computing anything, what may never be signed; true when it did. */
    private suspend fun refusedUpFront(a: AdmittedRequest): Boolean {
        val request = a.bunkerRequest
        // Mirrors EventNotificationConsumer: authorization and the approval
        // card decide on the first relay tag alone, so a second one would be
        // signed without ever being granted.
        if (request is BunkerRequestSign && hasAmbiguousRelayTags(request.event.kind, request.event.tags)) {
            a.store.addLog(a.relay.url, "bunker", "Refusing a multi-relay auth event ${request.id}")
            reply(a, "", "refusing a multi-relay auth event")
            return true
        }
        // Relay auth is granted per relay; a relay outside a non-empty auth
        // whitelist is rejected without asking, like Android.
        if (isBlockedByAuthWhitelist(a.kind, a.relayHost, AmberDesktop.settings.authWhitelist)) {
            a.store.addLog(a.relay.url, "bunker", "Relay auth for ${a.relayHost} is not in the auth whitelist")
            a.store.addHistory(HistoryRecord(a.event.pubKey, a.type.toString(), a.kind, TimeUtils.now(), false))
            reply(a, "", "user rejected")
            return true
        }
        return false
    }

    // Answers from the stored grants, else queues the request for the user's decision.
    private suspend fun answerOrQueue(a: AdmittedRequest) {
        // consume() checked the lock on arrival; re-check before any signing
        // work, since the passphrase lock may have come down in between.
        if (PassphraseLock.isLocked()) {
            deferUntilUnlock(a.event)
            return
        }
        val computed = computeOrRefuse(a) ?: return

        // NIP-04/NIP-44 encrypt/decrypt grants are per content type, like
        // Android: classify the plaintext (the encrypt input, or the decrypt
        // result) and try that grant, then the whole-NIP grant, then `nip:N`.
        val encryptedContent = if (a.type in contentScopedSignerTypes) EncryptedContent.classify(computed.preview) else null
        val permissionType = lookupPermission(a.store, a.event.pubKey, a.type, a.kind, encryptedContent, a.relayHost)
        // A first-time `connect` always goes through the approval UI: approving
        // is what migrates the bunker placeholder to the client key and grants
        // the default permissions. Reconnects were already acked above.
        when (if (a.type == SignerType.CONNECT) null else isRemembered(a.app?.app?.signPolicy, permissionType)) {
            true -> {
                // Auto-accept publishes a signed result: re-check the lock so
                // a signer locked mid-request never publishes.
                if (PassphraseLock.isLocked()) {
                    deferUntilUnlock(a.event)
                    return
                }
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

    // The response payload (also the approval preview), or null after answering that it can't be made.
    private suspend fun computeOrRefuse(a: AdmittedRequest): ComputedResult? = try {
        computeResult(a.bunkerRequest, a.type, a.acc)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        a.store.addLog(a.relay.url, "bunker", "Rejecting request that cannot be fulfilled: ${e.message}")
        // Mirrors Android's validateNip44v3Request errors; kept generic so
        // a failed decrypt doesn't leak the ciphertext's embedded context.
        val error = when (a.type) {
            in nip44v3SignerTypes if a.kind == null -> "kind is required for nip44v3"
            SignerType.NIP44_V3_DECRYPT -> "could not decrypt the message"
            else -> "could not process the request"
        }
        reply(a, "", error)
        null
    }

    private fun pendingRequest(a: AdmittedRequest, computed: ComputedResult, encryptedContent: EncryptedContent?): PendingBunkerRequest {
        val request = a.bunkerRequest
        // Saved names came from a client; older records were stored unsanitized.
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

    /** The stored grant that decides [type] for [appKey], with Android's fallbacks. */
    internal fun lookupPermission(
        store: AccountStore,
        appKey: String,
        type: SignerType,
        kind: Int?,
        encryptedContent: EncryptedContent?,
        relayHost: String,
    ): AppPermissionRecord? = when (type) {
        // Relay auth is granted per relay or for all relays, like Android.
        SignerType.SIGN_EVENT if kind == RELAY_AUTH_KIND -> store.getRelayAuthPermission(appKey, relayHost)

        else -> permissionLookupOrder(
            type,
            kind,
            contentType = if (type in contentScopedSignerTypes) type.contentPermissionType(encryptedContent) else null,
            signNip = if (type == SignerType.SIGN_EVENT) kind?.kindToNip()?.toIntOrNull() else null,
        ).firstNotNullOfOrNull { store.getPermission(appKey, it.type, it.kind) }
    }

    // Atomic updates: socket clients queue from several threads at once, and a
    // plain read-then-write of `pending.value` loses one of two racing requests.
    internal fun addPending(request: PendingBunkerRequest) {
        pending.update { list -> if (list.any { it.isSameEntry(request) }) list else list + request }
    }

    /** Removes the entry queued under [req]'s (account, connection, id) key. */
    fun removePending(req: PendingBunkerRequest) {
        pending.update { list -> list.filterNot { it.isSameEntry(req) } }
    }

    /**
     * Drops connections whose "delete after" time has passed, in every
     * account, and refreshes the relay filter if any were removed. Mirrors
     * the Android `ClearLogsWorker` cleanup.
     */
    suspend fun pruneExpiredApplications(now: Long = TimeUtils.now()) {
        // While locked the per-account databases can't be read (or would be
        // cached empty); the next tick after unlocking catches up.
        if (PassphraseLock.isLocked()) return
        var removed = 0
        AccountsStore.accounts.value.forEach { record ->
            val count = AmberDesktop.store(record.npub).deleteExpiredApps(now)
            if (count > 0) {
                AmberDesktop.store(record.npub).addLog("", "deleteApplications", "Deleted $count expired applications")
                removed += count
            }
        }
        if (removed > 0) checkForNewRelaysAndUpdateAllFilters()
    }

    /** Drops requests whose [PendingBunkerRequest.expiresAt] has passed; the UI calls this periodically. */
    fun pruneExpired(now: Long = TimeUtils.now()) {
        dropPending("request expired") { it.expiresAt != null && it.expiresAt <= now }
    }

    /**
     * Removes the matching requests from the queue. Relay clients simply time
     * out, but a local socket client is still waiting on its connection, so it
     * gets [error] as the answer.
     */
    fun dropPending(error: String, predicate: (PendingBunkerRequest) -> Boolean) {
        var dropped = emptyList<PendingBunkerRequest>()
        pending.update { list ->
            dropped = list.filter(predicate)
            list.filterNot(predicate)
        }
        dropped.forEach { req ->
            req.responder?.let { respond -> scope.launch { respond(BunkerResponse(req.request.id, "", error)) } }
        }
    }

    /**
     * Mirrors `BunkerRequestUtils.sendResult`. Runs on the engine's
     * application scope (a SupervisorJob), never the caller's — approving
     * removes the request card from the UI, so a caller-scoped coroutine would
     * be canceled mid-flight (during the publish retry/backoff) before the
     * relay response is ever sent. Returns the [Job] so callers/tests can
     * await completion if they need to.
     */
    fun approve(
        req: PendingBunkerRequest,
        rememberType: RememberType,
        grantedPermissions: List<Permission> = emptyList(),
        signPolicy: Int? = null,
        deleteAfter: Long = 0L,
        accountNpub: String? = null,
        encryptionScope: EncryptionScope = req.defaultScope,
    ): Job = scope.launch { doApprove(req, rememberType, grantedPermissions, signPolicy, deleteAfter, accountNpub, encryptionScope) }

    private suspend fun doApprove(
        req: PendingBunkerRequest,
        rememberType: RememberType,
        grantedPermissions: List<Permission> = emptyList(),
        signPolicy: Int? = null,
        deleteAfter: Long = 0L,
        accountNpub: String? = null,
        encryptionScope: EncryptionScope = EncryptionScope.ALL,
    ) {
        PassphraseLock.touch()
        removePending(req)
        val acc = approvingAccount(req, accountNpub)
        val store = AmberDesktop.store(acc.npub)
        val key = req.localKey
        val savedApplication = savedConnection(req, acc, store)
        // Approving changes this list in place; kept to take the approval back.
        val grantsBefore = savedApplication?.permissions?.toList().orEmpty()
        val relays = approvalRelays(req, savedApplication)

        // Only a connect may create an application. A non-connect request whose
        // app is gone (deleted, expired or logged out while the card was queued)
        // must not resurrect the revoked connection: answer it like an unknown client.
        if (savedApplication == null && req.request !is BunkerRequestConnect && req.type != SignerType.CONNECT) {
            answer(req, acc, req.signerPrivKey, BunkerResponse(req.request.id, "", "no permission"), relays)
            return
        }

        var application = savedApplication ?: newApplication(req, acc, relays, signPolicy ?: acc.signPolicy, deleteAfter)
        application = application.copy(app = application.app.copy(isConnected = true, lastUsed = TimeUtils.now()))

        if (req.type == SignerType.CONNECT) {
            application = applyConnect(application, signPolicy ?: acc.signPolicy, deleteAfter, grantedPermissions)
        } else if (rememberType != RememberType.NEVER && req.canRemember(encryptionScope)) {
            acceptOrRejectPermission(application, req.type, req.permissionKind(encryptionScope), true, rememberType, req.encryptedContent, encryptionScope, req.permissionRelay(encryptionScope))
        }

        store.upsert(application)
        val grantsAfter = application.permissions.toList()
        store.addHistory(HistoryRecord(key, req.type.toString(), req.kind, TimeUtils.now(), true))

        val response = if (req.type == SignerType.CONNECT) req.nostrConnectSecret.ifBlank { req.result } else req.result
        if (req.responder == null) checkForNewRelaysAndUpdateAllFilters()
        val delivered = answer(
            req,
            acc,
            application.app.localKey.ifEmpty { req.signerPrivKey },
            BunkerResponse(req.request.id, response, null),
            application.app.normalizedRelays().ifEmpty { relays },
        )
        // Like Android: what the client never heard of must not stay granted.
        if (!delivered && rememberType != RememberType.NEVER) revertApproval(req, store, application.app, grantsBefore, grantsAfter)
    }

    /**
     * Takes back what approving [req] remembered when its reply did not reach the
     * client, like Android's `revertApproval`: a connect drops the connection (a
     * bunker:// one goes back to waiting under its secret), any other request restores
     * the grants it replaced. The grants are re-read, so a change made while the reply
     * was retried survives.
     */
    private fun revertApproval(req: PendingBunkerRequest, store: AccountStore, approved: AppRecord, grantsBefore: List<AppPermissionRecord>, grantsAfter: List<AppPermissionRecord>) {
        val key = approved.key
        store.addLog(key, "bunker response", "The reply did not reach the client, so its approval was taken back")
        failedReplies.tryEmit(Unit)
        if (req.type == SignerType.CONNECT) {
            store.delete(key)
            if (!req.isNostrConnectUri && approved.secret.isNotBlank()) {
                store.upsert(AppWithPermissions(approved.copy(key = approved.secret, isConnected = false), grantsAfter.toMutableList()))
            }
            return
        }
        val current = store.getByKey(key) ?: return
        val added = (grantsAfter - grantsBefore.toSet()).toSet()
        val replaced = grantsBefore - grantsAfter.toSet()
        val restored = current.permissions.filterNot { it in added }
        store.upsert(current.copy(permissions = (restored + replaced.filterNot { it in restored }).toMutableList()))
    }

    // Mirrors the Android connect screen's account picker: the connection is
    // saved under (and signs for) the chosen account.
    private suspend fun approvingAccount(req: PendingBunkerRequest, accountNpub: String?): DesktopAccount = accountNpub
        ?.takeIf { it != req.account.npub && req.canSwitchAccount }
        ?.let { AmberDesktop.account(it) }
        ?: req.account

    /**
     * The connection [req] is approved on in [acc]'s store, or null for a new one. A
     * bunker:// connection is saved under its secret until a client connects with it:
     * that placeholder is moved to the chosen account and re-keyed to the client.
     */
    private fun savedConnection(req: PendingBunkerRequest, acc: DesktopAccount, store: AccountStore): AppWithPermissions? {
        val key = req.localKey
        val secret = (req.request as? BunkerRequestConnect)?.secret?.takeIf { it.isNotBlank() }
        if (secret != null && acc.npub != req.account.npub) {
            val sourceStore = AmberDesktop.store(req.account.npub)
            sourceStore.getByKey(secret)?.let { placeholder ->
                sourceStore.delete(placeholder.app.key)
                store.upsert(placeholder.copy(app = placeholder.app.copy(pubKey = acc.hexKey)))
            }
        }

        // An expired connection counts as gone: approving a card queued before
        // it expired must not sign for it (a new connect replaces it below).
        var savedApplication = store.getByKey(key)?.takeUnless { it.app.isExpired() }
        if (connectionToReplace(req, acc.npub) != null) {
            // A nostrconnect URI names the client key itself. Approving it
            // replaces the connection saved under that key (the card shows
            // which, like Android's "Replace connection" dialog) instead of
            // silently merging into it and keeping its grants and relays.
            store.delete(key)
            savedApplication = null
        }
        if (savedApplication == null && secret != null) {
            store.getByKey(secret)?.let { placeholder ->
                store.delete(placeholder.app.key)
                savedApplication = placeholder.copy(app = placeholder.app.copy(key = key))
                store.upsert(savedApplication)
            }
        }
        return savedApplication
    }

    // Where the approval is answered. A brand-new connection (bunker:// or a
    // nostrconnect:// that named no relays) gets the local WebSocket relay beside the
    // default relays, so clients on this machine work without any public relay; a
    // local socket client never talks to relays.
    private fun approvalRelays(req: PendingBunkerRequest, savedApplication: AppWithPermissions?): List<NormalizedRelayUrl> {
        if (req.isLocalSocket) return emptyList()
        val newConnectionRelays = if (req.isNostrConnectUri) req.relays.ifEmpty { AmberDesktop.newBunkerRelays() } else AmberDesktop.newBunkerRelays()
        return savedApplication?.app?.normalizedRelays()?.ifEmpty { AmberDesktop.defaultRelays() } ?: newConnectionRelays
    }

    // The record of a connection seen for the first time.
    private fun newApplication(
        req: PendingBunkerRequest,
        acc: DesktopAccount,
        relays: List<NormalizedRelayUrl>,
        signPolicy: Int,
        deleteAfter: Long,
    ): AppWithPermissions {
        val secret = (req.request as? BunkerRequestConnect)?.secret ?: ""
        return AppWithPermissions(
            app = AppRecord(
                key = req.localKey,
                name = req.appName,
                relays = relays.map { it.url },
                url = req.appUrl,
                icon = req.appIcon,
                pubKey = acc.hexKey,
                isConnected = true,
                secret = secret,
                useSecret = secret.isNotBlank(),
                signPolicy = signPolicy,
                deleteAfter = deleteAfter,
                lastUsed = TimeUtils.now(),
                transport = if (req.isLocalSocket) TRANSPORT_SOCKET else TRANSPORT_NIP46,
                socketSecretHash = req.socketSecretHash,
                // Each connection gets its own signing key.
                localKey = if (req.request is BunkerRequestConnect && !req.isLocalSocket) generateBunkerPrivKey() else "",
            ),
        )
    }

    /**
     * What approving a connect sets: the chosen sign policy and its grants, and the
     * "delete after" choice, which like Android's also applies to an existing (e.g.
     * bunker:// placeholder) connection. A connection may always ask for the public
     * key and ping.
     */
    private fun applyConnect(
        application: AppWithPermissions,
        signPolicy: Int,
        deleteAfter: Long,
        grantedPermissions: List<Permission>,
    ): AppWithPermissions {
        val result = application.copy(app = application.app.copy(signPolicy = signPolicy, deleteAfter = deleteAfter))
        applySignPolicy(result, signPolicy, grantedPermissions)
        listOf(SignerType.GET_PUBLIC_KEY, SignerType.PING).forEach { type ->
            if (result.permissions.none { it.type == type.toString() }) {
                result.permissions.add(AppPermissionRecord(type.toString(), null, true, RememberType.ALWAYS.screenCode, Long.MAX_VALUE / 1000, 0))
            }
        }
        return result
    }

    /**
     * Answers [req] over its local socket, or else as a kind-24133 event on [relays].
     * False only when a relay reply failed to publish; with no relay to send to there
     * is nothing to fail, as on Android.
     */
    private suspend fun answer(
        req: PendingBunkerRequest,
        acc: DesktopAccount,
        signerPrivKey: String,
        response: BunkerResponse,
        relays: List<NormalizedRelayUrl>,
    ): Boolean {
        val respond = req.responder ?: return sendResponse(acc, signerPrivKey, req.localKey, req.encryptionType, response, relays) || relays.isEmpty()
        respond(response)
        return true
    }

    /**
     * Mirrors `BunkerRequestUtils.sendRejection`. Runs on the application
     * scope for the same reason as [approve] — the rejection response must be
     * published even though the request card is gone.
     */
    fun reject(
        req: PendingBunkerRequest,
        rememberType: RememberType,
        encryptionScope: EncryptionScope = req.defaultScope,
    ): Job = scope.launch { doReject(req, rememberType, encryptionScope) }

    private suspend fun doReject(
        req: PendingBunkerRequest,
        rememberType: RememberType,
        encryptionScope: EncryptionScope = EncryptionScope.ALL,
    ) {
        PassphraseLock.touch()
        removePending(req)
        val acc = req.account
        val store = AmberDesktop.store(acc.npub)
        val key = req.localKey

        val savedApplication = store.getByKey(key)
        val defaultRelays = AmberDesktop.defaultRelays()
        val newConnectionRelays = if (req.isNostrConnectUri) {
            req.relays.ifEmpty { defaultRelays }
        } else {
            defaultRelays
        }
        val relays = if (req.isLocalSocket) {
            emptyList()
        } else {
            savedApplication?.app?.normalizedRelays()?.ifEmpty { defaultRelays } ?: newConnectionRelays
        }

        // A rejected connect leaves nothing behind (socket connects are plain
        // `BunkerRequest`s, so check the type rather than the class), and a
        // rejection never creates an application: if the app was deleted while
        // the card was queued, there is nothing to record the rejection on.
        if (savedApplication != null && req.request !is BunkerRequestConnect && req.type != SignerType.CONNECT) {
            if (rememberType != RememberType.NEVER && req.canRemember(encryptionScope)) {
                acceptOrRejectPermission(savedApplication, req.type, req.permissionKind(encryptionScope), false, rememberType, req.encryptedContent, encryptionScope, req.permissionRelay(encryptionScope))
            }
            store.upsert(savedApplication)
            store.addHistory(HistoryRecord(key, req.type.toString(), req.kind, TimeUtils.now(), false))
        }

        req.responder?.let { respond ->
            respond(BunkerResponse(req.request.id, "", "user rejected"))
            return
        }

        val signerPrivKey = savedApplication?.app?.localKey?.ifEmpty { null } ?: req.signerPrivKey
        sendResponse(
            acc,
            signerPrivKey,
            key,
            req.encryptionType,
            BunkerResponse(req.request.id, "", "user rejected"),
            relays,
        )
    }

    /** Mirrors `AmberUtils.configureSignPolicy`. */
    private fun applySignPolicy(
        application: AppWithPermissions,
        signPolicy: Int,
        permissions: List<Permission>,
    ) {
        when (signPolicy) {
            0 -> {
                basicPermissions.forEach { perm ->
                    addAcceptedPermission(application, perm)
                }
            }

            1 -> {
                permissions.filter { it.checked }.forEach { perm ->
                    addAcceptedPermission(application, perm)
                }
            }
        }
    }

    private fun addAcceptedPermission(application: AppWithPermissions, perm: Permission) {
        // A requested perm can map to more than one stored permission type: the
        // request path keys encrypt/decrypt by NIP (NIP04_ENCRYPT/NIP44_ENCRYPT),
        // but a content-scoped or generic perm (encrypt_event, encrypt) is
        // NIP-agnostic, so it must grant both NIP variants — otherwise the grant
        // is stored under a key the request path never queries and the client is
        // re-prompted on every request.
        expandPermissionTypes(perm.type).forEach { type ->
            if (application.permissions.any { it.type == type && it.kind == perm.kind }) return@forEach
            application.permissions.add(
                AppPermissionRecord(type, perm.kind, true, RememberType.ALWAYS.screenCode, Long.MAX_VALUE / 1000, 0),
            )
        }
    }

    /**
     * Mirrors `AmberUtils.updatePermission`. For NIP-04/NIP-44 encrypt and
     * decrypt, [scope] ALL stores the whole-NIP type (NIP44_DECRYPT) and drops
     * the narrower content-type grant; SPECIFIC stores the content type
     * (DECRYPT_CLEAR_TEXT) and drops the broader NIP grant.
     */
    private fun acceptOrRejectPermission(
        application: AppWithPermissions,
        type: SignerType,
        kind: Int?,
        accepted: Boolean,
        rememberType: RememberType,
        content: EncryptedContent? = null,
        scope: EncryptionScope = EncryptionScope.ALL,
        relay: String = "",
    ) {
        val until = rememberType.acceptUntil()
        val contentScoped = type in contentScopedSignerTypes
        val typeStr = if (contentScoped && scope == EncryptionScope.SPECIFIC) type.contentPermissionType(content) else type.toString()

        if (kind != null) {
            application.removeKindGrants(typeStr, kind, relay)
        } else {
            application.permissions.removeIf { it.type == typeStr && it.type != "SIGN_EVENT" }
            // A whole-NIP grant replaces the narrower content-type one, and the other way round.
            if (contentScoped) {
                val replaced = if (scope == EncryptionScope.ALL) type.contentPermissionType(content) else type.toString()
                application.permissions.removeIf { it.type == replaced }
            }
        }

        application.permissions.add(
            AppPermissionRecord(
                type = typeStr,
                kind = kind,
                acceptable = accepted,
                rememberType = rememberType.screenCode,
                acceptUntil = if (accepted) until else 0,
                rejectUntil = if (accepted) 0 else until,
                relay = relay,
            ),
        )
    }

    private suspend fun sendResponse(
        account: DesktopAccount,
        signerPrivKey: String,
        localKey: String,
        encryptionType: EncryptionType,
        bunkerResponse: BunkerResponse,
        relays: List<NormalizedRelayUrl>,
        attempts: Int = BUNKER_REPLY_ATTEMPTS,
    ): Boolean {
        val store = AmberDesktop.store(account.npub)
        if (relays.isEmpty()) {
            store.addLog(localKey, "bunker response", "No relays to send the response to")
            return false
        }

        val connSigner = if (signerPrivKey.isNotEmpty()) {
            NostrSignerInternal(KeyPair(privKey = signerPrivKey.hexToByteArray()))
        } else {
            null
        }

        val plainText = JacksonMapper.mapper.writeValueAsString(bunkerResponse)

        suspend fun buildEvent(): Event {
            val encryptedContent = if (encryptionType == EncryptionType.NIP44) {
                connSigner?.nip44Encrypt(plainText, localKey) ?: account.nip44Encrypt(plainText, localKey)
            } else {
                connSigner?.nip04Encrypt(plainText, localKey) ?: account.nip04Encrypt(plainText, localKey)
            }
            return connSigner?.signerSync?.sign(
                TimeUtils.now(),
                NostrConnectEvent.KIND,
                arrayOf(arrayOf("p", localKey)),
                encryptedContent,
            ) ?: account.signSync(
                TimeUtils.now(),
                NostrConnectEvent.KIND,
                arrayOf(arrayOf("p", localKey)),
                encryptedContent,
            )
        }

        val publish: suspend () -> Boolean = {
            client.publishAndConfirm(
                event = buildEvent(),
                relayList = relays.toSet(),
                timeoutInSeconds = 5,
                untilFirstAccept = true,
            )
        }

        val success = retryWithBackoff(maxRetries = attempts, block = publish)

        val sanitized = "id=${bunkerResponse.id} ${bunkerResponse.error?.let { "error=$it" } ?: "ok"} sent=$success"
        relays.forEach { store.addLog(it.url, "bunker response", sanitized) }
        return success
    }

    /** Parses a `nostrconnect://` URI and queues it for approval. */
    fun addNostrConnect(uriString: String, account: DesktopAccount): String? {
        try {
            // The client's key becomes the connection's identity and a p tag
            // on every reply: the shared parser accepts only a well-formed one.
            val uri = try {
                NostrConnectUri.parse(uriString) { it.containsNip() }
            } catch (e: IllegalArgumentException) {
                return e.message ?: "Invalid public key in URI"
            }
            val pubKey = uri.clientPubKey
            val permissions = uri.permissions.map { Permission(it.type, it.kind) }

            addPending(
                PendingBunkerRequest(
                    request = BunkerRequestConnect(
                        id = UUID.randomUUID().toString(),
                        remoteKey = pubKey,
                        secret = "",
                        permissions = permissions.joinToString(",") { if (it.kind != null) "${it.type}:${it.kind}" else it.type }.ifBlank { null },
                    ),
                    type = SignerType.CONNECT,
                    account = account,
                    localKey = pubKey,
                    relays = uri.relays,
                    nostrConnectSecret = uri.secret,
                    // Client-chosen (sanitized by the parser): the card, the saved record and every notification show it.
                    appName = uri.name.ifBlank { pubKey.toShortenHex() },
                    appUrl = uri.url,
                    appIcon = uri.image,
                    requestedPermissions = permissions,
                    result = "ack",
                    encryptionType = EncryptionType.NIP44,
                    isNostrConnectUri = true,
                ),
            )
            return null
        } catch (e: Exception) {
            AmberLogger.e(AmberDesktop.TAG, "Failed to parse nostrconnect uri", e)
            return e.message ?: "Failed to parse the URI"
        }
    }

    /**
     * Creates a bunker:// connection placeholder (key == secret until the
     * client connects) and returns the URI to hand to the client app.
     */
    suspend fun createBunkerConnection(
        account: DesktopAccount,
        name: String,
        relays: List<NormalizedRelayUrl>,
        deleteAfter: Long = 0L,
    ): String {
        val secret = UUID.randomUUID().toString()
        val connPrivKey = generateBunkerPrivKey()
        // Exactly the relays the caller picked: the dialog seeds them with
        // AmberDesktop.newBunkerRelays() (the local relay plus the defaults),
        // and any the user removed there must stay out of the URI.
        val uriRelays = relays.distinct()
        val app = AppWithPermissions(
            app = AppRecord(
                key = secret,
                name = name,
                relays = uriRelays.map { it.url },
                pubKey = account.hexKey,
                isConnected = false,
                secret = secret,
                useSecret = true,
                signPolicy = account.signPolicy,
                deleteAfter = deleteAfter,
                localKey = connPrivKey,
            ),
        )
        AmberDesktop.store(account.npub).upsert(app)
        checkForNewRelaysAndUpdateAllFilters()
        val relayParams = uriRelays.joinToString(separator = "&") { "relay=${it.url}" }
        return "bunker://${localPubKeyFromPrivKey(connPrivKey)}?$relayParams&secret=$secret"
    }

    private fun parsePermissionsParam(permissions: String?): List<Permission> {
        if (permissions.isNullOrBlank()) return emptyList()
        return permissions.split(",").mapNotNull { perm ->
            if (perm.isBlank()) return@mapNotNull null
            val parts = perm.split(":")
            Permission(parts.first().trim(), parts.getOrNull(1)?.toIntOrNull())
        }
    }

    companion object {
        /** How long a relay request waits for a decision before it is dropped. */
        const val PENDING_TTL_SECONDS = 10 * 60L

        /**
         * Maps a requested permission string to the stored permission type(s)
         * the request path queries. Amber's content-type perms
         * (`encrypt_clear_text`, `decrypt_event`, `encrypt_tag_array`, …) are
         * stored as-is (ENCRYPT_CLEAR_TEXT, …) and checked first, like Android;
         * standard NIP-46 `nip04_encrypt`/`nip44_decrypt` are whole-NIP grants,
         * and a generic `encrypt`/`decrypt` grants both NIPs.
         */
        fun expandPermissionTypes(type: String): List<String> = when (val method = type.lowercase()) {
            "encrypt" -> listOf(SignerType.NIP04_ENCRYPT.toString(), SignerType.NIP44_ENCRYPT.toString())
            "decrypt" -> listOf(SignerType.NIP04_DECRYPT.toString(), SignerType.NIP44_DECRYPT.toString())
            // A NIP-46 method is stored as its type (nip44v3_encrypt as NIP44_V3_ENCRYPT);
            // a content-type perm (encrypt_clear_text, …) or anything else as-is, upper-cased.
            else -> listOf(nip46SignerType(method).takeUnless { it == SignerType.INVALID }?.toString() ?: type.uppercase())
        }
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

// Relay auth grants: "*" replaces every relay's grant for this kind, a specific relay
// only its own entry, and any other kind grant only the one without a relay
// (Android's insertPermissions).
private fun AppWithPermissions.removeKindGrants(type: String, kind: Int, relay: String) {
    permissions.removeIf {
        it.kind == kind &&
            it.type == type &&
            when (relay) {
                "*" -> true
                "" -> it.relay.isEmpty()
                else -> it.relay == relay
            }
    }
}
