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

/** Header on a file whose JSON payload is AES-GCM encrypted with the master key. */
private const val ENC_MARKER = "AMBERENC1:"

private fun atomicWrite(file: File, payload: String) {
    val tmp = File(file.parentFile, "${file.name}.tmp")
    tmp.writeText(payload)
    if (!tmp.renameTo(file)) {
        // Windows can refuse an atomic replace; fall back to copy + delete.
        file.writeText(payload)
        tmp.delete()
    }
    AppDirs.restrictToOwner(file)
}

private inline fun <reified T> readJson(file: File): T? = try {
    if (file.exists()) mapper.readValue<T>(file.readText()) else null
} catch (e: Exception) {
    AmberLogger.e("Storage", "Failed to read ${file.name}", e)
    null
}

private fun writeJson(file: File, value: Any) {
    atomicWrite(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value))
}

/**
 * Reads a JSON file that may be encrypted. Encrypted files carry the
 * [ENC_MARKER] header (written while a passphrase is set); plaintext files
 * are read as-is, so installs that predate the passphrase migrate
 * transparently.
 */
private inline fun <reified T> readSecure(file: File): T? = try {
    if (!file.exists()) {
        null
    } else {
        val raw = file.readText()
        val json = if (raw.startsWith(ENC_MARKER)) DesktopKeyStore.decryptString(raw.substring(ENC_MARKER.length)) else raw
        mapper.readValue<T>(json)
    }
} catch (e: Exception) {
    AmberLogger.e("Storage", "Failed to read ${file.name}", e)
    null
}

/**
 * Writes a JSON file, encrypting the payload with the master key whenever a
 * passphrase lock is set. Refuses to write while locked rather than clobber
 * ciphertext with an empty document. Returns whether the file was written;
 * callers keeping dirty state use the result to retry later.
 */
private fun writeSecure(file: File, value: Any): Boolean {
    val encrypt = PassphraseLock.isEnabled()
    if (encrypt && !DesktopKeyStore.isMasterKeyAvailable()) {
        AmberLogger.e("Storage", "Refusing to write ${file.name} while the key store is locked")
        return false
    }
    val json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)
    atomicWrite(file, if (encrypt) ENC_MARKER + DesktopKeyStore.encryptString(json) else json)
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
    val apps = MutableStateFlow(readSecure<List<AppWithPermissions>>(appsFile) ?: emptyList())
    val history = MutableStateFlow(readSecure<List<HistoryRecord>>(historyFile) ?: emptyList())
    val logs = MutableStateFlow(readSecure<List<LogRecord>>(logsFile) ?: emptyList())

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

    fun getByKey(key: String): AppWithPermissions? = apps.value.firstOrNull { it.app.key == key }

    fun getBySecret(secret: String): AppWithPermissions? = apps.value.firstOrNull { constantTimeEquals(it.app.secret, secret) && it.app.useSecret }

    fun getPermission(key: String, type: String, kind: Int? = null): AppPermissionRecord? = getByKey(key)?.permissions?.firstOrNull {
        it.type == type && it.kind == kind
    }

    @Synchronized
    fun upsert(app: AppWithPermissions) {
        apps.value = apps.value.filter { it.app.key != app.app.key } + app
        writeSecure(appsFile, apps.value)
    }

    /**
     * Removes connections whose "delete after" time has passed, mirroring the
     * Android `deleteOldApplications`. Returns how many were removed.
     */
    @Synchronized
    fun deleteExpiredApps(now: Long = TimeUtils.now()): Int {
        val expired = apps.value.filter { it.app.deleteAfter in 1 until now }.map { it.app.key }.toSet()
        if (expired.isEmpty()) return 0
        apps.value = apps.value.filter { it.app.key !in expired }
        history.value = history.value.filter { it.appKey !in expired }
        writeSecure(appsFile, apps.value)
        writeSecure(historyFile, history.value)
        // The history rewrite includes anything still volatile.
        historyDirty = false
        return expired.size
    }

    @Synchronized
    fun delete(key: String) {
        apps.value = apps.value.filter { it.app.key != key }
        history.value = history.value.filter { it.appKey != key }
        writeSecure(appsFile, apps.value)
        writeSecure(historyFile, history.value)
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
        if (historyDirty && runCatching { writeSecure(historyFile, history.value) }.getOrDefault(false)) historyDirty = false
        if (logsDirty && runCatching { writeSecure(logsFile, logs.value) }.getOrDefault(false)) logsDirty = false
        flushQueued = false
    }

    @Synchronized
    fun clearLogs() {
        logs.value = emptyList()
        writeSecure(logsFile, logs.value)
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
        writeSecure(appsFile, apps.value)
        writeSecure(historyFile, history.value)
        writeSecure(logsFile, logs.value)
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
    val accounts = MutableStateFlow(readJson<List<AccountRecord>>(file) ?: emptyList())

    @Synchronized
    fun upsert(record: AccountRecord) {
        accounts.value = accounts.value.filter { it.npub != record.npub } + record
        writeJson(file, accounts.value)
    }

    @Synchronized
    fun delete(npub: String) {
        accounts.value = accounts.value.filter { it.npub != npub }
        writeJson(file, accounts.value)
    }

    fun get(npub: String): AccountRecord? = accounts.value.firstOrNull { it.npub == npub }

    /** Atomically rewrites one account's record; no-op if it was removed meanwhile. */
    @Synchronized
    fun update(npub: String, transform: (AccountRecord) -> AccountRecord) {
        val current = get(npub) ?: return
        upsert(transform(current))
    }
}
