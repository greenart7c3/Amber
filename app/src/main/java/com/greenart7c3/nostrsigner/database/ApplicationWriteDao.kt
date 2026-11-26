package com.greenart7c3.nostrsigner.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Room DAO that writes and deletes `application` rows, including the key-rotation rewrite of their secrets. */
@Dao
interface ApplicationWriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun insertApplicationRaw(event: ApplicationEntity): Long?

    suspend fun insertApplication(event: ApplicationEntity): Long? = insertApplicationRaw(event.encryptForStorage())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllRaw(events: List<ApplicationEntity>): List<Long>?

    fun insertAll(events: List<ApplicationEntity>): List<Long>? = insertAllRaw(events.map { it.encryptForStorage() })

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
     * Key-rotation write path ([com.greenart7c3.nostrsigner.SecureCryptoHelper.rotateKey]): replaces the
     * envelope-encrypted `secret` column with [newValue] (ciphertext under the
     * new Keystore key) only if it still holds [expected], so the sweep never
     * clobbers a row written concurrently. Returns the number of rows changed.
     * Not wrapped by a public default method because callers deal exclusively
     * in ciphertext.
     */
    @Query("UPDATE application SET secret = :newValue WHERE `key` = :key AND secret = :expected")
    @Transaction
    suspend fun replaceSecretRaw(key: String, expected: String, newValue: String): Int

    /** `localKey` counterpart of [replaceSecretRaw]. */
    @Query("UPDATE application SET localKey = :newValue WHERE `key` = :key AND localKey = :expected")
    @Transaction
    suspend fun replaceLocalKeyRaw(key: String, expected: String, newValue: String): Int
}
