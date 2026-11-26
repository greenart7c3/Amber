package com.greenart7c3.nostrsigner.database

/** [ApplicationWriteDao] whose writes evict what they change from the shared [ApplicationDaoCache]. */
class CachingApplicationWriteDao(
    private val delegate: ApplicationWriteDao,
    private val cache: ApplicationDaoCache,
) : ApplicationWriteDao {
    override suspend fun insertApplication(event: ApplicationEntity): Long? {
        val result = delegate.insertApplication(event)
        cache.invalidateApp(event.key)
        cache.invalidateGetAll(event.pubKey)
        return result
    }

    override fun insertAll(events: List<ApplicationEntity>): List<Long>? {
        val result = delegate.insertAll(events)
        events.forEach { cache.invalidateApp(it.key) }
        events.asSequence().map { it.pubKey }.distinct().forEach(cache::invalidateGetAll)
        return result
    }

    override suspend fun delete(entity: ApplicationEntity) {
        delegate.delete(entity)
        cache.invalidateApp(entity.key)
        cache.invalidateGetAll(entity.pubKey)
    }

    override suspend fun delete(key: String) {
        delegate.delete(key)
        cache.invalidateApp(key)
        cache.invalidateGetAll(null)
    }

    override suspend fun updateLastUsed(key: String, time: Long) {
        delegate.updateLastUsed(key, time)
        cache.invalidateGetAll(null)
    }

    override suspend fun updateNameAndIcon(key: String, name: String, icon: String) {
        delegate.updateNameAndIcon(key, name, icon)
        cache.invalidateApp(key)
        cache.invalidateGetAll(null)
    }

    override suspend fun replaceSecretRaw(key: String, expected: String, newValue: String): Int = delegate.replaceSecretRaw(key, expected, newValue).also { cache.invalidateGetAll(null) }

    override suspend fun replaceLocalKeyRaw(key: String, expected: String, newValue: String): Int = delegate.replaceLocalKeyRaw(key, expected, newValue).also { cache.invalidateGetAll(null) }

    override suspend fun insertApplicationRaw(event: ApplicationEntity): Long? = delegate.insertApplicationRaw(event)

    override fun insertAllRaw(events: List<ApplicationEntity>): List<Long>? = delegate.insertAllRaw(events)

    override suspend fun deleteRaw(entity: ApplicationEntity) = delegate.deleteRaw(entity)
}
