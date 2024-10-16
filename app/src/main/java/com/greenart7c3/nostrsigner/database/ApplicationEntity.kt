package com.greenart7c3.nostrsigner.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.RelaySetupInfo

@Entity(
    tableName = "application",
    indices = [
        Index(
            value = ["key"],
            name = "index_key",
            unique = true,
        ),
        Index(
            value = ["name"],
            name = "index_name",
            unique = false,
        ),
    ],
)
data class ApplicationEntity(
    @PrimaryKey(autoGenerate = false)
    val key: String,
    val name: String,
    val relays: List<RelaySetupInfo>,
    val url: String,
    val icon: String,
    val description: String,
    val pubKey: String,
    var isConnected: Boolean,
    val secret: String,
    val useSecret: Boolean,
    var signPolicy: Int,
) {
    companion object {
        fun empty() = ApplicationEntity(
            key = "",
            name = "",
            relays = emptyList(),
            url = "",
            icon = "",
            description = "",
            pubKey = "",
            isConnected = false,
            secret = "",
            useSecret = false,
            signPolicy = 0,
        )
    }
}

data class ApplicationWithPermissions(
    @Embedded val application: ApplicationEntity,
    @Relation(
        parentColumn = "key",
        entityColumn = "pkKey",
    )
    val permissions: MutableList<ApplicationPermissionsEntity>,
)

class Converters {
    @TypeConverter
    fun fromString(stringListString: String): List<RelaySetupInfo> {
        if (stringListString.isBlank()) {
            return emptyList()
        }
        return stringListString.split(",").map {
            RelaySetupInfo(it, read = true, write = true, feedTypes = COMMON_FEED_TYPES)
        }
    }

    @TypeConverter
    fun toString(relays: List<RelaySetupInfo>): String {
        return relays.joinToString(separator = ",") {
            it.url
        }
    }
}
