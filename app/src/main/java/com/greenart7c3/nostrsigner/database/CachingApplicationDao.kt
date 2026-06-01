package com.greenart7c3.nostrsigner.database

import androidx.collection.LruCache
import androidx.paging.PagingSource

/**
 * Bounded read-through cache in front of [ApplicationDao] for the five queries
 * on the signing hot path. SignerProvider runs its lookups inside a runBlocking
 * on the calling app's binder thread, so each redundant SQLite roundtrip costs
 * IPC latency. Most apps issue many requests against the same permission rows,
 * so a small LRU per account eliminates almost all of those roundtrips.
 *
 * Negative results are cached too — "no permission row" is the common branch
 * and dominates the miss path otherwise.
 *
 * Writes invalidate every cache entry whose key shares the affected pkKey
 * (coarse-grained per-app). Every mutation in [ApplicationDao] is scoped to a
 * single app key, so this matches the natural granularity without forcing
 * callers to know which exact (type, kind, relay) tuple they touched.
 */
class CachingApplicationDao(
    private val delegate: ApplicationDao,
) : ApplicationDao {
    private enum class Method { SIGN_POLICY, PERM, PERM_KIND, PERM_ALL_KINDS, PERM_RELAY, PERM_WILDCARD }

    private data class Key(
        val method: Method,
        val pkKey: String,
        val type: String?,
        val kind: Int?,
        val relay: String?,
    )

    private data class Value(
        val signPolicy: Int?,
        val permission: ApplicationPermissionsEntity?,
    )

    private val cache = LruCache<Key, Value>(MAX_ENTRIES)

    private fun lookup(key: Key): Value? = synchronized(cache) { cache.get(key) }

    private fun store(key: Key, value: Value) {
        synchronized(cache) { cache.put(key, value) }
    }

    private fun invalidateApp(pkKey: String) {
        synchronized(cache) {
            val toRemove = cache.snapshot().keys.filter { it.pkKey == pkKey }
            toRemove.forEach { cache.remove(it) }
        }
    }

    private fun invalidateAll() {
        synchronized(cache) { cache.evictAll() }
    }

    // -------- cached reads --------

    override fun getSignPolicy(key: String): Int? {
        val k = Key(Method.SIGN_POLICY, key, null, null, null)
        lookup(k)?.let { return it.signPolicy }
        val result = delegate.getSignPolicy(key)
        store(k, Value(result, null))
        return result
    }

    override fun getPermission(key: String, type: String, kind: Int?): ApplicationPermissionsEntity? {
        val k = Key(Method.PERM_KIND, key, type, kind, null)
        lookup(k)?.let { return it.permission }
        val result = delegate.getPermission(key, type, kind)
        store(k, Value(null, result))
        return result
    }

    override fun getPermission(key: String, type: String): ApplicationPermissionsEntity? {
        val k = Key(Method.PERM, key, type, null, null)
        lookup(k)?.let { return it.permission }
        val result = delegate.getPermission(key, type)
        store(k, Value(null, result))
        return result
    }

    override fun getPermissionAllKinds(key: String, type: String): ApplicationPermissionsEntity? {
        val k = Key(Method.PERM_ALL_KINDS, key, type, null, null)
        lookup(k)?.let { return it.permission }
        val result = delegate.getPermissionAllKinds(key, type)
        store(k, Value(null, result))
        return result
    }

    override fun getPermissionForRelay(key: String, type: String, kind: Int, relay: String): ApplicationPermissionsEntity? {
        val k = Key(Method.PERM_RELAY, key, type, kind, relay)
        lookup(k)?.let { return it.permission }
        val result = delegate.getPermissionForRelay(key, type, kind, relay)
        store(k, Value(null, result))
        return result
    }

    override fun getWildcardRelayPermission(key: String, type: String, kind: Int): ApplicationPermissionsEntity? {
        val k = Key(Method.PERM_WILDCARD, key, type, kind, null)
        lookup(k)?.let { return it.permission }
        val result = delegate.getWildcardRelayPermission(key, type, kind)
        store(k, Value(null, result))
        return result
    }

    // -------- writes that must invalidate --------

    override suspend fun insertPermissions(permissions: List<ApplicationPermissionsEntity>): List<Long>? {
        val result = delegate.insertPermissions(permissions)
        permissions.asSequence().map { it.pkKey }.distinct().forEach(::invalidateApp)
        return result
    }

    override suspend fun insertPermissions2(permissions: List<ApplicationPermissionsEntity>): List<Long>? {
        val result = delegate.insertPermissions2(permissions)
        permissions.asSequence().map { it.pkKey }.distinct().forEach(::invalidateApp)
        return result
    }

    override suspend fun insertApplicationWithPermissions(application: ApplicationWithPermissions) {
        delegate.insertApplicationWithPermissions(application)
        invalidateApp(application.application.key)
    }

    override suspend fun deletePermissions(key: String) {
        delegate.deletePermissions(key)
        invalidateApp(key)
    }

    override suspend fun deletePermissions(key: String, type: String) {
        delegate.deletePermissions(key, type)
        invalidateApp(key)
    }

    override suspend fun deletePermissions(key: String, type: String, kind: Int) {
        delegate.deletePermissions(key, type, kind)
        invalidateApp(key)
    }

    override suspend fun deletePermissions(key: String, type: String, kind: Int, relay: String) {
        delegate.deletePermissions(key, type, kind, relay)
        invalidateApp(key)
    }

    override suspend fun deletePermissionsForKind(key: String, type: String, kind: Int) {
        delegate.deletePermissionsForKind(key, type, kind)
        invalidateApp(key)
    }

    override suspend fun deletePermission(permission: ApplicationPermissionsEntity) {
        delegate.deletePermission(permission)
        invalidateApp(permission.pkKey)
    }

    override suspend fun delete(entity: ApplicationEntity) {
        delegate.delete(entity)
        invalidateApp(entity.key)
    }

    override suspend fun delete(key: String) {
        delegate.delete(key)
        invalidateApp(key)
    }

    // Affects many apps at once (timestamp predicate), so wipe the whole cache.
    override fun updateExpiredPermissions(time: Long) {
        delegate.updateExpiredPermissions(time)
        invalidateAll()
    }

    override suspend fun deleteOldApplications(time: Long): Int {
        val n = delegate.deleteOldApplications(time)
        if (n > 0) invalidateAll()
        return n
    }

    // -------- pass-through (not cached, not invalidating) --------

    override suspend fun getAll(pubKey: String): List<ApplicationEntity> = delegate.getAll(pubKey)

    override suspend fun getAllNotConnected(): List<ApplicationWithPermissions> = delegate.getAllNotConnected()

    override fun getAllPaging(pubKey: String): PagingSource<Int, ApplicationEntity> = delegate.getAllPaging(pubKey)

    override fun getAllRelayLists(): List<RelayListWrapper> = delegate.getAllRelayLists()

    override suspend fun getAppName(key: String): String? = delegate.getAppName(key)

    override suspend fun getByKey(key: String): ApplicationWithPermissions? = delegate.getByKey(key)

    override fun getByKeySync(key: String): ApplicationWithPermissions? = delegate.getByKeySync(key)

    override suspend fun getByName(name: String): ApplicationWithPermissions? = delegate.getByName(name)

    override suspend fun getBySecret(secret: String): ApplicationWithPermissions? = delegate.getBySecret(secret)

    override suspend fun getAllWithLocalKey(pubKey: String): List<ApplicationEntity> = delegate.getAllWithLocalKey(pubKey)

    override suspend fun getAllByKey(key: String): List<ApplicationPermissionsEntity> = delegate.getAllByKey(key)

    override fun getApplicationKeyNames(): List<ApplicationKeyName> = delegate.getApplicationKeyNames()

    override fun getAllRejectedPermissions(): List<ApplicationPermissionsEntity> = delegate.getAllRejectedPermissions()

    override fun getAllAcceptedPermissions(): List<ApplicationPermissionsEntity> = delegate.getAllAcceptedPermissions()

    override suspend fun insertApplication(event: ApplicationEntity): Long? {
        val result = delegate.insertApplication(event)
        // ApplicationEntity columns like signPolicy and lastUsed feed cached reads
        // (getSignPolicy), so invalidate the affected app's entries.
        invalidateApp(event.key)
        return result
    }

    override fun insertAll(events: List<ApplicationEntity>): List<Long>? {
        val result = delegate.insertAll(events)
        events.forEach { invalidateApp(it.key) }
        return result
    }

    override suspend fun updateLastUsed(key: String, time: Long) {
        delegate.updateLastUsed(key, time)
        // lastUsed doesn't affect any cached read, so no invalidation needed.
    }

    companion object {
        private const val MAX_ENTRIES = 512
    }
}
