package com.greenart7c3.nostrsigner.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/** Room DAO that reads stored permissions: the grant lookups of the signing decision and an application's permission rows. */
@Dao
interface PermissionLookupDao {
    @Query("SELECT signPolicy FROM application WHERE `key` = :key")
    fun getSignPolicy(key: String): Int?

    @Query("SELECT deleteAfter FROM application WHERE `key` = :key LIMIT 1")
    fun getDeleteAfter(key: String): Long?

    @Query("SELECT * FROM applicationPermission WHERE pkKey = :key")
    suspend fun getAllByKey(key: String): List<ApplicationPermissionsEntity>

    @Query("SELECT * FROM applicationPermission WHERE acceptable = 0 AND rejectUntil = 0")
    @Transaction
    fun getAllRejectedPermissions(): List<ApplicationPermissionsEntity>

    @Query("SELECT * FROM applicationPermission WHERE acceptable = 1 AND acceptUntil = 0")
    @Transaction
    fun getAllAcceptedPermissions(): List<ApplicationPermissionsEntity>

    @Query("SELECT * FROM applicationPermission WHERE pkKey = :key AND type = :type AND kind = :kind AND relay = ''")
    fun getPermission(
        key: String,
        type: String,
        kind: Int?,
    ): ApplicationPermissionsEntity?

    /**
     * The kind-less, relay-less grant of [type] (GET_PUBLIC_KEY, PING,
     * SIGN_PSBT, the V2 encrypt/decrypt types). Kind- or relay-scoped rows of
     * the same type are a different grant and never stand in for it. The
     * unique index treats NULL kinds as distinct, so duplicates can exist:
     * an explicit rejection wins (fail-safe), then the newest row, instead of
     * whatever order SQLite happens to return.
     */
    @Query("SELECT * FROM applicationPermission WHERE pkKey = :key AND type = :type AND kind IS NULL AND relay = '' ORDER BY acceptable ASC, id DESC LIMIT 1")
    fun getPermission(
        key: String,
        type: String,
    ): ApplicationPermissionsEntity?

    /**
     * Match an "all kinds" grant — the row whose `kind` column is `NULL`.
     * V3's kind-scoped permission model needs the explicit `IS NULL` to avoid
     * kind-A rejects leaking to kind-B requests.
     */
    @Query("SELECT * FROM applicationPermission WHERE pkKey = :key AND type = :type AND kind IS NULL AND relay = '' LIMIT 1")
    fun getPermissionAllKinds(
        key: String,
        type: String,
    ): ApplicationPermissionsEntity?

    @Query("SELECT * FROM applicationPermission WHERE pkKey = :key AND type = :type AND kind = :kind AND relay = :relay LIMIT 1")
    fun getPermissionForRelay(
        key: String,
        type: String,
        kind: Int,
        relay: String,
    ): ApplicationPermissionsEntity?

    @Query("SELECT * FROM applicationPermission WHERE pkKey = :key AND type = :type AND kind = :kind AND (relay = '*' OR relay = '' OR relay IS NULL) LIMIT 1")
    fun getWildcardRelayPermission(
        key: String,
        type: String,
        kind: Int,
    ): ApplicationPermissionsEntity?
}
