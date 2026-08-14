package com.greenart7c3.nostrsigner.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface BunkerEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @Transaction
    suspend fun insert(bunkerEvent: BunkerEventEntity): Long?

    @Query("SELECT EXISTS(SELECT 1 FROM bunker_event WHERE eventId = :eventId)")
    suspend fun exists(eventId: String): Boolean

    @Query("DELETE FROM bunker_event WHERE time < :time")
    @Transaction
    suspend fun deleteOld(time: Long): Int
}
