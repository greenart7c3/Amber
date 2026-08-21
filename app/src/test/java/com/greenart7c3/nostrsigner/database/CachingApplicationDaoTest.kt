package com.greenart7c3.nostrsigner.database

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Test

/**
 * Verifies the per-account getAll() read-through cache introduced because
 * NotificationSubscription.updateFilter() re-runs getAll on every ~30s relay
 * refresh and each call used to re-decrypt every application row through
 * AndroidKeyStore. The delegate must be hit once per cache lifetime, and every
 * application-table mutation must evict the cached list.
 */
class CachingApplicationDaoTest {
    private lateinit var delegate: ApplicationDao
    private lateinit var dao: CachingApplicationDao

    private val accountKey = "accounthexpubkey"

    private fun entity(key: String = "app1") = ApplicationEntity.empty().copy(
        key = key,
        pubKey = accountKey,
        localKey = "ab".repeat(32),
    )

    @Before
    fun setUp() {
        delegate = mockk(relaxed = true)
        dao = CachingApplicationDao(delegate)
    }

    private suspend fun stubGetAll(vararg entities: ApplicationEntity) {
        coEvery { delegate.getAll(accountKey) } returns entities.toList()
    }

    @Test
    fun `getAll hits the delegate once per cache lifetime`() = runBlocking {
        stubGetAll(entity())

        dao.getAll(accountKey)
        dao.getAll(accountKey)
        dao.getAll(accountKey)

        coVerify(exactly = 1) { delegate.getAll(accountKey) }
    }

    @Test
    fun `getAll returns equal lists on cache hits`() = runBlocking {
        val first = entity()
        stubGetAll(first)

        assertEquals(listOf(first), dao.getAll(accountKey))
        assertEquals(listOf(first), dao.getAll(accountKey))
    }

    @Test
    fun `returned list is a defensive copy of the cached list`() = runBlocking {
        stubGetAll(entity())

        val first = dao.getAll(accountKey)
        val second = dao.getAll(accountKey)

        // Cache hits must not hand out the same mutable list instance callers
        // could alias; equality stays intact.
        assertNotSame(first, second)
        assertEquals(first, second)
    }

    @Test
    fun `insertApplication evicts the owning account's cached list`() = runBlocking {
        stubGetAll(entity())
        dao.getAll(accountKey)

        dao.insertApplication(entity())

        dao.getAll(accountKey)
        coVerify(exactly = 2) { delegate.getAll(accountKey) }
    }

    @Test
    fun `insertAll evicts the owning account's cached list`() = runBlocking {
        stubGetAll(entity())
        dao.getAll(accountKey)

        dao.insertAll(listOf(entity()))

        dao.getAll(accountKey)
        coVerify(exactly = 2) { delegate.getAll(accountKey) }
    }

    @Test
    fun `insertApplicationWithPermissions evicts the owning account's cached list`() = runBlocking {
        stubGetAll(entity())
        dao.getAll(accountKey)

        val withPermissions = ApplicationWithPermissions(entity(), mutableListOf())
        dao.insertApplicationWithPermissions(withPermissions)

        dao.getAll(accountKey)
        coVerify(exactly = 2) { delegate.getAll(accountKey) }
    }

    @Test
    fun `delete by entity evicts the owning account's cached list`() = runBlocking {
        stubGetAll(entity())
        dao.getAll(accountKey)

        dao.delete(entity())

        dao.getAll(accountKey)
        coVerify(exactly = 2) { delegate.getAll(accountKey) }
    }

    @Test
    fun `delete by key evicts all cached lists`() = runBlocking {
        stubGetAll(entity())
        dao.getAll(accountKey)

        dao.delete("app1")

        dao.getAll(accountKey)
        coVerify(exactly = 2) { delegate.getAll(accountKey) }
    }

    @Test
    fun `updateLastUsed evicts all cached lists`() = runBlocking {
        stubGetAll(entity())
        dao.getAll(accountKey)

        dao.updateLastUsed("app1", 123L)

        dao.getAll(accountKey)
        coVerify(exactly = 2) { delegate.getAll(accountKey) }
    }

    @Test
    fun `updateNameAndIcon evicts all cached lists`() = runBlocking {
        stubGetAll(entity())
        dao.getAll(accountKey)

        dao.updateNameAndIcon("app1", "name", "icon")

        dao.getAll(accountKey)
        coVerify(exactly = 2) { delegate.getAll(accountKey) }
    }

    @Test
    fun `updateEncryptedColumnsRaw evicts all cached lists`() = runBlocking {
        stubGetAll(entity())
        dao.getAll(accountKey)

        dao.updateEncryptedColumnsRaw("app1", "secret", "localKey")

        dao.getAll(accountKey)
        coVerify(exactly = 2) { delegate.getAll(accountKey) }
    }

    @Test
    fun `deleteOldApplications evicts cached lists when rows are removed`() = runBlocking {
        stubGetAll(entity())
        dao.getAll(accountKey)

        coEvery { delegate.deleteOldApplications(any()) } returns 1
        dao.deleteOldApplications(0L)

        dao.getAll(accountKey)
        coVerify(exactly = 2) { delegate.getAll(accountKey) }
    }

    @Test
    fun `permission-only writes do not evict the cached list`() = runBlocking {
        stubGetAll(entity())
        dao.getAll(accountKey)

        dao.deletePermissions("app1")

        dao.getAll(accountKey)
        coVerify(exactly = 1) { delegate.getAll(accountKey) }
    }

    @Test
    fun `accounts are cached independently`() = runBlocking {
        val otherAccount = "otheraccounthexkey"
        coEvery { delegate.getAll(accountKey) } returns listOf(entity())
        coEvery { delegate.getAll(otherAccount) } returns emptyList()

        dao.getAll(accountKey)
        dao.getAll(otherAccount)
        dao.getAll(accountKey)
        dao.getAll(otherAccount)

        coVerify(exactly = 1) { delegate.getAll(accountKey) }
        coVerify(exactly = 1) { delegate.getAll(otherAccount) }
    }
}
