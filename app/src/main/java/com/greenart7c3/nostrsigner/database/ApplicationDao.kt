package com.greenart7c3.nostrsigner.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.greenart7c3.nostrsigner.SecureCryptoHelper

/**
 * Room DAO that reads one row of the per-account `application` table (by key,
 * name or secret) and the account's connections.
 *
 * **At-rest envelope encryption** (GHSA-5fjp-ghh8-wch8, CWE-312): the two
 * NIP-46 secret columns [ApplicationEntity.secret] and
 * [ApplicationEntity.localKey] are persisted as Keystore-encrypted
 * (`SecureCryptoHelper.encryptBlocking`) Base64 ciphertext. The non-`Raw`
 * methods of this DAO, [ApplicationListDao], [ApplicationWriteDao] and
 * [PermissionWriteDao] are interface default methods that transparently encrypt on
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
    @Query("SELECT * FROM application where pubKey = :pubKey order by name")
    suspend fun getAllRaw(pubKey: String): List<ApplicationEntity>

    suspend fun getAll(pubKey: String): List<ApplicationEntity> = getAllRaw(pubKey).map { it.decryptFromStorage() }

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
}

/**
 * Room DAO that saves an application's permissions, alone or together with the
 * application row, replacing the stored grants each new one supersedes.
 */
@Dao
interface PermissionWriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertApplicationRaw(event: ApplicationEntity): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @Transaction
    suspend fun insertPermissions2Raw(permissions: List<ApplicationPermissionsEntity>): List<Long>?

    @Query("SELECT `key` FROM application WHERE `key` IN (:keys)")
    suspend fun getExistingApplicationKeys(keys: List<String>): List<String>

    /**
     * Inserts permissions, skipping rows whose parent application no longer
     * exists. `OnConflictStrategy.IGNORE` does not cover foreign-key failures,
     * so a permission written after its application was deleted (e.g. a toggle
     * racing an app delete/reset) would otherwise crash with
     * `SQLITE_CONSTRAINT_FOREIGNKEY`.
     */
    @Transaction
    suspend fun insertPermissions2(permissions: List<ApplicationPermissionsEntity>): List<Long>? {
        if (permissions.isEmpty()) return emptyList()
        val existingKeys = getExistingApplicationKeys(permissions.map { it.pkKey }.distinct()).toSet()
        val valid = permissions.filter { it.pkKey in existingKeys }
        if (valid.isEmpty()) return emptyList()
        return insertPermissions2Raw(valid)
    }

    @Transaction
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
        return insertPermissions2(permissions)
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
