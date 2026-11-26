package com.greenart7c3.nostrsigner.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/** Room DAO for the lists of applications: the paged Applications screen, connections, relays and keys. */
@Dao
interface ApplicationListDao {
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

    /** Alphabetical variant for the Applications list sort toggle (AmberSettings.sortApplicationsAlphabetically). */
    @Query("SELECT `key`, name, relays, icon, lastUsed FROM application WHERE pubKey = :pubKey ORDER BY name COLLATE NOCASE ASC, lastUsed DESC")
    fun getApplicationListItemsPagingByName(pubKey: String): PagingSource<Int, ApplicationListItem>

    @Query("SELECT * FROM application WHERE pubKey = :pubKey AND localKey != ''")
    suspend fun getAllWithLocalKeyRaw(pubKey: String): List<ApplicationEntity>

    suspend fun getAllWithLocalKey(pubKey: String): List<ApplicationEntity> = getAllWithLocalKeyRaw(pubKey).map { it.decryptFromStorage() }

    /** Every connection's client key: the senders the local bunker relay never rate limits. */
    @Query("SELECT `key` FROM application")
    fun getAllKeys(): List<String>

    @Query("SELECT `key`, `name` FROM application WHERE name <> ''")
    fun getApplicationKeyNames(): List<ApplicationKeyName>
}
