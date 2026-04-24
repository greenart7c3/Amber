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
import com.vitorpamplona.quartz.utils.TimeUtils

private const val MAX_CONTENT_LENGTH = 500

@Dao
interface HistoryDao {
    @Query(
        """
    SELECT * FROM history
    WHERE pkKey = :pk
    AND (:acceptedFilter IS NULL OR accepted = :acceptedFilter)
    ORDER BY time DESC
    """,
    )
    fun getAllHistoryPaging(pk: String, acceptedFilter: Boolean?): PagingSource<Int, HistoryEntity>

    @Query(
        """
    SELECT * FROM history
    WHERE pkKey = :pk
    AND (kind = :query OR LOWER(type) LIKE '%' || :query || '%'
    OR LOWER(translatedPermission) LIKE '%' || :query || '%'
    OR LOWER(content) LIKE '%' || :query || '%')
    AND (:acceptedFilter IS NULL OR accepted = :acceptedFilter)
    ORDER BY time DESC
    """,
    )
    fun searchAllHistoryPaging(pk: String, query: String, acceptedFilter: Boolean?): PagingSource<Int, HistoryEntity>

    @Query(
        """
    SELECT * FROM history
    WHERE (:acceptedFilter IS NULL OR accepted = :acceptedFilter)
    ORDER BY time DESC
    """,
    )
    fun getAllHistoryPaging(acceptedFilter: Boolean?): PagingSource<Int, HistoryEntity>

    @Query(
        """
    SELECT * FROM history
    WHERE (kind = :query OR LOWER(type) LIKE '%' || :query || '%'
    OR LOWER(translatedPermission) LIKE '%' || :query || '%'
    OR LOWER(content) LIKE '%' || :query || '%')
    AND (:acceptedFilter IS NULL OR accepted = :acceptedFilter)
    ORDER BY time DESC
    """,
    )
    fun searchAllHistoryPaging(query: String, acceptedFilter: Boolean?): PagingSource<Int, HistoryEntity>

    @Query("DELETE FROM history where pkKey = :pk")
    suspend fun deleteHistory(pk: String)

    @Query("SELECT COUNT(*) FROM history WHERE time >= :sinceSeconds AND accepted = 1")
    suspend fun countAcceptedSince(sinceSeconds: Long): Long

    @Query("SELECT COUNT(*) FROM history WHERE time >= :sinceSeconds AND accepted = 0")
    suspend fun countRejectedSince(sinceSeconds: Long): Long

    @Query("SELECT COUNT(*) FROM history WHERE pkKey = :pk AND time >= :sinceSeconds AND accepted = 1")
    suspend fun countAcceptedSince(pk: String, sinceSeconds: Long): Long

    @Query("SELECT COUNT(*) FROM history WHERE pkKey = :pk AND time >= :sinceSeconds AND accepted = 0")
    suspend fun countRejectedSince(pk: String, sinceSeconds: Long): Long

    @Query("SELECT COUNT(*) FROM history")
    suspend fun getCount(): Long

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY time DESC LIMIT :keepCount)")
    @Transaction
    suspend fun deleteExcessHistory(keepCount: Int): Int

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
    suspend fun innerAddHistory(entities: List<HistoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @Transaction
    suspend fun addHistory(entities: List<HistoryEntity>, npub: String?) {
        try {
            val localEntities = entities.map { entity ->
                val permission = Permission(entity.type.toLowerCase(Locale.current), entity.kind)
                entity.copy(
                    translatedPermission = permission.toLocalizedString(Amber.instance, true),
                    content = if (entity.content.length > MAX_CONTENT_LENGTH) entity.content.take(MAX_CONTENT_LENGTH) else entity.content,
                )
            }
            innerAddHistory(localEntities)
            npub?.let {
                val lastUsed = entities.maxByOrNull { it.time }?.time ?: TimeUtils.now()
                val pkKey = entities.firstOrNull()?.pkKey
                if (pkKey != null) {
                    Amber.instance.getDatabase(npub).dao().updateLastUsed(pkKey, lastUsed)
                }
            }
        } catch (e: Exception) {
            Log.e(Amber.TAG, "Error adding history", e)
        }
    }
}
