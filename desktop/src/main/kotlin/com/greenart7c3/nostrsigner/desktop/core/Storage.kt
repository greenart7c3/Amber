package com.greenart7c3.nostrsigner.desktop.core

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.greenart7c3.nostrsigner.common.RELAY_AUTH_KIND
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.isAllRelaysGrant
import com.vitorpamplona.quartz.utils.TimeUtils
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

/** Header on a file whose JSON payload is AES-GCM encrypted with the master key, bound to its account and file. */
@Suppress("NoComments")
private const val ENC_MARKER = "AMBERENC2:"

/**
 * Header on a rollback-protected file: `AMBERENC3:<generation>:<payload>`, the
 * payload bound to its account, file and generation (see [StoreGenerations]).
 */
@Suppress("NoComments")
private const val ENC_MARKER_V3 = "AMBERENC3:"

/**
 * The newest generation written for each rollback-protected file, kept in the
 * OS credential store, outside the data directory. Each write bumps it, so an
 * older copy of a file put back into the data directory (a restored backup,
 * a synced folder, an offline edit of the disk) is refused instead of
 * reinstating connections and grants the user has since removed. Where the
 * credential store is unavailable there is nothing to compare against and
 * files are accepted as before; a process running as the user can rewrite the
 * credential store too, so this does not protect against one.
 */
@Suppress("NoComments")
internal object StoreGenerations {
    internal var storeFor: (String) -> PasswordStore = { context -> OsCredentialStore(account = "store-generation:$context") }

    private val lastSeenGeneration = ConcurrentHashMap<String, Long>()

    @Volatile private var warnedUnavailable = false

    fun expected(context: String): Long? = storeFor(context).load()?.toLongOrNull()

    fun next(context: String): Long = (lastSeenGeneration[context] ?: expected(context) ?: 0L) + 1

    fun accepted(context: String, generation: Long, expected: Long?) {
        lastSeenGeneration[context] = generation
        if ((expected ?: 0L) < generation) record(context, generation)
    }

    fun record(context: String, generation: Long) {
        lastSeenGeneration[context] = generation
        if (!storeFor(context).store(generation.toString()) && !warnedUnavailable) {
            warnedUnavailable = true
            AmberLogger.e("Storage", "The credential store is unavailable; encrypted files are not rollback-protected")
        }
    }

    fun forget(context: String) {
        lastSeenGeneration.remove(context)
        storeFor(context).delete()
    }
}

private fun atomicWrite(file: File, payload: String) {
    AppDirs.writeAtomically(file, payload.toByteArray(Charsets.UTF_8))
}

/**
 * Reads a JSON file. A file that exists but cannot be parsed is moved aside
 * rather than treated as empty: otherwise the next write would replace it,
 * and for accounts.json that destroys the only copy of the encrypted keys.
 * When [backup] is given and parses, it is restored in the file's place.
 */
@Suppress("NoComments")
private inline fun <reified T> readJson(file: File, backup: File? = null): T? {
    if (!file.exists()) return null
    try {
        return mapper.readValue<T>(file.readText())
    } catch (e: Exception) {
        val moved = AppDirs.quarantine(file)
        AmberLogger.e("Storage", "Failed to read ${file.name}; kept it as ${moved?.name}", e)
    }
    if (backup == null || !backup.exists()) return null
    return try {
        val raw = backup.readText()
        mapper.readValue<T>(raw).also {
            atomicWrite(file, raw)
            AmberLogger.i("Storage", "Restored ${file.name} from ${backup.name}")
        }
    } catch (e: Exception) {
        AmberLogger.e("Storage", "Backup ${backup.name} is unreadable too", e)
        null
    }
}

private fun writeJson(file: File, value: Any, backup: File? = null) {
    if (backup != null && file.exists()) AppDirs.writeAtomically(backup, file.readBytes())
    atomicWrite(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value))
}

/**
 * Reads a JSON file that may be encrypted. Encrypted files carry the
 * [ENC_MARKER] header, with the ciphertext bound to [context]. While a
 * passphrase lock is set, nothing else is accepted: a plaintext or malformed
 * file is moved aside (never overwritten), so a data-directory writer cannot
 * plant a trusted store. Without the lock, stores are plaintext by design.
 */
@Suppress("NoComments")
private inline fun <reified T> readSecure(file: File, context: String, rollbackProtected: Boolean = false): T? {
    if (!file.exists()) return null
    return try {
        mapper.readValue<T>(storeJson(file, context, rollbackProtected))
    } catch (e: DesktopKeyStore.LockedException) {
        AmberLogger.e("Storage", "Cannot read ${file.name} while the key store is locked")
        null
    } catch (e: Exception) {
        val moved = AppDirs.quarantine(file)
        AmberLogger.e("Storage", "Failed to read ${file.name}; kept it as ${moved?.name}", e)
        null
    }
}

private fun storeJson(file: File, context: String, rollbackProtected: Boolean): String {
    val raw = file.readText()
    return when {
        rollbackProtected && raw.startsWith(ENC_MARKER_V3) -> rollbackProtectedJson(file, raw.substring(ENC_MARKER_V3.length), context)
        rollbackProtected && raw.startsWith(ENC_MARKER) -> throw SecurityException("Refusing an encrypted ${file.name} without a generation header")
        raw.startsWith(ENC_MARKER) -> DesktopKeyStore.decryptString(requireBoundPayload(raw.substring(ENC_MARKER.length)), context)
        PassphraseLock.isEnabled() -> throw SecurityException("Refusing an unbound ${file.name} while a passphrase lock is set")
        else -> raw
    }
}

private fun generationBoundContext(context: String, generation: Long) = "$context#$generation"

private fun rollbackProtectedJson(file: File, rest: String, context: String): String {
    val generation = rest.substringBefore(':').toLongOrNull() ?: throw SecurityException("Malformed generation header")
    val decrypted = DesktopKeyStore.decryptString(requireBoundPayload(rest.substringAfter(':')), generationBoundContext(context, generation))
    val expected = StoreGenerations.expected(context)
    if (expected != null && generation < expected) {
        throw SecurityException("${file.name} is generation $generation, older than the last one written ($expected): refusing a rolled-back copy")
    }
    StoreGenerations.accepted(context, generation, expected)
    return decrypted
}

private fun requireBoundPayload(payload: String): String {
    if (!DesktopKeyStore.isBound(payload)) throw SecurityException("Unbound payload under the bound header")
    return payload
}

/**
 * Writes a JSON file, encrypting the payload with the master key whenever a
 * passphrase lock is set. Refuses to write while locked rather than clobber
 * ciphertext with an empty document. Returns whether the file was written;
 * callers keeping dirty state use the result to retry later.
 *
 * A generation is recorded only once the file is in place: a crash in between
 * leaves a file newer than the record, which the next read accepts and records.
 */
@Suppress("NoComments")
private fun writeSecure(file: File, value: Any, context: String, rollbackProtected: Boolean = false): Boolean {
    val encrypt = PassphraseLock.isEnabled()
    if (encrypt && !DesktopMasterKey.isMasterKeyAvailable()) {
        AmberLogger.e("Storage", "Refusing to write ${file.name} while the key store is locked")
        return false
    }
    val json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)
    when {
        !encrypt -> atomicWrite(file, json)
        rollbackProtected -> {
            val generation = StoreGenerations.next(context)
            atomicWrite(file, "$ENC_MARKER_V3$generation:" + DesktopKeyStore.encryptString(json, generationBoundContext(context, generation)))
            StoreGenerations.record(context, generation)
        }
        else -> atomicWrite(file, ENC_MARKER + DesktopKeyStore.encryptString(json, context))
    }
    return true
}

object AmberLogger {
    fun d(tag: String, message: String) {
        if (System.getenv("AMBER_DEBUG") != null) println("D/$tag: $message")
    }

    fun i(tag: String, message: String) {
        println("I/$tag: $message")
    }

    fun e(tag: String, message: String, e: Throwable? = null) {
        System.err.println("E/$tag: $message")
        e?.printStackTrace()
    }
}

/**
 * Constant-time equality for secret material (connection secrets, secret
 * hashes): every byte is compared instead of short-circuiting on the first
 * mismatch, so callers comparing attacker-supplied secrets don't leak the
 * expected value's prefix through timing.
 */
@Suppress("NoComments")
internal fun constantTimeEquals(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

/** Read-only queries over an account's connected applications and their grants. */
class AppLookup(private val apps: StateFlow<List<AppWithPermissions>>) {
    fun getByKey(key: String): AppWithPermissions? = apps.value.firstOrNull { it.app.key == key }

    fun getBySecret(secret: String): AppWithPermissions? = apps.value.firstOrNull { constantTimeEquals(it.app.secret, secret) && it.app.useSecret }

    fun getPermission(key: String, type: String, kind: Int? = null): AppPermissionRecord? = getByKey(key)?.permissions?.firstOrNull {
        it.type == type && it.kind == kind
    }

    /**
     * The kind-22242 grant deciding a relay auth for [relayHost], like the
     * Android `getPermissionForRelay` then `getWildcardRelayPermission`: a
     * grant for this relay first, then an all-relays one ("*" or blank).
     */
    fun getRelayAuthPermission(key: String, relayHost: String): AppPermissionRecord? {
        val grants = getByKey(key)?.permissions?.filter { it.type == SignerType.SIGN_EVENT.toString() && it.kind == RELAY_AUTH_KIND }.orEmpty()
        return grants.firstOrNull { relayHost.isNotEmpty() && it.relay == relayHost } ?: grants.firstOrNull { isAllRelaysGrant(it.relay) }
    }
}

/**
 * Per-account persistence: connected applications with their permissions,
 * request history, and relay/bunker logs. The desktop app keeps everything
 * in memory as [MutableStateFlow]s (so Compose observes changes directly)
 * and writes JSON files under the account's data directory on every change.
 * Those files are the sensitive metadata about who you sign for, so they are
 * encrypted at rest with the master key whenever a passphrase lock is set.
 * History and logs persist with a short debounce, so a relay flood does not
 * rewrite a whole AES-GCM file per event.
 */
class AccountStore(val npub: String) {
    private val dir = AppDirs.accountDir(npub)
    private val appsFile = File(dir, "applications.json")
    private val historyFile = File(dir, "history.json")
    private val logsFile = File(dir, "logs.json")

    val apps = MutableStateFlow(readSecure<List<AppWithPermissions>>(appsFile, cipherContext(appsFile), rollbackProtected = true) ?: emptyList())
    val history = MutableStateFlow(readSecure<List<HistoryRecord>>(historyFile, cipherContext(historyFile)) ?: emptyList())
    val logs = MutableStateFlow(readSecure<List<LogRecord>>(logsFile, cipherContext(logsFile)) ?: emptyList())

    private var historyDirty = false
    private var logsDirty = false
    private var flushQueued = false

    init {
        liveStores.add(this)
    }

    val lookup = AppLookup(apps)

    private fun cipherContext(file: File) = "$npub/${file.name}"

    @Synchronized
    fun upsert(app: AppWithPermissions) {
        apps.value = apps.value.filter { it.app.key != app.app.key } + app
        writeSecure(appsFile, apps.value, cipherContext(appsFile), rollbackProtected = true)
    }

    /**
     * Removes connections whose "delete after" time has passed, mirroring the
     * Android `deleteOldApplications`. Returns how many were removed.
     */
    @Synchronized
    fun deleteExpiredApps(now: Long = TimeUtils.now()): Int {
        val expired = apps.value.filter { it.app.isExpired(now) }.map { it.app.key }.toSet()
        if (expired.isEmpty()) return 0
        apps.value = apps.value.filter { it.app.key !in expired }
        history.value = history.value.filter { it.appKey !in expired }
        writeSecure(appsFile, apps.value, cipherContext(appsFile), rollbackProtected = true)
        writeHistoryIncludingVolatile()
        return expired.size
    }

    @Synchronized
    fun delete(key: String) {
        apps.value = apps.value.filter { it.app.key != key }
        history.value = history.value.filter { it.appKey != key }
        writeSecure(appsFile, apps.value, cipherContext(appsFile), rollbackProtected = true)
        writeHistoryIncludingVolatile()
    }

    private fun writeHistoryIncludingVolatile() {
        writeSecure(historyFile, history.value, cipherContext(historyFile))
        historyDirty = false
    }

    @Synchronized
    fun addHistory(record: HistoryRecord) {
        history.value = (history.value + record).takeLast(MAX_HISTORY)
        historyDirty = true
        scheduleVolatileFlushHoldingLock()
    }

    @Synchronized
    fun addLog(url: String, type: String, message: String) {
        AmberLogger.d("Amber", "$url: $message")
        logs.value = (logs.value + LogRecord(url, type, message, System.currentTimeMillis())).takeLast(MAX_LOGS)
        logsDirty = true
        scheduleVolatileFlushHoldingLock()
    }

    private fun scheduleVolatileFlushHoldingLock() {
        if (flushQueued) return
        flushQueued = true
        flushScheduler.schedule({ runCatching { flushVolatile() } }, FLUSH_DEBOUNCE_MS, TimeUnit.MILLISECONDS)
    }

    /**
     * Persists debounced log/history entries now. No-op when nothing is
     * volatile; a refused write (key store locked) stays dirty, so a later
     * flush — after unlock, on a passphrase change, at shutdown — can still
     * persist it. Called by the debounce scheduler, the shutdown hook, and
     * before the accounts are evicted on lock ([AmberDesktop.evictAllAccounts]).
     */
    fun flushVolatile() = synchronized(this) {
        if (historyDirty && runCatching { writeSecure(historyFile, history.value, cipherContext(historyFile)) }.getOrDefault(false)) historyDirty = false
        if (logsDirty && runCatching { writeSecure(logsFile, logs.value, cipherContext(logsFile)) }.getOrDefault(false)) logsDirty = false
        flushQueued = false
    }

    @Synchronized
    fun clearLogs() {
        logs.value = emptyList()
        writeSecure(logsFile, logs.value, cipherContext(logsFile))
        logsDirty = false
    }

    /**
     * Rewrites all files in the current encryption state. Called when the
     * passphrase lock is enabled (plaintext → encrypted) or removed
     * (encrypted → plaintext) so at-rest data matches immediately rather than
     * only on the next natural write.
     */
    @Synchronized
    fun rewriteAll() {
        writeSecure(appsFile, apps.value, cipherContext(appsFile), rollbackProtected = true)
        writeSecure(historyFile, history.value, cipherContext(historyFile))
        writeSecure(logsFile, logs.value, cipherContext(logsFile))
        historyDirty = false
        logsDirty = false
    }

    fun deleteAllFiles() {
        liveStores.remove(this)
        dir.deleteRecursively()
        StoreGenerations.forget(cipherContext(appsFile))
    }

    companion object {
        private const val MAX_HISTORY = 1000
        private const val MAX_LOGS = 1000
        private const val FLUSH_DEBOUNCE_MS = 500L

        private val flushScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "amber-store-flush").apply { isDaemon = true }
        }

        private val liveStores: MutableSet<AccountStore> = Collections.newSetFromMap(ConcurrentHashMap())

        init {
            runCatching {
                Runtime.getRuntime().addShutdownHook(Thread({ drainVolatile() }, "amber-store-flush"))
            }
        }

        private fun drainVolatile() {
            liveStores.forEach { runCatching { it.flushVolatile() } }
        }
    }
}

/** Global (account-independent) settings persisted as plain JSON. */
object SettingsStore {
    private val file = File(AppDirs.dataDir, "settings.json")
    val settings = MutableStateFlow(readJson<DesktopSettings>(file) ?: DesktopSettings())

    @Synchronized
    fun update(transform: (DesktopSettings) -> DesktopSettings) {
        settings.value = transform(settings.value)
        writeJson(file, settings.value)
    }
}

/**
 * Account list persistence. Only the private key (and optional seed words)
 * are sensitive; they are encrypted with the keystore-held AES key before
 * touching disk (see [DesktopKeyStore]).
 */
object AccountsStore {
    private val file = File(AppDirs.dataDir, "accounts.json")

    private val backup = File(AppDirs.dataDir, "accounts.json.bak")
    val accounts = MutableStateFlow(readJson<List<AccountRecord>>(file, backup) ?: emptyList())

    @Synchronized
    internal fun reload() {
        accounts.value = readJson<List<AccountRecord>>(file, backup) ?: emptyList()
    }

    @Synchronized
    fun upsert(record: AccountRecord) {
        accounts.value = accounts.value.filter { it.npub != record.npub } + record
        writeJson(file, accounts.value, backup)
    }

    @Synchronized
    fun delete(npub: String) {
        accounts.value = accounts.value.filter { it.npub != npub }
        writeJson(file, accounts.value)
        dropDeletedKeysFromBackup()
    }

    private fun dropDeletedKeysFromBackup() {
        AppDirs.writeAtomically(backup, file.readBytes())
    }

    fun get(npub: String): AccountRecord? = accounts.value.firstOrNull { it.npub == npub }

    /** Atomically rewrites one account's record; no-op if it was removed meanwhile. */
    @Synchronized
    fun update(npub: String, transform: (AccountRecord) -> AccountRecord) {
        val current = get(npub) ?: return
        upsert(transform(current))
    }
}
