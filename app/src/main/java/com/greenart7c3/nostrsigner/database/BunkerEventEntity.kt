package com.greenart7c3.nostrsigner.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bunker_event",
    indices = [
        Index(
            value = ["eventId"],
            name = "index_bunker_event_id",
            unique = true,
        ),
        Index(
            value = ["time"],
            name = "index_bunker_event_time",
        ),
    ],
)
data class BunkerEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val eventId: String,
    val time: Long,
)
