package com.greenart7c3.nostrsigner.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history",
    foreignKeys = [
        ForeignKey(
            entity = ApplicationEntity::class,
            childColumns = ["pkKey"],
            parentColumns = ["key"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = ["pkKey"],
            name = "history_by_pk_key",
        ),
        Index(
            value = ["id"],
            name = "history_by_id",
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
