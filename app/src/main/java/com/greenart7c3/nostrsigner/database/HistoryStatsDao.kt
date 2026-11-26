package com.greenart7c3.nostrsigner.database

import androidx.room.Dao
import androidx.room.Query

/** Counts of accepted and rejected requests since a time, for the activity statistics. */
@Dao
interface HistoryStatsDao {
    @Query("SELECT COUNT(*) FROM history WHERE time >= :sinceSeconds AND accepted = 1")
    suspend fun countAcceptedSince(sinceSeconds: Long): Long

    @Query("SELECT COUNT(*) FROM history WHERE time >= :sinceSeconds AND accepted = 0")
    suspend fun countRejectedSince(sinceSeconds: Long): Long

    @Query("SELECT COUNT(*) FROM history WHERE pkKey = :pk AND time >= :sinceSeconds AND accepted = 1")
    suspend fun countAcceptedSince(pk: String, sinceSeconds: Long): Long

    @Query("SELECT COUNT(*) FROM history WHERE pkKey = :pk AND time >= :sinceSeconds AND accepted = 0")
    suspend fun countRejectedSince(pk: String, sinceSeconds: Long): Long
}
