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
        val deleteAfter = delegate.getDeleteAfter(pkKey) ?: return false
        return deleteAfter > 0 && deleteAfter <= TimeUtils.now()
    }

    override fun getSignPolicy(key: String): Int? {
        val k = ApplicationDaoCache.Key(ApplicationDaoCache.Method.SIGN_POLICY, key, null, null, null)
        if (isExpired(key)) return null
        val readGeneration = cache.generation()
        cache.lookup(k)?.let { return it.signPolicy }
        val result = delegate.getSignPolicy(key)
        cache.store(k, ApplicationDaoCache.Value(result, null), readGeneration)
        return result
    }

    override fun getPermission(key: String, type: String, kind: Int?): ApplicationPermissionsEntity? {
        val k = ApplicationDaoCache.Key(ApplicationDaoCache.Method.PERM_KIND, key, type, kind, null)
        if (isExpired(key)) return null
        val readGeneration = cache.generation()
        cache.lookup(k)?.let { return it.permission }
        val result = delegate.getPermission(key, type, kind)
        cache.store(k, ApplicationDaoCache.Value(null, result), readGeneration)
        return result
    }

    override fun getPermission(key: String, type: String): ApplicationPermissionsEntity? {
        val k = ApplicationDaoCache.Key(ApplicationDaoCache.Method.PERM, key, type, null, null)
        if (isExpired(key)) return null
        val readGeneration = cache.generation()
        cache.lookup(k)?.let { return it.permission }
        val result = delegate.getPermission(key, type)
        cache.store(k, ApplicationDaoCache.Value(null, result), readGeneration)
        return result
    }

    override fun getPermissionAllKinds(key: String, type: String): ApplicationPermissionsEntity? {
        val k = ApplicationDaoCache.Key(ApplicationDaoCache.Method.PERM_ALL_KINDS, key, type, null, null)
        if (isExpired(key)) return null
        val readGeneration = cache.generation()
        cache.lookup(k)?.let { return it.permission }
        val result = delegate.getPermissionAllKinds(key, type)
        cache.store(k, ApplicationDaoCache.Value(null, result), readGeneration)
        return result
    }

    override fun getPermissionForRelay(key: String, type: String, kind: Int, relay: String): ApplicationPermissionsEntity? {
        val k = ApplicationDaoCache.Key(ApplicationDaoCache.Method.PERM_RELAY, key, type, kind, relay)
        if (isExpired(key)) return null
        val readGeneration = cache.generation()
        cache.lookup(k)?.let { return it.permission }
        val result = delegate.getPermissionForRelay(key, type, kind, relay)
        cache.store(k, ApplicationDaoCache.Value(null, result), readGeneration)
        return result
    }

    override fun getWildcardRelayPermission(key: String, type: String, kind: Int): ApplicationPermissionsEntity? {
        val k = ApplicationDaoCache.Key(ApplicationDaoCache.Method.PERM_WILDCARD, key, type, kind, null)
        if (isExpired(key)) return null
        val readGeneration = cache.generation()
        cache.lookup(k)?.let { return it.permission }
        val result = delegate.getWildcardRelayPermission(key, type, kind)
        cache.store(k, ApplicationDaoCache.Value(null, result), readGeneration)
        return result
    }

    override fun getDeleteAfter(key: String): Long? = delegate.getDeleteAfter(key)

    override suspend fun getAllByKey(key: String): List<ApplicationPermissionsEntity> = delegate.getAllByKey(key)

    override fun getAllRejectedPermissions(): List<ApplicationPermissionsEntity> = delegate.getAllRejectedPermissions()

    override fun getAllAcceptedPermissions(): List<ApplicationPermissionsEntity> = delegate.getAllAcceptedPermissions()
}
