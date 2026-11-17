package com.greenart7c3.nostrsigner.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LogDao(private val database: LogDatabase) {
    companion object {
        const val COLLAPSE_WINDOW_MILLIS = 60 * 60 * 1000L
    }

    @Insert
    abstract fun insertAll(items: List<LogEntity>)

    @Insert
    @Transaction
    abstract suspend fun insertLog(logEntity: LogEntity)

    /**
     * Inserts a log entry unless privacy mode is on, in which case the write is
     * dropped. All call sites that record non-essential relay/bunker/request
     * logs should use this instead of [insertLog].
     */
    @Transaction
    open suspend fun insertLogIfEnabled(logEntity: LogEntity) {
        if (database.settingsStore.settings.privacyMode) return
        insertOrCollapse(logEntity)
    }

    /**
     * Relays repeat the same lifecycle events (Connecting, Connected, Sent...)
     * constantly. Instead of one row per event, an entry identical to one
     * logged within [COLLAPSE_WINDOW_MILLIS] bumps that row's [LogEntity.repeatCount]
     * and moves it to the latest time, so the noise doesn't crowd out the rest.
     */
    @Transaction
    open suspend fun insertOrCollapse(logEntity: LogEntity) {
        val existingId = findRecentDuplicate(
            url = logEntity.url,
            type = logEntity.type,
            message = logEntity.message,
            since = logEntity.time - COLLAPSE_WINDOW_MILLIS,
        )
        if (existingId != null) {
            incrementRepeat(existingId, logEntity.time)
        } else {
            insertLog(logEntity)
        }
    }

    @Query("SELECT id FROM amber_log WHERE url = :url AND type = :type AND message = :message AND time >= :since ORDER BY time DESC LIMIT 1")
    abstract suspend fun findRecentDuplicate(url: String, type: String, message: String, since: Long): Int?

    @Query("UPDATE amber_log SET repeatCount = repeatCount + 1, time = MAX(time, :time) WHERE id = :id")
    abstract suspend fun incrementRepeat(id: Int, time: Long)

    @Query("SELECT * FROM amber_log ORDER BY time DESC")
    abstract fun getLogs(): Flow<List<LogEntity>>

    @Query("SELECT * FROM amber_log ORDER BY time DESC")
    abstract fun getLogsPaging(): PagingSource<Int, LogEntity>

    @Query("SELECT * FROM amber_log where url = :url ORDER BY time DESC")
    abstract fun getLogsByUrlPaging(url: String): PagingSource<Int, LogEntity>

    @Query("DELETE FROM amber_log WHERE id IN (SELECT id FROM amber_log ORDER BY time ASC LIMIT :count)")
    abstract suspend fun deleteOldestLogs(count: Int): Int

    @Query("DELETE FROM amber_log")
    @Transaction
    abstract suspend fun clearLogs()

    @Query("DELETE FROM amber_log WHERE time < :time")
    @Transaction
    abstract suspend fun deleteOldLog(time: Long): Int
}
