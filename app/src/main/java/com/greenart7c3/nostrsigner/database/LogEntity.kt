package com.greenart7c3.nostrsigner.database

import androidx.room.ColumnInfo
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
        Index(
            value = ["url", "time"],
            name = "log_by_url_time",
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
    // Number of identical (url, type, message) entries merged into this row;
    // see [LogDao.insertOrCollapse]. [time] is the latest occurrence.
    @ColumnInfo(defaultValue = "1")
    val repeatCount: Int = 1,
)
