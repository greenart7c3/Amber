package com.greenart7c3.nostrsigner.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.greenart7c3.nostrsigner.SecureCryptoHelper

/**
 * Room DAO for the per-account `application` table.
 *
 * **At-rest envelope encryption** (GHSA-5fjp-ghh8-wch8, CWE-312): the two
 * NIP-46 secret columns [ApplicationEntity.secret] and
 * [ApplicationEntity.localKey] are persisted as Keystore-encrypted
 * (`SecureCryptoHelper.encryptBlocking`) Base64 ciphertext. The non-`Raw`
 * methods below are interface default methods that transparently encrypt on
 * write and decrypt on read via the [ApplicationEntity.encryptForStorage] /
 * [ApplicationEntity.decryptFromStorage] mappers, so every consumer of
 * `ApplicationEntity` / `ApplicationWithPermissions` continues to see the
 * plaintext values it already expects. The only at-rest reader is Room itself.
 *
 * The `*Raw` methods are Room-generated `@Query` / `@Insert` implementations
 * operating on the raw (encrypted) column values. Callers must use the public
 * default-method wrappers; the `Raw` methods are the persistence boundary.
 *
 * Sentinel: an empty `secret` or `localKey` is stored as `""` (not encrypted),
 * matching `LocalPreferences.kt:661-681`'s WebDAV password idiom and preserving
 * `WHERE localKey != ''` enumeration semantics in
 * `NotificationSubscription.kt:90-96`.
 *
 * `getBySecret` cannot use a SQL `WHERE secret = :secret` predicate because
 * GCM uses a random IV — the same plaintext produces a distinct ciphertext
 * each call. The lookup is carried out in Kotlin by decrypting all rows of the
 * (small, per-account) `application` table and filtering by the plaintext
 * secret. Caller call-sites: `BunkerSingleEventHomeScreen.kt:83`,
 * `BunkerConnectRequestScreen.kt:125`.
 */
@Dao
interface ApplicationDao {
    @Query("SELECT signPolicy FROM application WHERE `key` = :key")
    fun getSignPolicy(key: String): Int?

    @Query("SELECT * FROM application where pubKey = :pubKey order by name")
    suspend fun getAllRaw(pubKey: String): List<ApplicationEntity>

    suspend fun getAll(pubKey: String): List<ApplicationEntity> = getAllRaw(pubKey).map { it.decryptFromStorage() }

    @Query("SELECT * FROM application where isConnected = 0")
    @Transaction
    suspend fun getAllNotConnectedRaw(): List<ApplicationWithPermissions>

    suspend fun getAllNotConnected(): List<ApplicationWithPermissions> = getAllNotConnectedRaw().map { it.decryptFromStorage() }

    @Query("SELECT DISTINCT relays FROM application")
    fun getAllRelayLists(): List<RelayListWrapper>

    /**
     * Paged Applications-list projection. Selects only the rendered columns
     * ([ApplicationListItem]) and deliberately NOT the envelope-encrypted
     * `secret`/`localKey` columns: each Keystore decrypt is a binder + TEE
     * round trip, and the list screen renders neither field, so a `SELECT *`
     * here made the screen pay 2 × rows cipher operations per page (Paging's
     * initial load is 3 × pageSize rows) for values it never reads.
     */
    @Query("SELECT `key`, name, relays, icon, lastUsed FROM application WHERE pubKey = :pubKey ORDER BY lastUsed DESC")
    fun getApplicationListItemsPaging(pubKey: String): PagingSource<Int, ApplicationListItem>

    @Query("SELECT name FROM application WHERE `key` = :key LIMIT 1")
    suspend fun getAppName(key: String): String?

    @Query("SELECT * FROM application WHERE `key` = :key")
    @Transaction
    suspend fun getByKeyRaw(key: String): ApplicationWithPermissions?

    suspend fun getByKey(key: String): ApplicationWithPermissions? = getByKeyRaw(key)?.decryptFromStorage()

    @Query("SELECT * FROM application WHERE `key` = :key")
    @Transaction
    fun getByKeySyncRaw(key: String): ApplicationWithPermissions?

    fun getByKeySync(key: String): ApplicationWithPermissions? = getByKeySyncRaw(key)?.decryptFromStorage()

    @Query("SELECT * FROM application WHERE `name` = :name LIMIT 1")
    @Transaction
    suspend fun getByNameRaw(name: String): ApplicationWithPermissions?

    suspend fun getByName(name: String): ApplicationWithPermissions? = getByNameRaw(name)?.decryptFromStorage()

    @Query("SELECT * FROM application")
    suspend fun getAllApplicationsRaw(): List<ApplicationEntity>

    /**
     * Lookup by NIP-46 bunker connection `secret`. Cannot use SQL
     * `WHERE secret = :secret` after envelope encryption (random GCM IV);
     * decrypt all rows of the per-account table and filter in Kotlin.
     * Tables are bounded by NIP-46 connection count (typically < 100).
     */
    suspend fun getBySecret(secret: String): ApplicationWithPermissions? {
        val matchKey = getAllApplicationsRaw().firstOrNull { row ->
            if (row.secret.isEmpty()) {
                secret.isEmpty()
            } else {
                runCatching { SecureCryptoHelper.decryptBlocking(row.secret) }.getOrDefault(null) == secret
            }
        }?.key ?: return null
        // Eager-load the related permissions via the wrapped getByKey so callers
        // receive the same shape the original @Transaction/SELECT joined query did.
        return getByKey(matchKey)
    }

    @Query("SELECT * FROM application WHERE pubKey = :pubKey AND localKey != ''")
    suspend fun getAllWithLocalKeyRaw(pubKey: String): List<ApplicationEntity>

    suspend fun getAllWithLocalKey(pubKey: String): List<ApplicationEntity> = getAllWithLocalKeyRaw(pubKey).map { it.decryptFromStorage() }

    @Query("UPDATE applicationPermission set acceptUntil = 0, rejectUntil = 0, rememberType = 0 where (acceptUntil < :time OR rejectUntil < :time) AND rememberType <> 4")
    fun updateExpiredPermissions(time: Long)

    @Query("SELECT * FROM applicationPermission WHERE pkKey = :key")
    suspend fun getAllByKey(key: String): List<ApplicationPermissionsEntity>

    @Query("SELECT `key`, `name` FROM application WHERE name <> ''")
    fun getApplicationKeyNames(): List<ApplicationKeyName>

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

    @Query("SELECT * FROM applicationPermission WHERE pkKey = :key AND type = :type")
    fun getPermission(
        key: String,
        type: String,
    ): ApplicationPermissionsEntity?

    /**
     * Match an "all kinds" grant — the row whose `kind` column is `NULL`.
     * Distinct from [getPermission] (no kind), which matches any row of the
     * given type regardless of kind: V3's kind-scoped permission model needs
     * the explicit `IS NULL` to avoid kind-A rejects leaking to kind-B requests.
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertApplicationRaw(event: ApplicationEntity): Long?

    suspend fun insertApplication(event: ApplicationEntity): Long? = insertApplicationRaw(event.encryptForStorage())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllRaw(events: List<ApplicationEntity>): List<Long>?

    fun insertAll(events: List<ApplicationEntity>): List<Long>? = insertAllRaw(events.map { it.encryptForStorage() })

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @Transaction
    suspend fun insertPermissions2Raw(permissions: List<ApplicationPermissionsEntity>): List<Long>?

    suspend fun insertPermissions2(permissions: List<ApplicationPermissionsEntity>): List<Long>? = insertPermissions2Raw(permissions)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertPermissionsRaw(permissions: List<ApplicationPermissionsEntity>): List<Long>?

    suspend fun insertPermissions(permissions: List<ApplicationPermissionsEntity>): List<Long>? {
        permissions.forEach {
            if (it.kind != null) {
                if (it.relay.isNotEmpty()) {
                    // For relay-specific permissions (kind 22242): wildcard "*" clears all relay entries,
                    // specific relay only clears its own entry
                    if (it.relay == "*") {
                        deletePermissionsForKind(it.pkKey, it.type, it.kind)
                    } else {
                        deletePermissions(it.pkKey, it.type, it.kind, it.relay)
                    }
                } else {
                    deletePermissions(it.pkKey, it.type, it.kind)
                }
            } else {
                deletePermissions(it.pkKey, it.type)
            }
        }
        return insertPermissions2Raw(permissions)
    }

    @Transaction
    suspend fun insertApplicationWithPermissions(application: ApplicationWithPermissions) {
        deletePermissions(application.application.key)
        insertApplicationRaw(application.application.encryptForStorage())?.let {
            application.permissions.forEach {
                it.pkKey = application.application.key
            }

            insertPermissions(application.permissions)
        }
    }

    @Delete
    @Transaction
    suspend fun deleteRaw(entity: ApplicationEntity)

    suspend fun delete(entity: ApplicationEntity) = deleteRaw(entity)

    @Query("DELETE FROM application WHERE `key` = :key")
    @Transaction
    suspend fun delete(key: String)

    @Query("UPDATE application SET lastUsed = :time where `key` = :key")
    @Transaction
    suspend fun updateLastUsed(key: String, time: Long)

    @Query("UPDATE application SET name = :name, icon = :icon WHERE `key` = :key")
    @Transaction
    suspend fun updateNameAndIcon(key: String, name: String, icon: String)

    /**
     * Rewrites the two envelope-encrypted columns for one row. Key-rotation
     * write path ([SecureCryptoHelper.rotateKey]): the values must already be
     * ciphertext encrypted with the TARGET Keystore key — or the unchanged raw
     * value when the old ciphertext could not be decrypted, so rotation never
     * destroys data it cannot recover. Not wrapped by a public default method
     * because callers deal exclusively in ciphertext (empty-sentinel included).
     */
    @Query("UPDATE application SET secret = :secret, localKey = :localKey WHERE `key` = :key")
    @Transaction
    suspend fun updateEncryptedColumnsRaw(key: String, secret: String, localKey: String)

    @Delete
    @Transaction
    suspend fun deletePermission(permission: ApplicationPermissionsEntity)

    @Query("DELETE FROM application WHERE deleteAfter < :time AND deleteAfter > 0")
    @Transaction
    suspend fun deleteOldApplications(time: Long): Int

    @Query("DELETE FROM applicationPermission WHERE pkKey = :key")
    @Transaction
    suspend fun deletePermissions(key: String)

    @Query("DELETE FROM applicationPermission WHERE pkKey = :key AND type = :type")
    @Transaction
    suspend fun deletePermissions(
        key: String,
        type: String,
    )

    @Query("DELETE FROM applicationPermission WHERE pkKey = :key AND type = :type AND kind = :kind AND relay = ''")
    @Transaction
    suspend fun deletePermissions(
        key: String,
        type: String,
        kind: Int,
    )

    @Query("DELETE FROM applicationPermission WHERE pkKey = :key AND type = :type AND kind = :kind AND relay = :relay")
    @Transaction
    suspend fun deletePermissions(
        key: String,
        type: String,
        kind: Int,
        relay: String,
    )

    @Query("DELETE FROM applicationPermission WHERE pkKey = :key AND type = :type AND kind = :kind")
    @Transaction
    suspend fun deletePermissionsForKind(
        key: String,
        type: String,
        kind: Int,
    )
}
