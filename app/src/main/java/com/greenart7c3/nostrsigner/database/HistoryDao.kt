package com.greenart7c3.nostrsigner.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.localizedBlocking
import com.greenart7c3.nostrsigner.models.encryptDecryptHistoryTypeNames
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.concurrent.ConcurrentHashMap

private const val MAX_CONTENT_LENGTH = 500
private const val LAST_USED_THROTTLE_SECONDS = 60L

@Dao
abstract class HistoryDao(private val database: HistoryDatabase) {
    @Query("DELETE FROM history where pkKey = :pk")
    abstract suspend fun deleteHistory(pk: String)

    @Query("DELETE FROM history WHERE id IN (SELECT id FROM history ORDER BY time ASC LIMIT :count)")
    abstract suspend fun deleteOldestHistory(count: Int): Int

    @Query("DELETE FROM history WHERE time < :time")
    @Transaction
    abstract suspend fun deleteOldHistory(time: Long): Int

    @Query("DELETE FROM history")
    @Transaction
    abstract suspend fun clearHistory()

    @Delete
    @Transaction
    abstract suspend fun deleteHistory(historyEntity: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    abstract suspend fun innerAddHistory(entities: List<HistoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @Transaction
    open suspend fun addHistory(entities: List<HistoryEntity>, npub: String?) {
        if (database.settingsStore.settings.privacyMode) return
        try {
            val localEntities = entities.map { entity ->
                val permission = Permission(entity.type.lowercase(), entity.kind)
                entity.copy(
                    translatedPermission = permission.localizedBlocking(true),
                    content = contentToStore(entity),
                )
            }
            innerAddHistory(localEntities)
            npub?.let {
                val lastUsed = entities.maxByOrNull { it.time }?.time ?: TimeUtils.now()
                val pkKey = entities.firstOrNull()?.pkKey
                if (pkKey != null && shouldWriteLastUsed("$npub|$pkKey", lastUsed)) {
                    database.accountRepository.daos(npub).applicationWriteDao.updateLastUsed(pkKey, lastUsed)
                }
            }
        } catch (e: Exception) {
            AmberLog.e(Amber.TAG, "Error adding history", e)
        }
    }

    private fun contentToStore(entity: HistoryEntity): String {
        val isCiphertextKeptWholeForOnDemandDecryption = entity.type in encryptDecryptHistoryTypeNames
        return if (!isCiphertextKeptWholeForOnDemandDecryption && entity.content.length > MAX_CONTENT_LENGTH) entity.content.take(MAX_CONTENT_LENGTH) else entity.content
    }

    companion object {
        private val lastUsedWrittenAt = ConcurrentHashMap<String, Long>()

        internal fun shouldWriteLastUsed(cacheKey: String, nowSeconds: Long): Boolean {
            var shouldWrite = false
            lastUsedWrittenAt.compute(cacheKey) { _, previous ->
                if (previous == null || nowSeconds - previous >= LAST_USED_THROTTLE_SECONDS) {
                    shouldWrite = true
                    nowSeconds
                } else {
                    previous
                }
            }
            return shouldWrite
        }
    }
}
