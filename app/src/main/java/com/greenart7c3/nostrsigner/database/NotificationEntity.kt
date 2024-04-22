package com.greenart7c3.nostrsigner.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification"
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val eventId: String,
    val time: Long
)
