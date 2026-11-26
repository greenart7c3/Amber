package com.greenart7c3.nostrsigner.database

import androidx.collection.LruCache

/**
 * The bounded read-through cache one account's caching DAOs share. [ApplicationDaos.cached] builds one
 * instance per account and hands it to every caching DAO of that account: the writes of one DAO evict the
 * reads of another only because they share it, so never give a caching DAO a cache of its own.
 *
 * [CachingPermissionLookupDao] caches the queries on the signing hot path
 * through it. SignerProvider runs its
 * lookups inside a runBlocking on the calling app's binder thread, so each
 * redundant SQLite roundtrip costs IPC latency. Most apps issue many requests
 * against the same permission rows, so a small LRU per account eliminates almost
 * all of those roundtrips.
 *
 * Negative results are cached too — "no permission row" is the common branch
 * and dominates the miss path otherwise.
 *
 * Per app key it holds the SIGN_POLICY entry, one entry per permission lookup, and the DELETE_AFTER entry:
 * the application row's raw `deleteAfter` timestamp, or null when there is no row. Only the timestamp is
 * cached, never the verdict: [CachingPermissionLookupDao] compares it with the clock on every lookup, so a
 * connection whose lifetime runs out stops answering at once, with no write and no database read.
 *
 * Invalidation is coarse-grained, so callers never need to know which exact (type, kind, relay) tuple
 * they touched. Every caching write delegates first and evicts afterwards:
 * - A permission write evicts every entry of its app key, and so does an application-row write (an insert
 *   or delete can change `signPolicy` and `deleteAfter`, which the SIGN_POLICY and DELETE_AFTER entries hold).
 * - [CachingPermissionMaintenanceDao.updateExpiredPermissions] and
 *   [CachingPermissionMaintenanceDao.deleteOldApplications] touch many apps at once and clear the whole
 *   permission cache (the latter also every [CachingApplicationDao.getAll] list), and only when they
 *   changed a row.
 * - [CachingApplicationWriteDao.updateLastUsed], [CachingApplicationWriteDao.replaceSecretRaw] and
 *   [CachingApplicationWriteDao.replaceLocalKeyRaw] change no column a cached permission read uses, so
 *   they evict only the [CachingApplicationDao.getAll] lists.
 * - The other `*Raw` writes forward without evicting, on purpose. Most are the Room halves of the DAOs'
 *   default methods, whose caching overrides evict after delegating;
 *   [PermissionMaintenanceDao.insertPermissionsRaw] is a standalone insert that only the instrumented
 *   tests use. Main code must write permissions and application rows through an evicting method, never a
 *   `*Raw` one: a connection's expiry is read from the cached DELETE_AFTER entry, so an application-row
 *   write that does not evict leaves the old lifetime in force.
 *
 * Every invalidation bumps a generation counter, and a read only stores its
 * result if the generation is unchanged since the read began. Otherwise, a
 * delegate read that started before a revocation could land after the
 * invalidation and resurrect the revoked grant in the cache.
 *
 * [CachingApplicationDao.getAll] results are cached per account pubKey in a
 * separate small LRU. That query decrypts every application row (AndroidKeyStore
 * AES-GCM, a binder hop to keystore2 per field) and
 * [com.greenart7c3.nostrsigner.service.NotificationSubscription] re-runs it on
 * every relay-refresh cycle, which made it the app's dominant native allocator.
 * A write evicts the affected account's list, or every list when only the app key — not the owning
 * account — is known.
 * Cached lists are handed out as defensive copies; callers must not expect
 * in-place edits of returned entities to be visible (all current call sites are
 * read-only consumers).
 */
class ApplicationDaoCache {
    private enum class Method { SIGN_POLICY, DELETE_AFTER, PERM, PERM_KIND, PERM_ALL_KINDS, PERM_RELAY, PERM_WILDCARD }

    private data class Key(
        val method: Method,
        val pkKey: String,
        val type: String?,
        val kind: Int?,
        val relay: String?,
    )

    private data class Value(
        val signPolicy: Int? = null,
        val permission: ApplicationPermissionsEntity? = null,
        val deleteAfter: Long? = null,
    )

    private val cache = LruCache<Key, Value>(MAX_ENTRIES)
    private var cacheGeneration = 0L

    // One entry per account (key = account pubKey); a handful at most.
    private val getAllCache = LruCache<String, List<ApplicationEntity>>(GET_ALL_MAX_ENTRIES)
    private var getAllGeneration = 0L

    internal fun signPolicy(key: String, load: () -> Int?): Int? = cached(Key(Method.SIGN_POLICY, key, null, null, null), Value::signPolicy, { Value(signPolicy = it) }, load)

    internal fun deleteAfter(key: String, load: () -> Long?): Long? = cached(Key(Method.DELETE_AFTER, key, null, null, null), Value::deleteAfter, { Value(deleteAfter = it) }, load)

    internal fun permission(key: String, type: String, kind: Int?, load: () -> ApplicationPermissionsEntity?): ApplicationPermissionsEntity? = cachedPermission(Key(Method.PERM_KIND, key, type, kind, null), load)

    internal fun permission(key: String, type: String, load: () -> ApplicationPermissionsEntity?): ApplicationPermissionsEntity? = cachedPermission(Key(Method.PERM, key, type, null, null), load)

    internal fun permissionAllKinds(key: String, type: String, load: () -> ApplicationPermissionsEntity?): ApplicationPermissionsEntity? = cachedPermission(Key(Method.PERM_ALL_KINDS, key, type, null, null), load)

    internal fun permissionForRelay(key: String, type: String, kind: Int, relay: String, load: () -> ApplicationPermissionsEntity?): ApplicationPermissionsEntity? = cachedPermission(Key(Method.PERM_RELAY, key, type, kind, relay), load)

    internal fun wildcardRelayPermission(key: String, type: String, kind: Int, load: () -> ApplicationPermissionsEntity?): ApplicationPermissionsEntity? = cachedPermission(Key(Method.PERM_WILDCARD, key, type, kind, null), load)

    private fun cachedPermission(key: Key, load: () -> ApplicationPermissionsEntity?): ApplicationPermissionsEntity? = cached(key, Value::permission, { Value(permission = it) }, load)

    private fun <T> cached(key: Key, extract: (Value) -> T, wrap: (T) -> Value, load: () -> T): T {
        val readGeneration = synchronized(cache) { cacheGeneration }
        synchronized(cache) { cache[key] }?.let { return extract(it) }
        val result = load()
        synchronized(cache) {
            if (readGeneration == cacheGeneration) cache.put(key, wrap(result))
        }
        return result
    }

    internal fun invalidateGetAll(pubKey: String?) {
        synchronized(getAllCache) {
            getAllGeneration++
            if (pubKey != null) getAllCache.remove(pubKey) else getAllCache.evictAll()
        }
    }

    internal fun invalidateApp(pkKey: String) {
        synchronized(cache) {
            cacheGeneration++
            val toRemove = cache.snapshot().keys.filter { it.pkKey == pkKey }
            toRemove.forEach { cache.remove(it) }
        }
    }

    internal fun invalidateAll() {
        synchronized(cache) {
            cacheGeneration++
            cache.evictAll()
        }
    }

    internal suspend fun getAll(pubKey: String, load: suspend () -> List<ApplicationEntity>): List<ApplicationEntity> {
        val readGeneration = synchronized(getAllCache) {
            getAllCache[pubKey]?.let { return it.toList() }
            getAllGeneration
        }
        val result = load()
        synchronized(getAllCache) {
            if (readGeneration == getAllGeneration) getAllCache.put(pubKey, result)
        }
        return result.toList()
    }

    private companion object {
        const val MAX_ENTRIES = 512
        const val GET_ALL_MAX_ENTRIES = 8
    }
}

/**
 * [ApplicationDao] in front of Room that serves [getAll] from the shared
 * [ApplicationDaoCache] and hides connections past their `deleteAfter` lifetime.
 */
class CachingApplicationDao(
    private val delegate: ApplicationDao,
    private val cache: ApplicationDaoCache,
) : ApplicationDao {
    override suspend fun getAll(pubKey: String): List<ApplicationEntity> = cache.getAll(pubKey) { delegate.getAll(pubKey).filterNot { it.isExpired() } }

    override suspend fun getAppName(key: String): String? = delegate.getAppName(key)

    override suspend fun getByKey(key: String): ApplicationWithPermissions? = delegate.getByKey(key)

    override fun getByKeySync(key: String): ApplicationWithPermissions? = delegate.getByKeySync(key)

    override suspend fun getByName(name: String): ApplicationWithPermissions? = delegate.getByName(name)

    override suspend fun getBySecret(secret: String): ApplicationWithPermissions? = delegate.getBySecret(secret)?.takeUnless { it.application.isExpired() }

    // -------- *Raw delegations (envelope-encrypted persistence boundary) --------
    //
    // ApplicationDao now splits methods that touch the [ApplicationEntity.secret]
    // / [ApplicationEntity.localKey] columns into a Room-generated `*Raw`
    // (encrypted column values) and a public default-method wrapper that
    // envelope-encrypts/decrypts via [ApplicationEntity.encryptForStorage].
    // CachingApplicationDao is a decorator over a Room-generated ApplicationDao,
    // so its `*Raw` overrides simply forward to [delegate] — they are never
    // invoked from the cache layer (the public wrappers above are the entry
    // points), but Kotlin requires every interface member to be implemented.

    override suspend fun getAllRaw(pubKey: String): List<ApplicationEntity> = delegate.getAllRaw(pubKey)

    override suspend fun getByKeyRaw(key: String): ApplicationWithPermissions? = delegate.getByKeyRaw(key)

    override fun getByKeySyncRaw(key: String): ApplicationWithPermissions? = delegate.getByKeySyncRaw(key)

    override suspend fun getByNameRaw(name: String): ApplicationWithPermissions? = delegate.getByNameRaw(name)

    override suspend fun getAllApplicationsRaw(): List<ApplicationEntity> = delegate.getAllApplicationsRaw()
}
