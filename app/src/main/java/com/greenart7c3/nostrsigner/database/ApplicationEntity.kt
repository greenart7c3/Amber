package com.greenart7c3.nostrsigner.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter

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
    val name: String,
    val relays: List<String>,
    val url: String,
    val icon: String,
    val description: String,
    val pubKey: String,
    var isConnected: Boolean,
    val secret: String
)

data class ApplicationWithPermissions(
    @Embedded val application: ApplicationEntity,
    @Relation(
        parentColumn = "key",
        entityColumn = "pkKey"
    )
    val permissions: MutableList<ApplicationPermissionsEntity>
)

class Converters {
    @TypeConverter
    fun fromString(stringListString: String): List<String> {
        return stringListString.split(",").map { it }
    }

    @TypeConverter
    fun toString(stringList: List<String>): String {
        return stringList.joinToString(separator = ",")
    }
}
