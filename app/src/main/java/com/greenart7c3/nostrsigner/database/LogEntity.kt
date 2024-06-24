package com.greenart7c3.nostrsigner.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "amber_log",
)
data class LogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val url: String,
    val type: String,
    val message: String,
    val time: Long,
)
