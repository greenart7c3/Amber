package com.greenart7c3.nostrsigner.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/** Deletes logged entries: retention pruning, the size budget and clearing them all. */
@Dao
interface LogCleanupDao {
    @Query("DELETE FROM amber_log WHERE id IN (SELECT id FROM amber_log ORDER BY time ASC LIMIT :count)")
    suspend fun deleteOldestLogs(count: Int): Int

    @Query("DELETE FROM amber_log")
    @Transaction
    suspend fun clearLogs()

    @Query("DELETE FROM amber_log WHERE time < :time")
    @Transaction
    suspend fun deleteOldLog(time: Long): Int
}
