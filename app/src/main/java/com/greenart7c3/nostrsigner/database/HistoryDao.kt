package com.greenart7c3.nostrsigner.database

import android.util.Log
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.greenart7c3.nostrsigner.Amber
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history where pkKey = :pk ORDER BY time DESC")
    fun getAllHistory(pk: String): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY time DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history where pkKey = :pk")
    suspend fun deleteHistory(pk: String)

    @Query("SELECT COUNT(*) FROM history WHERE time < :time")
    @Transaction
    suspend fun countOldHistory(time: Long): Long

    @Query("SELECT * FROM history WHERE time < :time LIMIT 100")
    @Transaction
    suspend fun getOldHistory(time: Long): List<HistoryEntity>

    @Delete
    @Transaction
    suspend fun deleteHistory(historyEntity: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun innerAddHistory(entity: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @Transaction
    suspend fun addHistory(entity: HistoryEntity, npub: String?) {
        try {
            innerAddHistory(entity)
            npub?.let {
                Amber.instance.getDatabase(npub).dao().updateLastUsed(entity.pkKey, entity.time)
            }
        } catch (e: Exception) {
            Log.e(Amber.TAG, "Error adding history", e)
        }
    }
}
