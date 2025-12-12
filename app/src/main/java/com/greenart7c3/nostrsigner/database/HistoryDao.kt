package com.greenart7c3.nostrsigner.database

import android.util.Log
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.models.Permission

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history where pkKey = :pk ORDER BY time DESC")
    fun getAllHistoryPaging(pk: String): PagingSource<Int, HistoryEntity>

    @Query("SELECT * FROM history where (kind = :query OR LOWER(type) LIKE '%' || :query || '%' OR LOWER(translatedPermission) LIKE '%' || :query || '%') AND pkKey = :pk ORDER BY time DESC")
    fun searchAllHistoryPaging(pk: String, query: String): PagingSource<Int, HistoryEntity>

    @Query("SELECT * FROM history ORDER BY time DESC")
    fun getAllHistoryPaging(): PagingSource<Int, HistoryEntity>

    @Query(
        """
    SELECT * FROM history
    WHERE (kind = :query OR LOWER(type) LIKE '%' || :query || '%'
    OR LOWER(translatedPermission) LIKE '%' || :query || '%')
    ORDER BY time DESC
    """,
    )
    fun searchAllHistoryPaging(query: String): PagingSource<Int, HistoryEntity>

    @Query("DELETE FROM history where pkKey = :pk")
    suspend fun deleteHistory(pk: String)

    @Query("SELECT COUNT(*) FROM history WHERE time < :time")
    @Transaction
    suspend fun countOldHistory(time: Long): Long

    @Query("SELECT * FROM history WHERE time < :time LIMIT 100")
    @Transaction
    suspend fun getOldHistory(time: Long): List<HistoryEntity>

    @Query("DELETE FROM history WHERE time < :time")
    @Transaction
    suspend fun deleteOldHistory(time: Long): Int

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
            val permission = Permission(entity.type.toLowerCase(Locale.current), entity.kind)
            val localEntity = entity.copy(
                translatedPermission = permission.toLocalizedString(Amber.instance, true),
            )
            innerAddHistory(localEntity)
            npub?.let {
                Amber.instance.getDatabase(npub).dao().updateLastUsed(entity.pkKey, entity.time)
            }
        } catch (e: Exception) {
            Log.e(Amber.TAG, "Error adding history", e)
        }
    }
}
