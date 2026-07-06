package com.greenart7c3.nostrsigner.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.greenart7c3.nostrsigner.Amber
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Insert
    @Transaction
    suspend fun insertLog(logEntity: LogEntity)

    /**
     * Inserts a log entry unless privacy mode is on, in which case the write is
     * dropped. All call sites that record non-essential relay/bunker/request
     * logs should use this instead of [insertLog].
     */
    @Transaction
    suspend fun insertLogIfEnabled(logEntity: LogEntity) {
        if (Amber.instance.settings.privacyMode) return
        insertLog(logEntity)
    }

    @Query("SELECT * FROM amber_log ORDER BY time DESC")
    fun getLogs(): Flow<List<LogEntity>>

    @Query("SELECT * FROM amber_log ORDER BY time DESC")
    fun getLogsPaging(): PagingSource<Int, LogEntity>

    @Query("SELECT * FROM amber_log where url = :url ORDER BY time DESC")
    fun getLogsByUrl(url: String): Flow<List<LogEntity>>

    @Query("SELECT * FROM amber_log where url = :url ORDER BY time DESC")
    fun getLogsByUrlPaging(url: String): PagingSource<Int, LogEntity>

    @Query("SELECT COUNT(*) FROM amber_log")
    suspend fun getCount(): Long

    @Query("DELETE FROM amber_log WHERE id NOT IN (SELECT id FROM amber_log ORDER BY time DESC LIMIT :keepCount)")
    @Transaction
    suspend fun deleteExcessLogs(keepCount: Int): Int

    @Query("DELETE FROM amber_log")
    @Transaction
    suspend fun clearLogs()

    @Query("DELETE FROM amber_log WHERE time < :time")
    @Transaction
    suspend fun deleteOldLog(time: Long): Int

    @Delete
    @Transaction
    suspend fun deleteLog(logEntity: LogEntity)
}
