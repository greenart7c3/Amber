package com.greenart7c3.nostrsigner.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.Index.Order
import androidx.room.PrimaryKey

@Entity(
    tableName = "amber_log",
    indices = [
        Index(
            value = ["time"],
            name = "log_by_time_key",
            orders = [Order.DESC],
        ),
    ],
)
data class LogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val url: String,
    val type: String,
    val message: String,
    val time: Long,
)
