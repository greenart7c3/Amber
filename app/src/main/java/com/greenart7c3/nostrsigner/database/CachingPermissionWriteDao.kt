package com.greenart7c3.nostrsigner.database

/** [PermissionWriteDao] whose writes evict the affected application's entries from the shared [ApplicationDaoCache]. */
class CachingPermissionWriteDao(
    private val delegate: PermissionWriteDao,
    private val cache: ApplicationDaoCache,
) : PermissionWriteDao {
    override suspend fun insertPermissions(permissions: List<ApplicationPermissionsEntity>): List<Long>? {
        val result = delegate.insertPermissions(permissions)
        permissions.asSequence().map { it.pkKey }.distinct().forEach(cache::invalidateApp)
        return result
    }

    override suspend fun insertPermissions2(permissions: List<ApplicationPermissionsEntity>): List<Long>? {
        val result = delegate.insertPermissions2(permissions)
        permissions.asSequence().map { it.pkKey }.distinct().forEach(cache::invalidateApp)
        return result
    }

    override suspend fun insertApplicationWithPermissions(application: ApplicationWithPermissions) {
        delegate.insertApplicationWithPermissions(application)
        cache.invalidateApp(application.application.key)
        cache.invalidateGetAll(application.application.pubKey)
    }

    override suspend fun deletePermissions(key: String) {
        delegate.deletePermissions(key)
        cache.invalidateApp(key)
    }

    override suspend fun deletePermissions(key: String, type: String) {
        delegate.deletePermissions(key, type)
        cache.invalidateApp(key)
    }

    override suspend fun deletePermissions(key: String, type: String, kind: Int) {
        delegate.deletePermissions(key, type, kind)
        cache.invalidateApp(key)
    }

    override suspend fun deletePermissions(key: String, type: String, kind: Int, relay: String) {
        delegate.deletePermissions(key, type, kind, relay)
        cache.invalidateApp(key)
    }

    override suspend fun deletePermissionsForKind(key: String, type: String, kind: Int) {
        delegate.deletePermissionsForKind(key, type, kind)
        cache.invalidateApp(key)
    }

    override suspend fun insertApplicationRaw(event: ApplicationEntity): Long? = delegate.insertApplicationRaw(event)

    override suspend fun insertPermissions2Raw(permissions: List<ApplicationPermissionsEntity>): List<Long>? = delegate.insertPermissions2Raw(permissions)

    override suspend fun getExistingApplicationKeys(keys: List<String>): List<String> = delegate.getExistingApplicationKeys(keys)
}
