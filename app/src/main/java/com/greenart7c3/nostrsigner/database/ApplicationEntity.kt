package com.greenart7c3.nostrsigner.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "application",
    indices = [
        Index(
            value = ["key"],
            name = "index_key",
            unique = true
        ),
        Index(
            value = ["name"],
            name = "index_name",
            unique = false
        )
    ]
)
data class ApplicationEntity(
    @PrimaryKey(autoGenerate = false)
    val key: String,
    val name: String
)

data class ApplicationWithPermissions(
    @Embedded val application: ApplicationEntity,
    @Relation(
        parentColumn = "key",
        entityColumn = "pkKey"
    )
    val permissions: MutableList<ApplicationPermissionsEntity>
)
