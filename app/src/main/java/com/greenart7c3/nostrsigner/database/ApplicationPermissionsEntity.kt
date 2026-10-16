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
            value = ["pkKey", "type", "kind", "relay"],
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
    val relay: String = "",
) {
    /**
     * The remembered decision in effect at [now]: true to accept, false to
     * reject, null to ask. Only the timestamp on the side that [acceptable]
     * selects counts, so a stale value left on the other side (for example
     * from an edited backup) can never decide a request.
     */
    fun activeDecision(now: Long): Boolean? = when {
        !acceptable && rejectUntil > now -> false
        acceptable && acceptUntil > now -> true
        else -> null
    }
}
