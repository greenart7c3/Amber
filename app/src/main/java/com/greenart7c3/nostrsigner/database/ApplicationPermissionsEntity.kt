package com.greenart7c3.nostrsigner.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "applicationPermission",
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
            name = "permissions_by_pk_key",
        ),
        Index(
            value = ["pkKey", "type", "kind"],
            name = "permissions_unique",
            unique = true,
        ),
    ],
)
data class ApplicationPermissionsEntity(
    @PrimaryKey
    val id: Int?,
    var pkKey: String,
    val type: String,
    val kind: Int?,
    var acceptable: Boolean,
    val rememberType: Int,
    var acceptUntil: Long,
    var rejectUntil: Long,
)
