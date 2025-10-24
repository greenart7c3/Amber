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
interface ApplicationDao {
    @Query("SELECT signPolicy FROM application WHERE `key` = :key")
    fun getSignPolicy(key: String): Int?

    @Query("SELECT * FROM application where pubKey = :pubKey order by name")
    suspend fun getAll(pubKey: String): List<ApplicationEntity>

    @Query("SELECT * FROM application where isConnected = 0")
    @Transaction
    suspend fun getAllNotConnected(): List<ApplicationWithPermissions>

    @Query("SELECT a.* FROM application a WHERE a.pubKey = :pubKey ORDER BY a.lastUsed DESC")
    fun getAllFlow(pubKey: String): Flow<List<ApplicationEntity>>

    @Query("SELECT * FROM application WHERE `key` = :key")
    @Transaction
    suspend fun getByKey(key: String): ApplicationWithPermissions?

    @Query("SELECT * FROM application WHERE `key` = :key")
    @Transaction
    fun getByKeySync(key: String): ApplicationWithPermissions?

    @Query("SELECT * FROM application WHERE `name` = :name LIMIT 1")
    @Transaction
    suspend fun getByName(name: String): ApplicationWithPermissions?

    @Query("SELECT * FROM application WHERE secret = :secret")
    @Transaction
    suspend fun getBySecret(secret: String): ApplicationWithPermissions?

    @Query("SELECT * FROM applicationPermission WHERE pkKey = :key and rememberType = 4")
    suspend fun getAllByKey(key: String): List<ApplicationPermissionsEntity>

    @Query("SELECT * FROM application")
    @Transaction
    fun getAllApplications(): List<ApplicationWithPermissions>

    @Query("SELECT * FROM applicationPermission WHERE acceptable = 0 AND rejectUntil = 0")
    @Transaction
    fun getAllRejectedPermissions(): List<ApplicationPermissionsEntity>

    @Query("SELECT * FROM applicationPermission WHERE acceptable = 1 AND acceptUntil = 0")
    @Transaction
    fun getAllAcceptedPermissions(): List<ApplicationPermissionsEntity>

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
    suspend fun innerAddHistory(entity: HistoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @Transaction
    suspend fun addHistory(entity: HistoryEntity) {
        try {
            innerAddHistory(entity)
            updateLastUsed(entity.pkKey, entity.time)
        } catch (e: Exception) {
            Log.e(Amber.TAG, "Error adding history", e)
        }
    }

    @Query("UPDATE application SET lastUsed = :time where `key` = :key")
    @Transaction
    suspend fun updateLastUsed(key: String, time: Long)

    @Query("SELECT * FROM history2 where pkKey = :pk ORDER BY time DESC")
    fun getAllHistory(pk: String): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history2 ORDER BY time DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history2 where pkKey = :pk")
    suspend fun deleteHistory(pk: String)

    @Delete
    @Transaction
    suspend fun deletePermission(permission: ApplicationPermissionsEntity)

    @Query("SELECT COUNT(*) FROM history2 WHERE time < :time")
    @Transaction
    suspend fun countOldHistory(time: Long): Long

    @Query("SELECT * FROM history2 WHERE time < :time LIMIT 100")
    @Transaction
    suspend fun getOldHistory(time: Long): List<HistoryEntity>

    @Delete
    @Transaction
    suspend fun deleteHistory(historyEntity: HistoryEntity)

    @Query("DELETE FROM application WHERE deleteAfter < :time AND deleteAfter > 0")
    @Transaction
    suspend fun deleteOldApplications(time: Long): Int
}
