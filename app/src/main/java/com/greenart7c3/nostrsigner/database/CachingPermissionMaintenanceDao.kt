package com.greenart7c3.nostrsigner.database

/** [PermissionMaintenanceDao] whose changes evict what they touch from the shared [ApplicationDaoCache]. */
class CachingPermissionMaintenanceDao(
    private val delegate: PermissionMaintenanceDao,
    private val cache: ApplicationDaoCache,
) : PermissionMaintenanceDao {
    override fun updateExpiredPermissions(time: Long): Int {
        val n = delegate.updateExpiredPermissions(time)
        if (n > 0) {
            cache.invalidateAll()
        }
        return n
    }

    override suspend fun deleteOldApplications(time: Long): Int {
        val n = delegate.deleteOldApplications(time)
        if (n > 0) {
            cache.invalidateAll()
            cache.invalidateGetAll(null)
        }
        return n
    }

    override suspend fun deletePermission(permission: ApplicationPermissionsEntity) {
        delegate.deletePermission(permission)
        cache.invalidateApp(permission.pkKey)
    }

    override suspend fun insertPermissionsRaw(permissions: List<ApplicationPermissionsEntity>): List<Long>? = delegate.insertPermissionsRaw(permissions)
}
