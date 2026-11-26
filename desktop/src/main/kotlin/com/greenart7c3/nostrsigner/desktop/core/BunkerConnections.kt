package com.greenart7c3.nostrsigner.desktop.core

import com.greenart7c3.nostrsigner.common.EncryptionType
import com.greenart7c3.nostrsigner.common.NostrConnectUri
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.generateBunkerPrivKey
import com.greenart7c3.nostrsigner.common.localPubKeyFromPrivKey
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.containsNip
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID

/**
 * Drops connections whose "delete after" time has passed, in every
 * account, and refreshes the relay filter if any were removed. Mirrors
 * the Android `ClearLogsWorker` cleanup.
 */
suspend fun BunkerEngine.pruneExpiredApplications(now: Long = TimeUtils.now()) {
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

/**
 * Parses a `nostrconnect://` URI and queues it for approval. The client's key becomes
 * the connection's identity and the p tag on every reply, so only a URI the shared
 * parser accepts as well-formed is queued.
 */
fun BunkerEngine.addNostrConnect(uriString: String, account: DesktopAccount): String? {
    try {
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
suspend fun BunkerEngine.createBunkerConnection(
    account: DesktopAccount,
    name: String,
    relays: List<NormalizedRelayUrl>,
    deleteAfter: Long = 0L,
): String {
    val secret = UUID.randomUUID().toString()
    val connPrivKey = generateBunkerPrivKey()
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
