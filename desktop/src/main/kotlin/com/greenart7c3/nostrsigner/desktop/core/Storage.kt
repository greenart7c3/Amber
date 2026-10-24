package com.greenart7c3.nostrsigner.desktop.core

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.utils.TimeUtils
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow

private val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

/** Header on a file whose JSON payload is AES-GCM encrypted with the master key, bound to its account and file. */
private const val ENC_MARKER = "AMBERENC2:"

private fun atomicWrite(file: File, payload: String) {
    AppDirs.writeAtomically(file, payload.toByteArray(Charsets.UTF_8))
}

/**
 * Reads a JSON file. A file that exists but cannot be parsed is moved aside
 * rather than treated as empty: otherwise the next write would replace it,
 * and for accounts.json that destroys the only copy of the encrypted keys.
 * When [backup] is given and parses, it is restored in the file's place.
 */
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

/** Writes a JSON file; with [backup], the previous version is kept there first. */
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
private inline fun <reified T> readSecure(file: File, context: String): T? {
    if (!file.exists()) return null
    return try {
        val raw = file.readText()
        val json = when {
            raw.startsWith(ENC_MARKER) -> {
                val payload = raw.substring(ENC_MARKER.length)
                if (!DesktopKeyStore.isBound(payload)) throw SecurityException("Unbound payload under the bound header")
                DesktopKeyStore.decryptString(payload, context)
            }
            PassphraseLock.isEnabled() -> throw SecurityException("Refusing an unbound ${file.name} while a passphrase lock is set")
            else -> raw
        }
        mapper.readValue<T>(json)
    } catch (e: DesktopKeyStore.LockedException) {
        AmberLogger.e("Storage", "Cannot read ${file.name} while the key store is locked")
        null
    } catch (e: Exception) {
        val moved = AppDirs.quarantine(file)
        AmberLogger.e("Storage", "Failed to read ${file.name}; kept it as ${moved?.name}", e)
        null
    }
}

/**
 * Writes a JSON file, encrypting the payload with the master key whenever a
 * passphrase lock is set. Refuses to write while locked rather than clobber
 * ciphertext with an empty document. Returns whether the file was written;
 * callers keeping dirty state use the result to retry later.
 */
private fun writeSecure(file: File, value: Any, context: String): Boolean {
    val encrypt = PassphraseLock.isEnabled()
    if (encrypt && !DesktopKeyStore.isMasterKeyAvailable()) {
        AmberLogger.e("Storage", "Refusing to write ${file.name} while the key store is locked")
        return false
    }
    val json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)
    atomicWrite(file, if (encrypt) ENC_MARKER + DesktopKeyStore.encryptString(json, context) else json)
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
internal fun constantTimeEquals(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

/**
 * Per-account persistence: connected applications with their permissions,
 * request history, and relay/bunker logs. The desktop app keeps everything
 * in memory as [MutableStateFlow]s (so Compose observes changes directly)
 * and writes JSON files under the account's data directory on every change.
 */
class AccountStore(val npub: String) {
    private val dir = AppDirs.accountDir(npub)
    private val appsFile = File(dir, "applications.json")
    private val historyFile = File(dir, "history.json")
    private val logsFile = File(dir, "logs.json")

    // The per-account database (apps + permissions, request history, relay
    // logs) is the sensitive metadata about who you sign for, so it is
    // encrypted at rest with the master key whenever a passphrase lock is set
    // (see writeSecure/readSecure).
    val apps = MutableStateFlow(readSecure<List<AppWithPermissions>>(appsFile, context(appsFile)) ?: emptyList())
    val history = MutableStateFlow(readSecure<List<HistoryRecord>>(historyFile, context(historyFile)) ?: emptyList())
    val logs = MutableStateFlow(readSecure<List<LogRecord>>(logsFile, context(logsFile)) ?: emptyList())

    // addLog/addHistory update memory at once but persist with a short
    // debounce (see [scheduleVolatileFlush]): a relay flood logs one line per
    // event, and rewriting the whole AES-GCM file per line turns that into a
    // file rewrite per event. All three flags are guarded by `this`.
    private var historyDirty = false
    private var logsDirty = false
    private var flushQueued = false

    init {
        liveStores.add(this)
    }

    // Binds each file's ciphertext to this account and file name.
    private fun context(file: File) = "$npub/${file.name}"

    fun getByKey(key: String): AppWithPermissions? = apps.value.firstOrNull { it.app.key == key }

    fun getBySecret(secret: String): AppWithPermissions? = apps.value.firstOrNull { constantTimeEquals(it.app.secret, secret) && it.app.useSecret }

    fun getPermission(key: String, type: String, kind: Int? = null): AppPermissionRecord? = getByKey(key)?.permissions?.firstOrNull {
        it.type == type && it.kind == kind
    }

    @Synchronized
    fun upsert(app: AppWithPermissions) {
        apps.value = apps.value.filter { it.app.key != app.app.key } + app
        writeSecure(appsFile, apps.value, context(appsFile))
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
        writeSecure(appsFile, apps.value, context(appsFile))
        writeSecure(historyFile, history.value, context(historyFile))
        // The history rewrite includes anything still volatile.
        historyDirty = false
        return expired.size
    }

    @Synchronized
    fun delete(key: String) {
        apps.value = apps.value.filter { it.app.key != key }
        history.value = history.value.filter { it.appKey != key }
        writeSecure(appsFile, apps.value, context(appsFile))
        writeSecure(historyFile, history.value, context(historyFile))
        // The history rewrite includes anything still volatile.
        historyDirty = false
    }

    @Synchronized
    fun addHistory(record: HistoryRecord) {
        history.value = (history.value + record).takeLast(MAX_HISTORY)
        historyDirty = true
        scheduleVolatileFlush()
    }

    @Synchronized
    fun addLog(url: String, type: String, message: String) {
        AmberLogger.d("Amber", "$url: $message")
        logs.value = (logs.value + LogRecord(url, type, message, System.currentTimeMillis())).takeLast(MAX_LOGS)
        logsDirty = true
        scheduleVolatileFlush()
    }

    // Only called from the @Synchronized writers, so no extra locking is
    // needed to check flushQueued.
    private fun scheduleVolatileFlush() {
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
        if (historyDirty && runCatching { writeSecure(historyFile, history.value, context(historyFile)) }.getOrDefault(false)) historyDirty = false
        if (logsDirty && runCatching { writeSecure(logsFile, logs.value, context(logsFile)) }.getOrDefault(false)) logsDirty = false
        flushQueued = false
    }

    @Synchronized
    fun clearLogs() {
        logs.value = emptyList()
        writeSecure(logsFile, logs.value, context(logsFile))
        // The empty-list write supersedes anything still volatile.
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
        writeSecure(appsFile, apps.value, context(appsFile))
        writeSecure(historyFile, history.value, context(historyFile))
        writeSecure(logsFile, logs.value, context(logsFile))
        historyDirty = false
        logsDirty = false
    }

    fun deleteAllFiles() {
        liveStores.remove(this)
        dir.deleteRecursively()
    }

    companion object {
        private const val MAX_HISTORY = 1000
        private const val MAX_LOGS = 1000
        private const val FLUSH_DEBOUNCE_MS = 500L

        // One daemon thread schedules every account's debounced writes, so a
        // relay flood costs at most a couple of file writes per second.
        private val flushScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "amber-store-flush").apply { isDaemon = true }
        }

        // Live stores, so the shutdown hook can persist volatile entries and
        // the lock path can flush before the key material is evicted.
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

    // The previous version of accounts.json, the only file holding the
    // encrypted keys; restored if the main file is ever unreadable.
    private val backup = File(AppDirs.dataDir, "accounts.json.bak")
    val accounts = MutableStateFlow(readJson<List<AccountRecord>>(file, backup) ?: emptyList())

    /** Re-reads accounts.json from disk (startup path; also used by tests). */
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
        // A deleted account's key must not linger in the backup.
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
