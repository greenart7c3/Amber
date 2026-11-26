package com.greenart7c3.nostrsigner.database

import androidx.collection.LruCache

/**
 * The bounded read-through cache one account's caching DAOs ([ApplicationDaos.cached]) share.
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
 * Writes invalidate every cache entry whose key shares the affected pkKey
 * (coarse-grained per-app). Every mutation of the application and permission
 * DAOs is scoped to a single app key, so this matches the natural granularity
 * without forcing callers to know which exact (type, kind, relay) tuple they touched.
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
 * Any mutation of the `application` table evicts the affected account's entry
 * (or all entries when only the app key — not the owning account — is known).
 * Cached lists are handed out as defensive copies; callers must not expect
 * in-place edits of returned entities to be visible (all current call sites are
 * read-only consumers).
 */
class ApplicationDaoCache {
    /** The cached query a [Key] belongs to. */
    enum class Method { SIGN_POLICY, PERM, PERM_KIND, PERM_ALL_KINDS, PERM_RELAY, PERM_WILDCARD }

    /** One cached query with its arguments. */
    data class Key(
        val method: Method,
        val pkKey: String,
        val type: String?,
        val kind: Int?,
        val relay: String?,
    )

    /** A cached query result, including a cached "no row". */
    data class Value(
        val signPolicy: Int?,
        val permission: ApplicationPermissionsEntity?,
    )

    private val cache = LruCache<Key, Value>(MAX_ENTRIES)
    private var cacheGeneration = 0L

    // One entry per account (key = account pubKey); a handful at most.
    private val getAllCache = LruCache<String, List<ApplicationEntity>>(GET_ALL_MAX_ENTRIES)
    private var getAllGeneration = 0L

    fun generation(): Long = synchronized(cache) { cacheGeneration }

    fun lookup(key: Key): Value? = synchronized(cache) { cache[key] }

    /** Caches [value] unless an invalidation ran after the read began at [readGeneration]. */
    fun store(key: Key, value: Value, readGeneration: Long) {
        synchronized(cache) {
            if (readGeneration == cacheGeneration) cache.put(key, value)
        }
    }

    /** Evicts the cached [getAll] list for [pubKey], or every list when null. */
    fun invalidateGetAll(pubKey: String?) {
        synchronized(getAllCache) {
            getAllGeneration++
            if (pubKey != null) getAllCache.remove(pubKey) else getAllCache.evictAll()
        }
    }

    fun invalidateApp(pkKey: String) {
        synchronized(cache) {
            cacheGeneration++
            val toRemove = cache.snapshot().keys.filter { it.pkKey == pkKey }
            toRemove.forEach { cache.remove(it) }
        }
    }

    fun invalidateAll() {
        synchronized(cache) {
            cacheGeneration++
            cache.evictAll()
        }
    }

    suspend fun getAll(pubKey: String, load: suspend () -> List<ApplicationEntity>): List<ApplicationEntity> {
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
