package com.greenart7c3.nostrsigner.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.collections.joinToString

fun generateBunkerPrivKey(): String = Nip01Crypto.privKeyCreate().toHexKey()

fun localPubKeyFromPrivKey(privKeyHex: String): String = try {
    KeyPair(privKey = privKeyHex.hexToByteArray()).pubKey.toHexKey()
} catch (_: Exception) {
    ""
}

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
    val localKey: String = "",
) {
    val localPubKey: String get() = if (localKey.isNotEmpty()) localPubKeyFromPrivKey(localKey) else ""

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
            localKey = "",
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

data class RelayListWrapper(
    val relays: List<NormalizedRelayUrl>,
)

data class ApplicationKeyName(
    val key: String,
    val name: String,
)

/**
 * Row projection for the Applications list screen ([ApplicationDao.getApplicationListItemsPaging]):
 * exactly the columns that screen renders. Deliberately excludes the
 * envelope-encrypted `secret`/`localKey` columns — every Keystore cipher
 * operation is a binder + TEE round trip, so a `SELECT *` paged load made
 * the screen pay 2 × rows decrypts per page for fields it never displays.
 */
data class ApplicationListItem(
    val key: String,
    val name: String,
    val relays: List<NormalizedRelayUrl>,
    val icon: String,
    val lastUsed: Long,
)
