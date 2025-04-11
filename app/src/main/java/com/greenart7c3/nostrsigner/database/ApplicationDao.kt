package com.greenart7c3.nostrsigner.database

import android.util.Log
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.Flow

@Dao
interface ApplicationDao {
    @Query("SELECT signPolicy FROM application WHERE `key` = :key")
    fun getSignPolicy(key: String): Int?

    @Query("SELECT MAX(time) FROM notification")
    fun getLatestNotification(): Long?

    @Query("SELECT * FROM notification WHERE eventId = :eventId")
    suspend fun getNotification(eventId: String): NotificationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertNotification(notificationEntity: NotificationEntity): Long? {
        deleteNotification(TimeUtils.oneDayAgo())
        return innerInsertNotification(notificationEntity)
    }

    @Query("DELETE FROM notification WHERE time <= :time")
    @Transaction
    suspend fun deleteNotification(time: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun innerInsertNotification(notificationEntity: NotificationEntity): Long?

    @Query("SELECT * FROM application where pubKey = :pubKey order by name")
    suspend fun getAll(pubKey: String): List<ApplicationEntity>

    @Query("SELECT * FROM application where isConnected = 0")
    @Transaction
    suspend fun getAllNotConnected(): List<ApplicationWithPermissions>

    @Query(
        """
    SELECT a.*, MAX(h.time) as latestTime
    FROM application a
    LEFT JOIN history2 h ON a.`key` = h.pkKey
    AND a.pubKey = :pubKey
    GROUP BY a.`key`, a.description, a.icon, a.isConnected, a.name, a.pubKey, a.secret, a.signPolicy, a.url
    ORDER BY latestTime DESC
    """,
    )
    fun getAllFlow(pubKey: String): Flow<List<ApplicationWithLatestHistory>>

    @Query("SELECT * FROM application WHERE `key` = :key")
    @Transaction
    suspend fun getByKey(key: String): ApplicationWithPermissions?

    @Query("SELECT * FROM application WHERE secret = :secret")
    @Transaction
    suspend fun getBySecret(secret: String): ApplicationWithPermissions?

    @Query("SELECT * FROM applicationPermission WHERE pkKey = :key")
    suspend fun getAllByKey(key: String): List<ApplicationPermissionsEntity>

    @Query("SELECT * FROM application")
    @Transaction
    fun getAllApplications(): List<ApplicationWithPermissions>

    @Query("SELECT * FROM applicationPermission WHERE pkKey = :key AND type = :type AND  kind = :kind")
    fun getPermission(
        key: String,
        type: String,
        kind: Int?,
    ): ApplicationPermissionsEntity?

    @Query("SELECT * FROM applicationPermission WHERE pkKey = :key AND type = :type")
    fun getPermission(
        key: String,
        type: String,
    ): ApplicationPermissionsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertApplication(event: ApplicationEntity): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertPermissions(permissions: List<ApplicationPermissionsEntity>): List<Long>? {
        permissions.forEach {
            if (it.kind != null) {
                deletePermissions(it.pkKey, it.type, it.kind)
            } else {
                deletePermissions(it.pkKey, it.type)
            }
        }
        return insertPermissions2(permissions)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @Transaction
    suspend fun insertPermissions2(permissions: List<ApplicationPermissionsEntity>): List<Long>?

    @Query("DELETE FROM applicationPermission WHERE pkKey = :key")
    @Transaction
    suspend fun deletePermissions(key: String)

    @Query("DELETE FROM applicationPermission WHERE pkKey = :key AND type = :type")
    @Transaction
    suspend fun deletePermissions(
        key: String,
        type: String,
    )

    @Query("DELETE FROM applicationPermission WHERE pkKey = :key AND type = :type and kind = :kind")
    @Transaction
    suspend fun deletePermissions(
        key: String,
        type: String,
        kind: Int,
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @Transaction
    suspend fun insertApplicationWithPermissions(application: ApplicationWithPermissions) {
        deletePermissions(application.application.key)
        insertApplication(application.application)?.let {
            application.permissions.forEach {
                it.pkKey = application.application.key
            }

            insertPermissions(application.permissions)
        }
    }

    @Delete
    @Transaction
    suspend fun delete(entity: ApplicationEntity)

    @Query("DELETE FROM application WHERE `key` = :key")
    @Transaction
    suspend fun delete(key: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun innerAddHistory(entity: HistoryEntity2)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @Transaction
    suspend fun addHistory(entity: HistoryEntity2) {
        try {
            innerAddHistory(entity)
        } catch (e: Exception) {
            Log.e("ApplicationDao", "Error adding history", e)
        }
    }

    @Query("SELECT * FROM history2 where pkKey = :pk ORDER BY time DESC")
    fun getAllHistory(pk: String): Flow<List<HistoryEntity2>>

    @Insert
    @Transaction
    suspend fun insertLog(logEntity: LogEntity)

    @Query("SELECT * FROM amber_log ORDER BY time DESC")
    fun getLogs(): Flow<List<LogEntity>>

    @Query("SELECT * FROM amber_log where url = :url ORDER BY time DESC")
    fun getLogsByUrl(url: String): Flow<List<LogEntity>>

    @Query("DELETE FROM amber_log")
    @Transaction
    suspend fun clearLogs()

    @Delete
    @Transaction
    suspend fun deletePermission(permission: ApplicationPermissionsEntity)

    @Query("SELECT COUNT(*) FROM history2 WHERE time < :time")
    @Transaction
    suspend fun countOldHistory(time: Long): Long

    @Query("SELECT * FROM history2 WHERE time < :time LIMIT 100")
    @Transaction
    suspend fun getOldHistory(time: Long): List<HistoryEntity2>

    @Delete
    @Transaction
    suspend fun deleteHistory(historyEntity: HistoryEntity2)

    @Query("SELECT COUNT(*) FROM notification WHERE time < :time")
    @Transaction
    suspend fun countOldNotification(time: Long): Long

    @Query("SELECT * FROM notification WHERE time <= :time ORDER BY TIME DESC LIMIT 100")
    @Transaction
    suspend fun getOldNotification(time: Long): List<NotificationEntity>

    @Delete
    @Transaction
    suspend fun deleteNotification(notificationEntity: NotificationEntity)

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
