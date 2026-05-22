package com.greenart7c3.nostrsigner.service

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay

private const val BACKUP_KIND = 30078
private const val BACKUP_D_TAG = "amber-app-backup"
private const val INBOX_KIND = 10002
private const val INBOX_FETCH_TIMEOUT_MS = 10_000L
private const val BACKUP_FETCH_TIMEOUT_MS = 15_000L
private const val PAYLOAD_VERSION = 1

private val AGGREGATOR_RELAY = RelayUrlNormalizer.normalizeOrNull("wss://aggr.nostr.land/")

data class BackupPermission(
    @param:JsonProperty("type") val type: String,
    @param:JsonProperty("kind") val kind: Int?,
    @param:JsonProperty("acceptable") val acceptable: Boolean,
    @param:JsonProperty("rememberType") val rememberType: Int,
    @param:JsonProperty("acceptUntil") val acceptUntil: Long,
    @param:JsonProperty("rejectUntil") val rejectUntil: Long,
    @param:JsonProperty("relay") val relay: String = "",
)

data class BackupApplication(
    @param:JsonProperty("key") val key: String,
    @param:JsonProperty("name") val name: String,
    @param:JsonProperty("relays") val relays: List<String>,
    @param:JsonProperty("url") val url: String,
    @param:JsonProperty("icon") val icon: String,
    @param:JsonProperty("description") val description: String,
    @param:JsonProperty("pubKey") val pubKey: String,
    @param:JsonProperty("isConnected") val isConnected: Boolean,
    @param:JsonProperty("secret") val secret: String,
    @param:JsonProperty("useSecret") val useSecret: Boolean,
    @param:JsonProperty("signPolicy") val signPolicy: Int,
    @param:JsonProperty("closeApplication") val closeApplication: Boolean,
    @param:JsonProperty("deleteAfter") val deleteAfter: Long,
    @param:JsonProperty("lastUsed") val lastUsed: Long,
    @param:JsonProperty("localKey") val localKey: String = "",
    @param:JsonProperty("permissions") val permissions: List<BackupPermission>,
)

data class BackupPayload(
    @param:JsonProperty("v") val version: Int = PAYLOAD_VERSION,
    @param:JsonProperty("ts") val timestamp: Long = TimeUtils.now(),
    @param:JsonProperty("applications") val applications: List<BackupApplication>,
)

sealed interface RestoreResult {
    data class Success(val apps: Int, val permissions: Int) : RestoreResult
    data object NoBackupFound : RestoreResult
    data class Failed(val message: String) : RestoreResult
}

object ApplicationBackup {
    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    suspend fun buildPayload(npub: String, account: Account): BackupPayload {
        val dao = Amber.instance.dao(npub)
        val apps = dao.getAll(account.hexKey)
        val backupApps = apps.map { app ->
            val perms = dao.getAllByKey(app.key)
            BackupApplication(
                key = app.key,
                name = app.name,
                relays = app.relays.map { it.url },
                url = app.url,
                icon = app.icon,
                description = app.description,
                pubKey = app.pubKey,
                isConnected = app.isConnected,
                secret = app.secret,
                useSecret = app.useSecret,
                signPolicy = app.signPolicy,
                closeApplication = app.closeApplication,
                deleteAfter = app.deleteAfter,
                lastUsed = app.lastUsed,
                localKey = app.localKey,
                permissions = perms.map { p ->
                    BackupPermission(
                        type = p.type,
                        kind = p.kind,
                        acceptable = p.acceptable,
                        rememberType = p.rememberType,
                        acceptUntil = p.acceptUntil,
                        rejectUntil = p.rejectUntil,
                        relay = p.relay,
                    )
                },
            )
        }
        return BackupPayload(applications = backupApps)
    }

    fun toJson(payload: BackupPayload): String = mapper.writeValueAsString(payload)

    fun fromJson(json: String): BackupPayload = mapper.readValue(json)

    /**
     * Returns inbox relays (NIP-65 read-marker) ∪ default profile relays ∪ aggregator.
     * Falls back to just profile relays + aggregator if the inbox fetch fails.
     */
    suspend fun resolveBackupRelays(account: Account): Set<NormalizedRelayUrl> {
        val settings = Amber.instance.settings
        val out = mutableSetOf<NormalizedRelayUrl>()
        out.addAll(settings.defaultProfileRelays)
        AGGREGATOR_RELAY?.let { out.add(it) }
        out.addAll(fetchInboxRelays(account))
        return out
    }

    private suspend fun fetchInboxRelays(account: Account): Set<NormalizedRelayUrl> {
        val client = Amber.instance.client
        val profileRelays = Amber.instance.settings.defaultProfileRelays
        if (profileRelays.isEmpty()) return emptySet()

        val subId = UUID.randomUUID().toString()
        val latest = mutableMapOf<Long, Event>()

        val listener = object : RelayConnectionListener {
            override fun onIncomingMessage(relay: IRelayClient, msgStr: String, msg: Message) {
                if (msg is EventMessage && msg.subId == subId && msg.event.kind == INBOX_KIND && msg.event.pubKey == account.hexKey) {
                    if (msg.event.verify()) {
                        latest[msg.event.createdAt] = msg.event
                    }
                }
                super.onIncomingMessage(relay, msgStr, msg)
            }
        }

        client.addConnectionListener(listener)
        try {
            val filter = Filter(
                kinds = listOf(INBOX_KIND),
                authors = listOf(account.hexKey),
                limit = 1,
            )
            client.subscribe(subId, profileRelays.associateWith { listOf(filter) })
            delay(INBOX_FETCH_TIMEOUT_MS)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(Amber.TAG, "ApplicationBackup: inbox relay fetch failed", e)
        } finally {
            runCatching { client.unsubscribe(subId) }
            client.removeConnectionListener(listener)
        }

        val newest = latest.maxByOrNull { it.key }?.value ?: return emptySet()
        return newest.tags
            .asSequence()
            .filter { it.size >= 2 && it[0] == "r" }
            .mapNotNull { tag ->
                val url = tag[1]
                val marker = tag.getOrNull(2)?.lowercase()
                if (marker == null || marker == "read") {
                    RelayUrlNormalizer.normalizeOrNull(url)
                } else {
                    null
                }
            }
            .toSet()
    }

    suspend fun publishBackup(npub: String, account: Account): Boolean {
        if (BuildFlavorChecker.isOfflineFlavor()) return false
        try {
            val payload = buildPayload(npub, account)
            if (payload.applications.isEmpty()) {
                Log.d(Amber.TAG, "ApplicationBackup: no applications to backup for $npub, skipping")
                return true
            }
            val json = toJson(payload)
            val encrypted = account.nip44Encrypt(json, account.hexKey)
            val event = account.signSync<Event>(
                TimeUtils.now(),
                BACKUP_KIND,
                arrayOf(
                    arrayOf("d", BACKUP_D_TAG),
                    arrayOf("client", "amber"),
                ),
                encrypted,
            )
            val relays = resolveBackupRelays(account)
            if (relays.isEmpty()) {
                Log.w(Amber.TAG, "ApplicationBackup: no relays resolved for backup")
                return false
            }
            val ok = Amber.instance.client.publishAndConfirm(event, relays)
            Log.d(Amber.TAG, "ApplicationBackup: publish for $npub result=$ok relays=${relays.size}")
            return ok
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(Amber.TAG, "ApplicationBackup: failed to publish backup for $npub", e)
            return false
        }
    }

    suspend fun fetchLatestBackupEvent(account: Account, relays: Set<NormalizedRelayUrl>): Event? {
        if (BuildFlavorChecker.isOfflineFlavor()) return null
        if (relays.isEmpty()) return null

        val client = Amber.instance.client
        val subId = UUID.randomUUID().toString()
        val received = mutableMapOf<Long, Event>()

        val listener = object : RelayConnectionListener {
            override fun onIncomingMessage(relay: IRelayClient, msgStr: String, msg: Message) {
                if (msg is EventMessage &&
                    msg.subId == subId &&
                    msg.event.kind == BACKUP_KIND &&
                    msg.event.pubKey == account.hexKey
                ) {
                    val identifier = msg.event.tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.getOrNull(1)
                    if (identifier == BACKUP_D_TAG && msg.event.verify()) {
                        received[msg.event.createdAt] = msg.event
                    }
                }
                super.onIncomingMessage(relay, msgStr, msg)
            }
        }

        client.addConnectionListener(listener)
        try {
            val filter = Filter(
                kinds = listOf(BACKUP_KIND),
                authors = listOf(account.hexKey),
                tags = mapOf("d" to listOf(BACKUP_D_TAG)),
                limit = 1,
            )
            client.subscribe(subId, relays.associateWith { listOf(filter) })
            delay(BACKUP_FETCH_TIMEOUT_MS)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(Amber.TAG, "ApplicationBackup: failed to fetch backup event", e)
        } finally {
            runCatching { client.unsubscribe(subId) }
            client.removeConnectionListener(listener)
        }

        return received.maxByOrNull { it.key }?.value
    }

    suspend fun decryptPayload(event: Event, account: Account): BackupPayload? = try {
        val json = account.nip44Decrypt(event.content, account.hexKey)
        fromJson(json)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e(Amber.TAG, "ApplicationBackup: failed to decrypt/parse backup payload", e)
        null
    }

    suspend fun restoreFromPayload(npub: String, payload: BackupPayload): RestoreResult = try {
        val dao = Amber.instance.dao(npub)
        var apps = 0
        var permissions = 0
        payload.applications.forEach { backupApp ->
            val entity = ApplicationEntity(
                key = backupApp.key,
                name = backupApp.name,
                relays = backupApp.relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) },
                url = backupApp.url,
                icon = backupApp.icon,
                description = backupApp.description,
                pubKey = backupApp.pubKey,
                isConnected = backupApp.isConnected,
                secret = backupApp.secret,
                useSecret = backupApp.useSecret,
                signPolicy = backupApp.signPolicy,
                closeApplication = backupApp.closeApplication,
                deleteAfter = backupApp.deleteAfter,
                lastUsed = backupApp.lastUsed,
                localKey = backupApp.localKey,
            )
            val perms = backupApp.permissions.map { p ->
                ApplicationPermissionsEntity(
                    id = null,
                    pkKey = backupApp.key,
                    type = p.type,
                    kind = p.kind,
                    acceptable = p.acceptable,
                    rememberType = p.rememberType,
                    acceptUntil = p.acceptUntil,
                    rejectUntil = p.rejectUntil,
                    relay = p.relay,
                )
            }
            dao.insertApplicationWithPermissions(
                ApplicationWithPermissions(
                    application = entity,
                    permissions = perms.toMutableList(),
                ),
            )
            apps++
            permissions += perms.size
        }
        Amber.instance.profileSubscription.closeSub()
        Amber.instance.notificationSubscription.closeAllSubs()
        Amber.instance.disconnectIntentionally()
        Amber.instance.checkForNewRelaysAndUpdateAllFilters(shouldReconnect = true)
        RestoreResult.Success(apps, permissions)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e(Amber.TAG, "ApplicationBackup: failed to restore payload for $npub", e)
        RestoreResult.Failed(e.message ?: "Unknown error")
    }

    suspend fun restoreFromRelays(npub: String, account: Account): RestoreResult {
        val relays = resolveBackupRelays(account)
        val event = fetchLatestBackupEvent(account, relays) ?: return RestoreResult.NoBackupFound
        val payload = decryptPayload(event, account) ?: return RestoreResult.Failed("Failed to decrypt backup")
        return restoreFromPayload(npub, payload)
    }
}
