package com.greenart7c3.nostrsigner.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Insert
    fun insertAll(items: List<LogEntity>)

    @Insert
    @Transaction
    suspend fun insertLog(logEntity: LogEntity)

    @Query("SELECT * FROM amber_log ORDER BY time DESC")
    fun getLogs(): Flow<List<LogEntity>>

    @Query("SELECT * FROM amber_log ORDER BY time DESC")
    fun getLogsPaging(): PagingSource<Int, LogEntity>

    @Query("SELECT * FROM amber_log where url = :url ORDER BY time DESC")
    fun getLogsByUrl(url: String): Flow<List<LogEntity>>

    @Query("SELECT * FROM amber_log where url = :url ORDER BY time DESC")
    fun getLogsByUrlPaging(url: String): PagingSource<Int, LogEntity>

    @Query("DELETE FROM amber_log")
    @Transaction
    suspend fun clearLogs()

    @Query("SELECT COUNT(*) FROM amber_log WHERE time < :time")
    @Transaction
    suspend fun countOldLog(time: Long): Long

    @Query("SELECT * FROM amber_log WHERE time < :time LIMIT 100")
    @Transaction
    suspend fun getOldLog(time: Long): List<LogEntity>

    @Delete
    @Transaction
    suspend fun deleteLog(logEntity: LogEntity)
}
