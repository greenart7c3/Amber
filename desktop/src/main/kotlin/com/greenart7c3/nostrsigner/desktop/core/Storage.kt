package com.greenart7c3.nostrsigner.desktop.core

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow

private val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

private inline fun <reified T> readJson(file: File): T? = try {
    if (file.exists()) mapper.readValue<T>(file.readText()) else null
} catch (e: Exception) {
    AmberLogger.e("Storage", "Failed to read ${file.name}", e)
    null
}

private fun writeJson(file: File, value: Any) {
    val tmp = File(file.parentFile, "${file.name}.tmp")
    tmp.writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value))
    if (!tmp.renameTo(file)) {
        // Windows can refuse an atomic replace; fall back to copy + delete.
        file.writeText(tmp.readText())
        tmp.delete()
    }
    AppDirs.restrictToOwner(file)
}

object AmberLogger {
    fun d(tag: String, message: String) {
        if (System.getenv("AMBER_DEBUG") != null) println("D/$tag: $message")
    }

    fun e(tag: String, message: String, e: Throwable? = null) {
        System.err.println("E/$tag: $message")
        e?.printStackTrace()
    }
}

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

    val apps = MutableStateFlow(readJson<List<AppWithPermissions>>(appsFile) ?: emptyList())
    val history = MutableStateFlow(readJson<List<HistoryRecord>>(historyFile) ?: emptyList())
    val logs = MutableStateFlow(readJson<List<LogRecord>>(logsFile) ?: emptyList())

    fun getByKey(key: String): AppWithPermissions? = apps.value.firstOrNull { it.app.key == key }

    fun getBySecret(secret: String): AppWithPermissions? = apps.value.firstOrNull { it.app.secret == secret && it.app.useSecret }

    fun getPermission(key: String, type: String, kind: Int? = null): AppPermissionRecord? = getByKey(key)?.permissions?.firstOrNull {
        it.type == type && it.kind == kind
    }

    @Synchronized
    fun upsert(app: AppWithPermissions) {
        apps.value = apps.value.filter { it.app.key != app.app.key } + app
        writeJson(appsFile, apps.value)
    }

    @Synchronized
    fun delete(key: String) {
        apps.value = apps.value.filter { it.app.key != key }
        history.value = history.value.filter { it.appKey != key }
        writeJson(appsFile, apps.value)
        writeJson(historyFile, history.value)
    }

    @Synchronized
    fun addHistory(record: HistoryRecord) {
        history.value = (history.value + record).takeLast(MAX_HISTORY)
        writeJson(historyFile, history.value)
    }

    @Synchronized
    fun addLog(url: String, type: String, message: String) {
        AmberLogger.d("Amber", "$url: $message")
        logs.value = (logs.value + LogRecord(url, type, message, System.currentTimeMillis())).takeLast(MAX_LOGS)
        writeJson(logsFile, logs.value)
    }

    @Synchronized
    fun clearLogs() {
        logs.value = emptyList()
        writeJson(logsFile, logs.value)
    }

    fun deleteAllFiles() {
        dir.deleteRecursively()
    }

    companion object {
        private const val MAX_HISTORY = 1000
        private const val MAX_LOGS = 1000
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
}
