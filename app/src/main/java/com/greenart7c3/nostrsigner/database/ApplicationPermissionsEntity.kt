package com.greenart7c3.nostrsigner.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.greenart7c3.nostrsigner.common.activeDecision

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
    fun activeDecision(now: Long): Boolean? = activeDecision(acceptable, acceptUntil, rejectUntil, now)
}

/**
 * Whether a kind-22242 grant's [relay] covers every relay. Matches
 * [ApplicationDao.getWildcardRelayPermission]: an explicit "*", or the blank
 * relay the basic sign policy stores, both mean all relays, so the approval
 * screens decide like the provider and NIP-46 paths.
 */
fun isAllRelaysGrant(relay: String?): Boolean = relay == "*" || relay.isNullOrEmpty()
