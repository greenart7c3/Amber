package com.greenart7c3.nostrsigner.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history2",
    indices = [
        Index(
            value = ["pkKey"],
            name = "history_by_pk_key2",
        ),
        Index(
            value = ["id"],
            name = "history_by_id2",
        ),
        Index(
            value = ["time"],
            name = "history_by_time",
        ),
        Index(
            value = ["pkKey", "time"],
            name = "history_by_key_and_time",
        ),
    ],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val pkKey: String,
    val type: String,
    val kind: Int?,
    val time: Long,
    val accepted: Boolean,
)
