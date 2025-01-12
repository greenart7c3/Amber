package com.greenart7c3.nostrsigner.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification",
    indices = [
        Index(value = ["eventId"]),
        Index(value = ["time"]),
    ],
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val eventId: String,
    val time: Long,
)
