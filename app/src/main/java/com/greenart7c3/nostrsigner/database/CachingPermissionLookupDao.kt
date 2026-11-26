package com.greenart7c3.nostrsigner.database

import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * [PermissionLookupDao] that serves the signing decision's lookups from the shared
 * [ApplicationDaoCache]. F-13: a row past its user-chosen `deleteAfter` lifetime
 * is authorization-dead even before the periodic cleanup deletes the row, so
 * every cached lookup of such an application answers "no grant".
 */
class CachingPermissionLookupDao(
    private val delegate: PermissionLookupDao,
    private val cache: ApplicationDaoCache,
) : PermissionLookupDao {
    private fun isExpired(pkKey: String): Boolean {
        val deleteAfter = getDeleteAfter(pkKey) ?: return false
        return deleteAfter in 1..TimeUtils.now()
    }

    override fun getSignPolicy(key: String): Int? {
        if (isExpired(key)) return null
        return cache.signPolicy(key) { delegate.getSignPolicy(key) }
    }

    override fun getPermission(key: String, type: String, kind: Int?): ApplicationPermissionsEntity? {
        if (isExpired(key)) return null
        return cache.permission(key, type, kind) { delegate.getPermission(key, type, kind) }
    }

    override fun getPermission(key: String, type: String): ApplicationPermissionsEntity? {
        if (isExpired(key)) return null
        return cache.permission(key, type) { delegate.getPermission(key, type) }
    }

    override fun getPermissionAllKinds(key: String, type: String): ApplicationPermissionsEntity? {
        if (isExpired(key)) return null
        return cache.permissionAllKinds(key, type) { delegate.getPermissionAllKinds(key, type) }
    }

    override fun getPermissionForRelay(key: String, type: String, kind: Int, relay: String): ApplicationPermissionsEntity? {
        if (isExpired(key)) return null
        return cache.permissionForRelay(key, type, kind, relay) { delegate.getPermissionForRelay(key, type, kind, relay) }
    }

    override fun getWildcardRelayPermission(key: String, type: String, kind: Int): ApplicationPermissionsEntity? {
        if (isExpired(key)) return null
        return cache.wildcardRelayPermission(key, type, kind) { delegate.getWildcardRelayPermission(key, type, kind) }
    }

    override fun getDeleteAfter(key: String): Long? = cache.deleteAfter(key) { delegate.getDeleteAfter(key) }

    override suspend fun getAllByKey(key: String): List<ApplicationPermissionsEntity> = delegate.getAllByKey(key)

    override fun getAllRejectedPermissions(): List<ApplicationPermissionsEntity> = delegate.getAllRejectedPermissions()

    override fun getAllAcceptedPermissions(): List<ApplicationPermissionsEntity> = delegate.getAllAcceptedPermissions()
}
