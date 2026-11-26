package com.greenart7c3.nostrsigner.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Room DAO for permission upkeep: expiring grants, pruning applications past their lifetime, and raw permission rows. */
@Dao
interface PermissionMaintenanceDao {
    /**
     * Resets (`acceptUntil = 0`, `rejectUntil = 0`, `rememberType = 0`) every permission row without an
     * active decision at [time], i.e. neither accepted with `acceptUntil > time` nor rejected with
     * `rejectUntil > time`. ALWAYS rows (`rememberType = 4`) are kept, and rows that are already reset
     * (all three columns 0) are skipped, so the returned count covers only rows that really changed.
     * [CachingPermissionMaintenanceDao] clears the permission cache only when that count is > 0.
     */
    @Query(
        "UPDATE applicationPermission SET acceptUntil = 0, rejectUntil = 0, rememberType = 0 " +
            "WHERE rememberType <> 4 " +
            "AND NOT (acceptable = 1 AND acceptUntil > :time) " +
            "AND NOT (acceptable = 0 AND rejectUntil > :time) " +
            "AND (acceptUntil <> 0 OR rejectUntil <> 0 OR rememberType <> 0)",
    )
    fun updateExpiredPermissions(time: Long): Int

    @Query("DELETE FROM application WHERE deleteAfter < :time AND deleteAfter > 0")
    @Transaction
    suspend fun deleteOldApplications(time: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertPermissionsRaw(permissions: List<ApplicationPermissionsEntity>): List<Long>?

    @Delete
    @Transaction
    suspend fun deletePermission(permission: ApplicationPermissionsEntity)
}
