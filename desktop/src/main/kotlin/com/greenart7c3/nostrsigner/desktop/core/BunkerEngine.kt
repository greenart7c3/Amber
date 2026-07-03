package com.greenart7c3.nostrsigner.desktop.core

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUsers
import com.vitorpamplona.quartz.nip04Dm.crypto.EncryptedInfo
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapRequestBuilder
import com.vitorpamplona.quartz.utils.TimeUtils
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun generateBunkerPrivKey(): String = Nip01Crypto.privKeyCreate().toHexKey()

fun localPubKeyFromPrivKey(privKeyHex: String): String = KeyPair(privKey = privKeyHex.hexToByteArray()).pubKey.toHexKey()

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
    val requestedPermissions: List<RequestedPermission> = emptyList(),
    val kind: Int? = null,
    val preview: String = "",
    val result: String = "",
    val encryptionType: EncryptionType = EncryptionType.NIP44,
    val isNostrConnectUri: Boolean = false,
    val signerPrivKey: String = "",
)

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

    private val subIds = mutableMapOf<String, String>()
    private val filterMutex = Mutex()

    /** localPubKey -> (npub, localPrivKey), mirrors `LocalKeyAccountIndex`. */
    private val localKeyIndex = ConcurrentHashMap<String, Pair<String, String>>()

    /** Recently processed event ids -> createdAt, for dedup + `since` computation. */
    private val seenEvents = object : LinkedHashMap<String, Long>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 512
    }

    init {
        client.addConnectionListener(this)
    }

    override fun onIncomingMessage(relay: IRelayClient, msgStr: String, msg: Message) {
        if (msg is EventMessage) {
            if (subIds.containsValue(msg.subId)) {
                scope.launch {
                    consume(msg.event, relay.url)
                }
            }
        }
        super.onIncomingMessage(relay, msgStr, msg)
    }

    fun start() {
        scope.launch {
            updateFilter()
            client.connect()
        }
    }

    /** Mirrors `NotificationSubscription.updateFilter`. */
    suspend fun updateFilter() {
        if (PassphraseLock.isLocked()) return
        updateFilterLocked()
    }

    private suspend fun updateFilterLocked() = filterMutex.withLock {
        val activeSubKeys = mutableSetOf<String>()
        val indexEntries = mutableMapOf<String, Pair<String, String>>()

        AmberDesktop.accounts().forEach { account ->
            val since = computeSince()
            val store = AmberDesktop.store(account.npub)
            val allConnections = store.apps.value.map { it.app }
            val connectionsWithLocalKey = allConnections.filter { it.localKey.isNotEmpty() }
            val hasLegacyConnections = allConnections.any { it.localKey.isEmpty() && it.relays.isNotEmpty() }

            for (conn in connectionsWithLocalKey) {
                val connPubKey = conn.localPubKey()
                indexEntries[connPubKey] = account.npub to conn.localKey
                val subKey = "${account.hexKey}_$connPubKey"

                val connRelays = conn.normalizedRelays().ifEmpty { AmberDesktop.savedRelays(account.npub).toList() }
                if (connRelays.isEmpty()) continue

                activeSubKeys.add(subKey)
                if (!subIds.containsKey(subKey)) {
                    subIds[subKey] = UUID.randomUUID().toString()
                }
                client.subscribe(
                    subIds[subKey]!!,
                    connRelays.associateWith {
                        listOf(
                            Filter(
                                kinds = listOf(NostrConnectEvent.KIND),
                                tags = mapOf("p" to listOf(connPubKey)),
                                limit = 1,
                                since = since,
                            ),
                        )
                    },
                )
            }

            if (hasLegacyConnections) {
                val relays = AmberDesktop.savedRelays(account.npub)
                if (relays.isNotEmpty()) {
                    activeSubKeys.add(account.hexKey)
                    if (!subIds.containsKey(account.hexKey)) {
                        subIds[account.hexKey] = UUID.randomUUID().toString()
                    }
                    client.subscribe(
                        subIds[account.hexKey]!!,
                        relays.associateWith {
                            listOf(
                                Filter(
                                    kinds = listOf(NostrConnectEvent.KIND),
                                    tags = mapOf("p" to listOf(account.hexKey)),
                                    limit = 1,
                                    since = since,
                                ),
                            )
                        },
                    )
                }
            }
        }

        val staleSubKeys = subIds.keys.filter { it !in activeSubKeys }
        for (subKey in staleSubKeys) {
            subIds.remove(subKey)?.let { subId ->
                client.unsubscribe(subId)
            }
        }

        localKeyIndex.clear()
        localKeyIndex.putAll(indexEntries)
    }

    private fun computeSince(): Long {
        val latest = synchronized(seenEvents) { seenEvents.values.maxOrNull() ?: 0L }
        return if (latest > 0) latest else TimeUtils.now()
    }

    /** Mirrors `EventNotificationConsumer.consume` + `notify`. */
    suspend fun consume(event: Event, relay: NormalizedRelayUrl) {
        if (PassphraseLock.isLocked()) return
        if (event.kind != NostrConnectEvent.KIND) return
        if (!event.verify()) return
        if (event.content.isEmpty()) return

        val alreadySeen = synchronized(seenEvents) {
            if (seenEvents.containsKey(event.id)) {
                true
            } else {
                seenEvents[event.id] = event.createdAt
                false
            }
        }
        if (alreadySeen) return

        val taggedKey = event.taggedUsers().firstOrNull() ?: return

        var account = AmberDesktop.accounts().firstOrNull { it.npub == taggedKey.pubKey.hexToByteArray().toNpub() }
        var connectionPrivKey = ""

        if (account == null) {
            localKeyIndex[taggedKey.pubKey]?.let { (npub, privKey) ->
                account = AmberDesktop.account(npub)
                connectionPrivKey = privKey
            }
        }

        val acc = account ?: return
        val store = AmberDesktop.store(acc.npub)
        store.addLog(relay.url, "bunker", "New event ${event.id} from ${event.pubKey}")

        val encryptionType = if (EncryptedInfo.isNIP04(event.content)) EncryptionType.NIP04 else EncryptionType.NIP44

        val decrypted = try {
            if (connectionPrivKey.isNotEmpty()) {
                NostrSignerInternal(KeyPair(privKey = connectionPrivKey.hexToByteArray())).decrypt(event.content, event.pubKey)
            } else {
                acc.decrypt(event.content, event.pubKey)
            }
        } catch (e: Exception) {
            store.addLog(relay.url, "bunker", "Decryption failed for event ${event.id}: ${e.message}")
            return
        }

        val bunkerRequest = try {
            JacksonMapper.mapper.readValue(decrypted, BunkerRequest::class.java)
        } catch (e: Exception) {
            store.addLog(relay.url, "bunker", "Failed to parse request: ${e.message}")
            return
        }
        store.addLog(relay.url, "bunker", "Request ${bunkerRequest.id} method ${bunkerRequest.method}")

        handleRequest(bunkerRequest, event, acc, relay, encryptionType, connectionPrivKey)
    }

    private suspend fun handleRequest(
        bunkerRequest: BunkerRequest,
        event: Event,
        acc: DesktopAccount,
        relay: NormalizedRelayUrl,
        encryptionType: EncryptionType,
        connectionPrivKey: String,
    ) {
        val store = AmberDesktop.store(acc.npub)
        val responseRelay = listOf(relay)
        val type = typeFromMethod(bunkerRequest.method)

        if (type == SignerType.INVALID) {
            sendResponse(
                acc,
                connectionPrivKey,
                event.pubKey,
                encryptionType,
                BunkerResponse(bunkerRequest.id, "", "Unrecognized method: ${bunkerRequest.method}"),
                responseRelay.ifEmpty { AmberDesktop.defaultRelays() },
            )
            return
        }

        val permission = store.getByKey(event.pubKey)

        // Already-connected client re-sending `connect`: ack silently.
        if (permission != null &&
            ((permission.app.secret != permission.app.key && permission.app.useSecret) || permission.app.isConnected) &&
            type == SignerType.CONNECT
        ) {
            store.addHistory(HistoryRecord(permission.app.key, type.toString().lowercase(), null, TimeUtils.now(), true))
            sendResponse(
                acc,
                connectionPrivKey.ifEmpty { permission.app.localKey },
                event.pubKey,
                encryptionType,
                BunkerResponse(bunkerRequest.id, "ack", null),
                permission.app.normalizedRelays().ifEmpty { responseRelay },
            )
            return
        }

        var applicationWithSecret: AppWithPermissions? = null
        if (bunkerRequest is BunkerRequestConnect) {
            val secret = bunkerRequest.secret ?: UUID.randomUUID().toString()
            applicationWithSecret = store.getBySecret(secret)
            if (applicationWithSecret == null || secret.isBlank() || applicationWithSecret.app.isConnected || !applicationWithSecret.app.useSecret) {
                val message = when {
                    applicationWithSecret == null -> "invalid secret"
                    secret.isBlank() -> "no secret"
                    applicationWithSecret.app.isConnected -> "already connected"
                    else -> "secret not in use"
                }
                store.addLog(relay.url, "bunker", "Connection rejected: $message")
                sendResponse(
                    acc,
                    connectionPrivKey,
                    event.pubKey,
                    encryptionType,
                    BunkerResponse(bunkerRequest.id, "", message),
                    applicationWithSecret?.app?.normalizedRelays() ?: responseRelay,
                )
                return
            }
        }

        val relays = permission?.app?.normalizedRelays() ?: applicationWithSecret?.app?.normalizedRelays() ?: responseRelay
        if (permission == null && applicationWithSecret == null) {
            store.addLog(relay.url, "bunker", "No permission found for ${event.pubKey}")
            sendResponse(
                acc,
                connectionPrivKey,
                event.pubKey,
                encryptionType,
                BunkerResponse(bunkerRequest.id, "", "no permission"),
                relays,
            )
            return
        }

        val app = permission ?: applicationWithSecret
        val effectivePrivKey = connectionPrivKey.ifEmpty { app?.app?.localKey ?: "" }

        if (type == SignerType.SWITCH_RELAYS) {
            permission?.let { current ->
                val defaultRelays = AmberDesktop.defaultRelays()
                val result = if (defaultRelays.isEmpty()) null else "[${defaultRelays.joinToString(separator = ",") { "\"${it.url}\"" }}]"
                val ok = sendResponse(
                    acc,
                    effectivePrivKey,
                    event.pubKey,
                    encryptionType,
                    BunkerResponse(bunkerRequest.id, result ?: "", null),
                    relays,
                )
                if (ok) {
                    store.upsert(current.copy(app = current.app.copy(relays = defaultRelays.map { it.url })))
                    updateFilter()
                }
            }
            return
        }

        if (type == SignerType.LOGOUT) {
            permission?.let { current ->
                val ok = sendResponse(
                    acc,
                    effectivePrivKey,
                    event.pubKey,
                    encryptionType,
                    BunkerResponse(bunkerRequest.id, "ack", null),
                    relays,
                )
                if (ok) {
                    store.delete(current.app.key)
                    updateFilter()
                }
            }
            return
        }

        val kind = if (bunkerRequest is BunkerRequestSign) bunkerRequest.event.kind else null
        val signPolicy = app?.app?.signPolicy
        val permissionType = if (type == SignerType.SIGN_EVENT) {
            store.getPermission(event.pubKey, type.toString(), kind)
        } else {
            store.getPermission(event.pubKey, type.toString())
        }
        // A first-time `connect` always goes through the approval UI: approving
        // is what migrates the bunker placeholder to the client key and grants
        // the default permissions. Reconnects were already acked above.
        val remembered = if (type == SignerType.CONNECT) null else isRemembered(signPolicy, permissionType)

        // Compute the response payload (also serves as the approval preview).
        val computed = try {
            computeResult(bunkerRequest, type, acc)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            store.addLog(relay.url, "bunker", "Rejecting request that cannot be fulfilled: ${e.message}")
            sendResponse(
                acc,
                effectivePrivKey,
                event.pubKey,
                encryptionType,
                BunkerResponse(bunkerRequest.id, "", "could not process the request"),
                relays,
            )
            return
        }

        when (remembered) {
            true -> {
                store.addHistory(HistoryRecord(event.pubKey, type.toString(), kind, TimeUtils.now(), true))
                app?.let {
                    store.upsert(it.copy(app = it.app.copy(lastUsed = TimeUtils.now())))
                }
                sendResponse(
                    acc,
                    effectivePrivKey,
                    event.pubKey,
                    encryptionType,
                    BunkerResponse(bunkerRequest.id, computed.result, null),
                    relays,
                )
            }

            false -> {
                store.addHistory(HistoryRecord(event.pubKey, type.toString(), kind, TimeUtils.now(), false))
                sendResponse(
                    acc,
                    effectivePrivKey,
                    event.pubKey,
                    encryptionType,
                    BunkerResponse(bunkerRequest.id, "", "user rejected"),
                    relays,
                )
            }

            null -> {
                val name = app?.app?.name?.ifBlank { null } ?: event.pubKey.toShortenHex()
                addPending(
                    PendingBunkerRequest(
                        request = bunkerRequest,
                        type = type,
                        account = acc,
                        localKey = event.pubKey,
                        relays = relays,
                        appName = name,
                        requestedPermissions = if (bunkerRequest is BunkerRequestConnect) {
                            parsePermissionsParam(bunkerRequest.permissions)
                        } else {
                            emptyList()
                        },
                        kind = kind,
                        preview = computed.preview,
                        result = computed.result,
                        encryptionType = encryptionType,
                        signerPrivKey = effectivePrivKey,
                    ),
                )
            }
        }
    }

    private data class ComputedResult(val result: String, val preview: String)

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun computeResult(
        request: BunkerRequest,
        type: SignerType,
        acc: DesktopAccount,
    ): ComputedResult = when (type) {
        SignerType.CONNECT -> ComputedResult("ack", "")
        SignerType.GET_PUBLIC_KEY -> ComputedResult(acc.hexKey, acc.npub)
        SignerType.PING -> ComputedResult("pong", "")
        SignerType.SIGN_EVENT -> {
            val template = (request as BunkerRequestSign).event
            val signed = acc.signer.sign(template)
            ComputedResult(signed.toJson(), template.toJson())
        }

        SignerType.NIP04_ENCRYPT -> {
            val pubKey = request.params.first()
            val message = request.params.getOrElse(1) { "" }
            ComputedResult(acc.nip04Encrypt(message, pubKey), message)
        }

        SignerType.NIP44_ENCRYPT -> {
            val pubKey = request.params.first()
            val message = request.params.getOrElse(1) { "" }
            ComputedResult(acc.nip44Encrypt(message, pubKey), message)
        }

        SignerType.NIP04_DECRYPT -> {
            val pubKey = request.params.first()
            val ciphertext = request.params.getOrElse(1) { "" }
            val plain = acc.nip04Decrypt(ciphertext, pubKey)
            ComputedResult(plain, plain)
        }

        SignerType.NIP44_DECRYPT -> {
            val pubKey = request.params.first()
            val ciphertext = request.params.getOrElse(1) { "" }
            val plain = acc.nip44Decrypt(ciphertext, pubKey)
            ComputedResult(plain, plain)
        }

        SignerType.NIP44_V3_ENCRYPT -> {
            // NIP-46 layout: [pubkey, kind, scope, base64 plaintext]
            val pubKey = request.params.first()
            val kind = request.params.getOrNull(1)?.toIntOrNull() ?: throw IllegalArgumentException("kind is required for nip44v3")
            val scope = request.params.getOrElse(2) { "" }
            val plainBytes = Base64.decode(request.params.getOrElse(3) { "" })
            val cipher = Nip44v3.encrypt(plainBytes, acc.signer.keyPair.privKey!!, pubKey.hexToByteArray(), kind, scope)
            ComputedResult(cipher, plainBytes.toString(Charsets.UTF_8))
        }

        SignerType.NIP44_V3_DECRYPT -> {
            val pubKey = request.params.first()
            val kind = request.params.getOrNull(1)?.toIntOrNull() ?: throw IllegalArgumentException("kind is required for nip44v3")
            val scope = request.params.getOrElse(2) { "" }
            val plainBytes = Nip44v3.decrypt(request.params.getOrElse(3) { "" }, acc.signer.keyPair.privKey!!, pubKey.hexToByteArray(), kind, scope)
            ComputedResult(Base64.encode(plainBytes), plainBytes.toString(Charsets.UTF_8))
        }

        SignerType.DECRYPT_ZAP_EVENT -> {
            val eventJson = request.params.first()
            val zapEvent = Event.fromJson(eventJson) as LnZapRequestEvent
            val decrypted = PrivateZapRequestBuilder().decryptZapEvent(zapEvent, acc.signer.signerSync).toJson()
            ComputedResult(decrypted, decrypted)
        }

        SignerType.SIGN_PSBT -> {
            val psbt = request.params.first()
            ComputedResult(acc.signer.signPsbt(psbt), psbt)
        }

        else -> throw IllegalArgumentException("Unsupported request type $type")
    }

    private fun addPending(request: PendingBunkerRequest) {
        pending.value = if (pending.value.any { it.request.id == request.request.id }) {
            pending.value
        } else {
            pending.value + request
        }
    }

    fun removePending(id: String) {
        pending.value = pending.value.filter { it.request.id != id }
    }

    /** Mirrors `BunkerRequestUtils.sendResult` for the approval UI. */
    suspend fun approve(
        req: PendingBunkerRequest,
        rememberType: RememberType,
        grantedPermissions: List<RequestedPermission> = emptyList(),
        signPolicy: Int? = null,
    ) {
        PassphraseLock.touch()
        removePending(req.request.id)
        val acc = req.account
        val store = AmberDesktop.store(acc.npub)
        val key = req.localKey
        val defaultRelays = AmberDesktop.defaultRelays()

        var savedApplication = store.getByKey(key)
        if (savedApplication == null && req.request is BunkerRequestConnect && !req.request.secret.isNullOrBlank()) {
            // The bunker:// placeholder is stored under its secret; migrate it
            // to the client's real pubkey on first connect.
            store.getByKey(req.request.secret!!)?.let { placeholder ->
                store.delete(placeholder.app.key)
                savedApplication = placeholder.copy(app = placeholder.app.copy(key = key))
                store.upsert(savedApplication!!)
            }
        }

        val newConnectionRelays = if (req.isNostrConnectUri) {
            req.relays.ifEmpty { defaultRelays }
        } else {
            defaultRelays
        }
        val relays = savedApplication?.app?.normalizedRelays()?.ifEmpty { defaultRelays } ?: newConnectionRelays
        val secret = if (req.request is BunkerRequestConnect) req.request.secret ?: "" else ""

        var application = savedApplication ?: AppWithPermissions(
            app = AppRecord(
                key = key,
                name = req.appName,
                relays = relays.map { it.url },
                url = req.appUrl,
                icon = req.appIcon,
                pubKey = acc.hexKey,
                isConnected = true,
                secret = secret,
                useSecret = secret.isNotBlank(),
                signPolicy = signPolicy ?: acc.signPolicy,
                lastUsed = TimeUtils.now(),
            ),
        )

        application = application.copy(app = application.app.copy(isConnected = true, lastUsed = TimeUtils.now()))

        // Ensure each connection has its own unique signing key.
        if (application.app.localKey.isBlank() && req.request is BunkerRequestConnect && savedApplication == null) {
            application = application.copy(app = application.app.copy(localKey = generateBunkerPrivKey()))
        }

        if (req.type == SignerType.CONNECT) {
            val effectivePolicy = signPolicy ?: acc.signPolicy
            application = application.copy(app = application.app.copy(signPolicy = effectivePolicy))
            applySignPolicy(application, effectivePolicy, grantedPermissions)
            if (application.permissions.none { it.type == SignerType.GET_PUBLIC_KEY.toString() }) {
                application.permissions.add(
                    AppPermissionRecord(SignerType.GET_PUBLIC_KEY.toString(), null, true, RememberType.ALWAYS.screenCode, Long.MAX_VALUE / 1000, 0),
                )
            }
            if (application.permissions.none { it.type == SignerType.PING.toString() }) {
                application.permissions.add(
                    AppPermissionRecord(SignerType.PING.toString(), null, true, RememberType.ALWAYS.screenCode, Long.MAX_VALUE / 1000, 0),
                )
            }
        } else if (rememberType != RememberType.NEVER) {
            acceptOrRejectPermission(application, req.type, req.kind, true, rememberType)
        }

        store.upsert(application)
        store.addHistory(HistoryRecord(key, req.type.toString(), req.kind, TimeUtils.now(), true))

        updateFilter()
        client.connect()

        val response = if (req.type == SignerType.CONNECT) {
            req.nostrConnectSecret.ifBlank { req.result }
        } else {
            req.result
        }
        val signerPrivKey = application.app.localKey.ifEmpty { req.signerPrivKey }

        sendResponse(
            acc,
            signerPrivKey,
            key,
            req.encryptionType,
            BunkerResponse(req.request.id, response, null),
            application.app.normalizedRelays().ifEmpty { relays },
        )
    }

    /** Mirrors `BunkerRequestUtils.sendRejection`. */
    suspend fun reject(
        req: PendingBunkerRequest,
        rememberType: RememberType,
    ) {
        PassphraseLock.touch()
        removePending(req.request.id)
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
        val relays = savedApplication?.app?.normalizedRelays()?.ifEmpty { defaultRelays } ?: newConnectionRelays
        val secret = if (req.request is BunkerRequestConnect) req.request.secret ?: "" else ""

        val application = savedApplication ?: AppWithPermissions(
            app = AppRecord(
                key = key,
                name = req.appName,
                relays = relays.map { it.url },
                pubKey = acc.hexKey,
                isConnected = true,
                secret = secret,
                useSecret = secret.isNotBlank(),
                signPolicy = acc.signPolicy,
                lastUsed = TimeUtils.now(),
            ),
        )

        if (rememberType != RememberType.NEVER) {
            acceptOrRejectPermission(application, req.type, req.kind, false, rememberType)
        }

        if (req.request !is BunkerRequestConnect) {
            store.upsert(application)
            store.addHistory(HistoryRecord(key, req.type.toString(), req.kind, TimeUtils.now(), false))
        }

        val signerPrivKey = application.app.localKey.ifEmpty { req.signerPrivKey }
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
        permissions: List<RequestedPermission>,
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

    private fun addAcceptedPermission(application: AppWithPermissions, perm: RequestedPermission) {
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

    /** Mirrors `AmberUtils.acceptPermission` / rejection with ALL scope. */
    private fun acceptOrRejectPermission(
        application: AppWithPermissions,
        type: SignerType,
        kind: Int?,
        accepted: Boolean,
        rememberType: RememberType,
    ) {
        val until = rememberType.acceptUntil()
        val typeStr = type.toString()

        if (kind != null) {
            application.permissions.removeIf { it.kind == kind && it.type == typeStr && it.relay.isEmpty() }
        } else {
            application.permissions.removeIf { it.type == typeStr && it.type != "SIGN_EVENT" }
        }

        application.permissions.add(
            AppPermissionRecord(
                type = typeStr,
                kind = kind,
                acceptable = accepted,
                rememberType = rememberType.screenCode,
                acceptUntil = if (accepted) until else 0,
                rejectUntil = if (accepted) 0 else until,
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

        val success = retryWithBackoff {
            client.publishAndConfirm(
                event = buildEvent(),
                relayList = relays.toSet(),
                timeoutInSeconds = 5,
            )
        }

        val sanitized = "id=${bunkerResponse.id} ${bunkerResponse.error?.let { "error=$it" } ?: "ok"} sent=$success"
        relays.forEach { store.addLog(it.url, "bunker response", sanitized) }
        return success
    }

    private suspend fun retryWithBackoff(
        maxRetries: Int = 5,
        initialDelayMs: Long = 200L,
        maxDelayMs: Long = 3_200L,
        block: suspend () -> Boolean,
    ): Boolean {
        var currentDelay = initialDelayMs
        repeat(maxRetries) { attempt ->
            delay(currentDelay)
            if (block()) return true
            if (attempt < maxRetries - 1) {
                currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMs)
            }
        }
        return false
    }

    /** Parses a `nostrconnect://` URI and queues it for approval. */
    suspend fun addNostrConnect(uriString: String, account: DesktopAccount): String? {
        try {
            val data = uriString.trim().removePrefix("nostrconnect://")
            val split = data.split("?")
            val pubKey = split.first()
            if (pubKey.length != 64) return "Invalid public key in URI"

            val relays = mutableListOf<NormalizedRelayUrl>()
            var name = ""
            var url = ""
            var image = ""
            var nostrConnectSecret = ""
            val permissions = mutableListOf<RequestedPermission>()

            split.drop(1).joinToString("?").split("&").forEach { param ->
                val parts = param.split("=")
                if (parts.size < 2) return@forEach
                val paramName = parts.first()
                val value = URLDecoder.decode(parts.drop(1).joinToString("="), Charsets.UTF_8)
                when (paramName) {
                    "relay" -> RelayUrlNormalizer.normalizeOrNull(value)?.let { relays.add(it) }
                    "name" -> name = value
                    "url" -> url = value
                    "image" -> image = value
                    "secret" -> nostrConnectSecret = value
                    "perms" -> permissions.addAll(parsePermissionsParam(value))
                    "metadata" -> runCatching {
                        val node = JacksonMapper.mapper.readTree(value)
                        node.get("name")?.asText()?.let { if (it.isNotBlank()) name = it }
                        node.get("url")?.asText()?.let { if (it.isNotBlank()) url = it }
                        node.get("image")?.asText()?.let { if (it.isNotBlank()) image = it }
                        node.get("perms")?.asText()?.let { permissions.addAll(parsePermissionsParam(it)) }
                    }
                }
            }
            // Match Android: drop sign_event / nip perms that carry no valid kind.
            permissions.removeIf { it.kind == null && (it.type == "sign_event" || it.type == "nip") }

            addPending(
                PendingBunkerRequest(
                    request = BunkerRequestConnect(
                        id = UUID.randomUUID().toString().substring(0, 6),
                        remoteKey = pubKey,
                        secret = "",
                        permissions = permissions.joinToString(",") { if (it.kind != null) "${it.type}:${it.kind}" else it.type }.ifBlank { null },
                    ),
                    type = SignerType.CONNECT,
                    account = account,
                    localKey = pubKey,
                    relays = relays,
                    nostrConnectSecret = nostrConnectSecret,
                    appName = name.ifBlank { pubKey.toShortenHex() },
                    appUrl = url,
                    appIcon = image,
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
    ): String {
        val secret = UUID.randomUUID().toString()
        val connPrivKey = generateBunkerPrivKey()
        val app = AppWithPermissions(
            app = AppRecord(
                key = secret,
                name = name,
                relays = relays.map { it.url },
                pubKey = account.hexKey,
                isConnected = false,
                secret = secret,
                useSecret = true,
                signPolicy = account.signPolicy,
                localKey = connPrivKey,
            ),
        )
        AmberDesktop.store(account.npub).upsert(app)
        updateFilter()
        client.connect()
        val relayParams = relays.joinToString(separator = "&") { "relay=${it.url}" }
        return "bunker://${localPubKeyFromPrivKey(connPrivKey)}?$relayParams&secret=$secret"
    }

    private fun parsePermissionsParam(permissions: String?): List<RequestedPermission> {
        if (permissions.isNullOrBlank()) return emptyList()
        return permissions.split(",").mapNotNull { perm ->
            if (perm.isBlank()) return@mapNotNull null
            val parts = perm.split(":")
            RequestedPermission(parts.first().trim(), parts.getOrNull(1)?.toIntOrNull())
        }
    }

    companion object {
        fun typeFromMethod(method: String): SignerType = when (method) {
            "connect" -> SignerType.CONNECT
            "sign_event" -> SignerType.SIGN_EVENT
            "get_public_key" -> SignerType.GET_PUBLIC_KEY
            "nip04_encrypt" -> SignerType.NIP04_ENCRYPT
            "nip04_decrypt" -> SignerType.NIP04_DECRYPT
            "nip44_encrypt" -> SignerType.NIP44_ENCRYPT
            "nip44_decrypt" -> SignerType.NIP44_DECRYPT
            "nip44v3_encrypt" -> SignerType.NIP44_V3_ENCRYPT
            "nip44v3_decrypt" -> SignerType.NIP44_V3_DECRYPT
            "decrypt_zap_event" -> SignerType.DECRYPT_ZAP_EVENT
            "ping" -> SignerType.PING
            "switch_relays" -> SignerType.SWITCH_RELAYS
            "sign_psbt" -> SignerType.SIGN_PSBT
            "logout" -> SignerType.LOGOUT
            else -> SignerType.INVALID
        }

        /**
         * Maps a requested permission string to the stored permission type(s)
         * the request path actually queries. Content-scoped or generic
         * encrypt/decrypt perms (Amber uses `encrypt_clear_text`,
         * `encrypt_event`, `encrypt_tag_array`; standard NIP-46 uses
         * `nip04_encrypt`/`nip44_encrypt`) grant both NIP variants because the
         * desktop request path is keyed by NIP, not by content type.
         */
        fun expandPermissionTypes(type: String): List<String> = when (type.lowercase()) {
            "connect" -> listOf(SignerType.CONNECT.toString())
            "sign_event" -> listOf(SignerType.SIGN_EVENT.toString())
            "get_public_key" -> listOf(SignerType.GET_PUBLIC_KEY.toString())
            "nip04_encrypt" -> listOf(SignerType.NIP04_ENCRYPT.toString())
            "nip04_decrypt" -> listOf(SignerType.NIP04_DECRYPT.toString())
            "nip44_encrypt" -> listOf(SignerType.NIP44_ENCRYPT.toString())
            "nip44_decrypt" -> listOf(SignerType.NIP44_DECRYPT.toString())
            "nip44v3_encrypt" -> listOf(SignerType.NIP44_V3_ENCRYPT.toString())
            "nip44v3_decrypt" -> listOf(SignerType.NIP44_V3_DECRYPT.toString())
            "encrypt_clear_text", "encrypt_event", "encrypt_tag_array", "encrypt" ->
                listOf(SignerType.NIP04_ENCRYPT.toString(), SignerType.NIP44_ENCRYPT.toString())
            "decrypt_clear_text", "decrypt_event", "decrypt_tag_array", "decrypt" ->
                listOf(SignerType.NIP04_DECRYPT.toString(), SignerType.NIP44_DECRYPT.toString())
            "decrypt_zap_event" -> listOf(SignerType.DECRYPT_ZAP_EVENT.toString())
            "ping" -> listOf(SignerType.PING.toString())
            "sign_psbt" -> listOf(SignerType.SIGN_PSBT.toString())
            else -> listOf(type.uppercase())
        }

        fun normalizePermissionType(type: String): String = expandPermissionTypes(type).first()
    }
}

/** Convenience accessor mirroring `ApplicationEntity.localPubKey`. */
fun AppRecord.localPubKey(): String = if (localKey.isNotEmpty()) localPubKeyFromPrivKey(localKey) else ""
