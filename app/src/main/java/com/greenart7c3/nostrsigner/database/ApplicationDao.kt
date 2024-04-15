package com.greenart7c3.nostrsigner.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ApplicationDao {
    @Query("SELECT * FROM application where pubKey = :pubKey")
    fun getAll(pubKey: String): List<ApplicationEntity>

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
    fun getPermission(key: String, type: String, kind: Int?): ApplicationPermissionsEntity?

    @Query("SELECT * FROM applicationPermission WHERE pkKey = :key AND type = :type")
    fun getPermission(key: String, type: String): ApplicationPermissionsEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertApplication(event: ApplicationEntity): Long?

    @Query("UPDATE application SET name = :name WHERE `key` = :key")
    fun changeApplicationName(key: String, name: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
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
    fun deletePermissions(key: String, type: String)

    @Query("DELETE FROM applicationPermission WHERE pkKey = :key AND type = :type and kind = :kind")
    @Transaction
    fun deletePermissions(key: String, type: String, kind: Int)

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
    fun delete(entity: ApplicationEntity)
}
