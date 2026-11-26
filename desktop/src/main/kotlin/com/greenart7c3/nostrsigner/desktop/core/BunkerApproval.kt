package com.greenart7c3.nostrsigner.desktop.core

import com.greenart7c3.nostrsigner.common.Grant
import com.greenart7c3.nostrsigner.common.RequestedPermission
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.generateBunkerPrivKey
import com.greenart7c3.nostrsigner.common.rememberedGrant
import com.greenart7c3.nostrsigner.common.revertedConnectKey
import com.greenart7c3.nostrsigner.common.revertedGrants
import com.greenart7c3.nostrsigner.common.signPolicyGrants
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.basicPermissions
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val ALWAYS_GRANTED_ON_CONNECT = listOf(SignerType.GET_PUBLIC_KEY, SignerType.PING)

private val PendingBunkerRequest.isConnect: Boolean get() = request is BunkerRequestConnect || type == SignerType.CONNECT

/**
 * Mirrors `BunkerRequestUtils.sendResult`. Runs on the engine's
 * application scope (a SupervisorJob), never the caller's — approving
 * removes the request card from the UI, so a caller-scoped coroutine would
 * be canceled mid-flight (during the publish retry/backoff) before the
 * relay response is ever sent. Returns the [Job] so callers/tests can
 * await completion if they need to.
 */
fun BunkerEngine.approve(
    req: PendingBunkerRequest,
    rememberType: RememberType,
    grantedPermissions: List<Permission> = emptyList(),
    signPolicy: Int? = null,
    deleteAfter: Long = 0L,
    accountNpub: String? = null,
    encryptionScope: EncryptionScope = req.defaultScope,
): Job = scope.launch { doApprove(req, rememberType, grantedPermissions, signPolicy, deleteAfter, accountNpub, encryptionScope) }

/**
 * Mirrors `BunkerRequestUtils.sendRejection`. Runs on the application
 * scope for the same reason as [approve] — the rejection response must be
 * published even though the request card is gone.
 */
fun BunkerEngine.reject(
    req: PendingBunkerRequest,
    rememberType: RememberType,
    encryptionScope: EncryptionScope = req.defaultScope,
): Job = scope.launch { doReject(req, rememberType, encryptionScope) }

private suspend fun BunkerEngine.doApprove(
    req: PendingBunkerRequest,
    rememberType: RememberType,
    grantedPermissions: List<Permission>,
    signPolicy: Int?,
    deleteAfter: Long,
    accountNpub: String?,
    encryptionScope: EncryptionScope,
) {
    PassphraseLock.touch()
    removePending(req)
    val acc = approvingAccount(req, accountNpub)
    val store = AmberDesktop.store(acc.npub)
    val key = req.localKey
    val savedApplication = savedConnection(req, acc, store)
    val grantsBefore = savedApplication?.permissions?.toList().orEmpty()
    val relays = approvalRelays(req, savedApplication)

    val connectionRevokedWhileQueued = savedApplication == null && !req.isConnect
    if (connectionRevokedWhileQueued) {
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
    if (!delivered && rememberType != RememberType.NEVER) revertApproval(req, store, application.app, grantsBefore, grantsAfter)
}

private fun BunkerEngine.revertApproval(req: PendingBunkerRequest, store: AccountStore, approved: AppRecord, grantsBefore: List<AppPermissionRecord>, grantsAfter: List<AppPermissionRecord>) {
    val key = approved.key
    store.addLog(key, "bunker response", "The reply did not reach the client, so its approval was taken back")
    failedReplies.tryEmit(Unit)
    if (req.type == SignerType.CONNECT) {
        store.delete(key)
        revertedConnectKey(req.isNostrConnectUri, approved.secret)?.let { secret ->
            store.upsert(AppWithPermissions(approved.copy(key = secret, isConnected = false), grantsAfter.toMutableList()))
        }
        return
    }
    val current = store.lookup.getByKey(key) ?: return
    store.upsert(current.copy(permissions = revertedGrants(current.permissions, grantsBefore, grantsAfter).toMutableList()))
}

private suspend fun approvingAccount(req: PendingBunkerRequest, accountNpub: String?): DesktopAccount = accountNpub
    ?.takeIf { it != req.account.npub && req.canSwitchAccount }
    ?.let { AmberDesktop.account(it) }
    ?: req.account

@Suppress("NoComments")
private fun savedConnection(req: PendingBunkerRequest, acc: DesktopAccount, store: AccountStore): AppWithPermissions? {
    val key = req.localKey
    val secret = (req.request as? BunkerRequestConnect)?.secret?.takeIf { it.isNotBlank() }
    if (secret != null && acc.npub != req.account.npub) {
        val sourceStore = AmberDesktop.store(req.account.npub)
        sourceStore.lookup.getByKey(secret)?.let { placeholder ->
            sourceStore.delete(placeholder.app.key)
            store.upsert(placeholder.copy(app = placeholder.app.copy(pubKey = acc.hexKey)))
        }
    }

    // Expired counts as gone: approving a card queued before it expired must not sign for it.
    var savedApplication = store.lookup.getByKey(key)?.takeUnless { it.app.isExpired() }
    if (connectionToReplace(req, acc.npub) != null) {
        // Replaced, not merged: the old connection's grants and relays must not carry over.
        store.delete(key)
        savedApplication = null
    }
    if (savedApplication == null && secret != null) {
        store.lookup.getByKey(secret)?.let { placeholder ->
            store.delete(placeholder.app.key)
            savedApplication = placeholder.copy(app = placeholder.app.copy(key = key))
            store.upsert(savedApplication)
        }
    }
    return savedApplication
}

private fun approvalRelays(req: PendingBunkerRequest, savedApplication: AppWithPermissions?): List<NormalizedRelayUrl> {
    if (req.isLocalSocket) return emptyList()
    val newConnectionRelays = if (req.isNostrConnectUri) req.relays.ifEmpty { AmberDesktop.newBunkerRelays() } else AmberDesktop.newBunkerRelays()
    return savedApplication?.app?.normalizedRelays()?.ifEmpty { BunkerRelays.defaultRelays() } ?: newConnectionRelays
}

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
            localKey = if (req.request is BunkerRequestConnect && !req.isLocalSocket) generateBunkerPrivKey() else "",
        ),
    )
}

private fun applyConnect(
    application: AppWithPermissions,
    signPolicy: Int,
    deleteAfter: Long,
    grantedPermissions: List<Permission>,
): AppWithPermissions {
    val result = application.copy(app = application.app.copy(signPolicy = signPolicy, deleteAfter = deleteAfter))
    applySignPolicy(result, signPolicy, grantedPermissions)
    ALWAYS_GRANTED_ON_CONNECT.forEach { type ->
        if (result.permissions.none { it.type == type.toString() }) {
            result.permissions.add(AppPermissionRecord(type.toString(), null, true, RememberType.ALWAYS.screenCode, Long.MAX_VALUE / 1000, 0))
        }
    }
    return result
}

private suspend fun BunkerEngine.answer(
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
private suspend fun BunkerEngine.doReject(
    req: PendingBunkerRequest,
    rememberType: RememberType,
    encryptionScope: EncryptionScope,
) {
    PassphraseLock.touch()
    removePending(req)
    val acc = req.account
    val store = AmberDesktop.store(acc.npub)
    val key = req.localKey

    val savedApplication = store.lookup.getByKey(key)
    val defaultRelays = BunkerRelays.defaultRelays()
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

    if (savedApplication != null && !req.isConnect) {
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

private fun applySignPolicy(
    application: AppWithPermissions,
    signPolicy: Int,
    permissions: List<Permission>,
) {
    val grants = signPolicyGrants(signPolicy, basicPermissions.map { it.requested() }, permissions.filter { it.checked }.map { it.requested() }) { type, kind ->
        application.permissions.any { it.type == type && it.kind == kind }
    }
    grants?.forEach { application.permissions.add(it.toRecord()) }
}

private fun Permission.requested() = RequestedPermission(type, kind)

private fun Grant.toRecord() = AppPermissionRecord(type, kind, acceptable, rememberType, acceptUntil, rejectUntil, relay)

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
    val remembered = rememberedGrant(type, type.contentPermissionType(content), scope == EncryptionScope.ALL, kind, accepted, rememberType.screenCode, relay, TimeUtils.now())
    application.permissions.removeIf { remembered.replaces(it.type, it.kind, it.relay) }
    application.permissions.add(remembered.grant.toRecord())
}
