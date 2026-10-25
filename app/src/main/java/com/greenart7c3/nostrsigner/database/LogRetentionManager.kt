package com.greenart7c3.nostrsigner.database

import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.LocalPreferences
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps the per-account log and history databases bounded.
 *
 * Rows are kept for the user's retention period ([LocalPreferences.getLogRetention] and
 * [LocalPreferences.getHistoryRetention]), but each database is also held
 * under a disk budget: an app signing thousands of events per second would otherwise fill the disk long
 * before the retention period expires. Pruning runs from [com.greenart7c3.nostrsigner.service.ClearLogsWorker]
 * and, throttled, whenever a table changes (see [watch]), so a burst is trimmed within
 * [MIN_PRUNE_INTERVAL_MILLIS] instead of at the next daily cleanup.
 */
object LogRetentionManager {
    const val LOG_DB_MAX_BYTES = 20L * 1024 * 1024
    const val HISTORY_DB_MAX_BYTES = 50L * 1024 * 1024
    private const val PRUNE_BATCH = 2000
    private const val MIN_PRUNE_INTERVAL_MILLIS = 60_000L
    private const val AUTO_VACUUM_NONE = 0L
    private const val AUTO_VACUUM_INCREMENTAL = 2L

    private val locks = ConcurrentHashMap<String, Mutex>()
    private val lastPruneTimes = ConcurrentHashMap<String, Long>()

    suspend fun prune(npub: String) {
        pruneLogs(npub, Amber.instance.getLogDatabase(npub))
        pruneHistory(npub, Amber.instance.getHistoryDatabase(npub))
    }

    /** Prunes [db] in the background after writes, at most once per [MIN_PRUNE_INTERVAL_MILLIS]. */
    fun watch(npub: String, db: LogDatabase) {
        db.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer("amber_log") {
                override fun onInvalidated(tables: Set<String>) {
                    launchThrottled(logKey(npub)) { pruneLogs(npub, db) }
                }
            },
        )
    }

    fun watch(npub: String, db: HistoryDatabase) {
        db.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer("history") {
                override fun onInvalidated(tables: Set<String>) {
                    launchThrottled(historyKey(npub)) { pruneHistory(npub, db) }
                }
            },
        )
    }

    private fun logKey(npub: String) = "log_$npub"

    private fun historyKey(npub: String) = "history_$npub"

    private fun launchThrottled(key: String, block: suspend () -> Unit) {
        val now = System.currentTimeMillis()
        var claimed = false
        lastPruneTimes.compute(key) { _, last ->
            if (last == null || now - last >= MIN_PRUNE_INTERVAL_MILLIS) {
                claimed = true
                now
            } else {
                last
            }
        }
        if (!claimed) return
        Amber.instance.applicationIOScope.launch {
            val lock = locks.computeIfAbsent(key) { Mutex() }
            if (!lock.tryLock()) return@launch
            try {
                block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AmberLog.e(Amber.TAG, "Error pruning $key", e)
            } finally {
                lock.unlock()
            }
        }
    }

    private suspend fun pruneLogs(npub: String, db: LogDatabase) {
        val key = logKey(npub)
        locks.computeIfAbsent(key) { Mutex() }.withLock {
            lastPruneTimes[key] = System.currentTimeMillis()
            val dao = db.dao()
            val expired = dao.deleteOldLog(System.currentTimeMillis() - LocalPreferences.getLogRetention(Amber.instance).millis)
            val trimmed = trimToBudget(LOG_DB_MAX_BYTES, { liveBytes(db) }) { dao.deleteOldestLogs(PRUNE_BATCH) }
            if (expired > 0 || trimmed > 0) {
                AmberLog.d(Amber.TAG, "Pruned logs: $expired expired, $trimmed over the size budget")
            }
            reclaimSpace(db)
        }
    }

    private suspend fun pruneHistory(npub: String, db: HistoryDatabase) {
        val key = historyKey(npub)
        locks.computeIfAbsent(key) { Mutex() }.withLock {
            lastPruneTimes[key] = System.currentTimeMillis()
            val dao = db.dao()
            // History times are in seconds.
            val expired = dao.deleteOldHistory(TimeUtils.now() - LocalPreferences.getHistoryRetention(Amber.instance).millis / 1000)
            val trimmed = trimToBudget(HISTORY_DB_MAX_BYTES, { liveBytes(db) }) { dao.deleteOldestHistory(PRUNE_BATCH) }
            if (expired > 0 || trimmed > 0) {
                AmberLog.d(Amber.TAG, "Pruned history: $expired expired, $trimmed over the size budget")
            }
            reclaimSpace(db)
        }
    }

    /**
     * Deletes the oldest rows in batches until the database's live size is at most [maxBytes].
     * Returns the number of rows deleted.
     */
    internal suspend fun trimToBudget(
        maxBytes: Long,
        liveBytes: suspend () -> Long,
        deleteOldest: suspend () -> Int,
    ): Int {
        var total = 0
        while (liveBytes() > maxBytes) {
            val deleted = deleteOldest()
            if (deleted <= 0) break
            total += deleted
        }
        return total
    }

    // Pages in use; freed pages sit on the freelist until they are vacuumed.
    private suspend fun liveBytes(db: RoomDatabase): Long = withContext(Dispatchers.IO) {
        val sqlite = db.openHelper.writableDatabase
        (sqlite.longPragma("page_count") - sqlite.longPragma("freelist_count")) * sqlite.longPragma("page_size")
    }

    /**
     * Makes sure deleted rows give their space back to the filesystem. Android's SQLite
     * defaults to FULL auto-vacuum, which already truncates the file on every commit. A
     * database without auto-vacuum would never shrink, so it is switched to incremental
     * auto-vacuum once (a full VACUUM, which can't run inside a Room migration's transaction)
     * and its freed pages are released on every prune after that.
     */
    private suspend fun reclaimSpace(db: RoomDatabase) = withContext(Dispatchers.IO) {
        val sqlite = db.openHelper.writableDatabase
        when (sqlite.longPragma("auto_vacuum")) {
            AUTO_VACUUM_NONE -> {
                sqlite.execSQL("PRAGMA auto_vacuum = INCREMENTAL")
                sqlite.execSQL("VACUUM")
            }
            AUTO_VACUUM_INCREMENTAL -> if (sqlite.longPragma("freelist_count") > 0) {
                sqlite.query("PRAGMA incremental_vacuum").use { cursor ->
                    while (cursor.moveToNext()) {
                        // Stepping through the rows is what frees the pages.
                    }
                }
            }
        }
    }

    private fun SupportSQLiteDatabase.longPragma(name: String): Long = query("PRAGMA $name").use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
    }
}
