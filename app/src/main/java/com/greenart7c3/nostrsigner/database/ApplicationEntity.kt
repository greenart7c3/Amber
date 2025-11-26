package com.greenart7c3.nostrsigner.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.collections.joinToString

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
    val relays: List<NormalizedRelayUrl>,
    val url: String,
    val icon: String,
    val description: String,
    val pubKey: String,
    var isConnected: Boolean,
    val secret: String,
    val useSecret: Boolean,
    var signPolicy: Int,
    var closeApplication: Boolean,
    var deleteAfter: Long,
    val lastUsed: Long,
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
            closeApplication = true,
            deleteAfter = 0L,
            lastUsed = 0L,
        )
    }

    fun shouldShowRelays(): Boolean = (secret.isNotEmpty() || relays.isNotEmpty()) && !isConnected
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
    fun fromString(stringListString: String): List<NormalizedRelayUrl> {
        if (stringListString.isBlank()) {
            return emptyList()
        }
        return stringListString.split(",").mapNotNull {
            RelayUrlNormalizer.normalizeOrNull(it)
        }
    }

    @TypeConverter
    fun toString(relays: List<NormalizedRelayUrl>): String = relays.joinToString(separator = ",") {
        it.url
    }
}
