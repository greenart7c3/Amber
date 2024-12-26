package com.greenart7c3.nostrsigner.database

import android.util.Log
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ApplicationDao {
    @Query("SELECT signPolicy FROM application WHERE `key` = :key")
    fun getSignPolicy(key: String): Int?

    @Query("SELECT MAX(time) FROM notification")
    fun getLatestNotification(): Long?

    @Query("SELECT * FROM notification WHERE eventId = :eventId")
    fun getNotification(eventId: String): NotificationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertNotification(notificationEntity: NotificationEntity): Long?

    @Query("SELECT * FROM application where pubKey = :pubKey order by name")
    fun getAll(pubKey: String): List<ApplicationEntity>

    @Query("SELECT * FROM application where isConnected = 0")
    fun getAllNotConnected(): List<ApplicationWithPermissions>

    @Query(
        """
    SELECT a.*, MAX(h.time) as latestTime
    FROM application a
    LEFT JOIN history h ON a.`key` = h.pkKey
    AND a.pubKey = :pubKey
    GROUP BY a.`key`, a.description, a.icon, a.isConnected, a.name, a.pubKey, a.secret, a.signPolicy, a.url
    ORDER BY latestTime DESC
    """,
    )
    fun getAllFlow(pubKey: String): Flow<List<ApplicationWithLatestHistory>>

    @Query("SELECT * FROM application WHERE `key` = :key")
    @Transaction
    fun getByKey(key: String): ApplicationWithPermissions?

    @Query("SELECT * FROM application WHERE secret = :secret")
    @Transaction
    fun getBySecret(secret: String): ApplicationWithPermissions?

    @Query("SELECT * FROM applicationPermission WHERE pkKey = :key")
    fun getAllByKey(key: String): List<ApplicationPermissionsEntity>

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
    fun insertApplication(event: ApplicationEntity): Long?

    @Query("UPDATE application SET name = :name WHERE `key` = :key")
    fun changeApplicationName(
        key: String,
        name: String,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    fun insertPermissions(permissions: List<ApplicationPermissionsEntity>): List<Long>? {
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
    fun insertPermissions2(permissions: List<ApplicationPermissionsEntity>): List<Long>?

    @Query("DELETE FROM applicationPermission WHERE pkKey = :key")
    @Transaction
    fun deletePermissions(key: String)

    @Query("DELETE FROM applicationPermission WHERE pkKey = :key AND type = :type")
    @Transaction
    fun deletePermissions(
        key: String,
        type: String,
    )

    @Query("DELETE FROM applicationPermission WHERE pkKey = :key AND type = :type and kind = :kind")
    @Transaction
    fun deletePermissions(
        key: String,
        type: String,
        kind: Int,
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @Transaction
    fun insertApplicationWithPermissions(application: ApplicationWithPermissions) {
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
    fun delete(entity: ApplicationEntity)

    @Query("DELETE FROM application WHERE `key` = :key")
    @Transaction
    fun delete(key: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun innerAddHistory(entity: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @Transaction
    fun addHistory(entity: HistoryEntity) {
        try {
            innerAddHistory(entity)
        } catch (e: Exception) {
            Log.e("ApplicationDao", "Error adding history", e)
        }
    }

    @Query("SELECT * FROM history where pkKey = :pk ORDER BY time DESC")
    fun getAllHistory(pk: String): Flow<List<HistoryEntity>>

    @Insert
    fun insertLog(logEntity: LogEntity)

    @Query("SELECT * FROM amber_log ORDER BY time DESC")
    fun getLogs(): Flow<List<LogEntity>>

    @Query("SELECT * FROM amber_log where url = :url ORDER BY time DESC")
    fun getLogsByUrl(url: String): Flow<List<LogEntity>>

    @Query("DELETE FROM amber_log")
    fun clearLogs()

    @Delete
    fun deletePermission(permission: ApplicationPermissionsEntity)
}
