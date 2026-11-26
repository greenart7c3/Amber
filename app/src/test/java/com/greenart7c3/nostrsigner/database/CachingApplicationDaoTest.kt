package com.greenart7c3.nostrsigner.database

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private lateinit var raw: ApplicationDaos
    private lateinit var daos: ApplicationDaos
    private lateinit var delegate: ApplicationDao
    private lateinit var dao: ApplicationDao

    private val accountKey = "accounthexpubkey"

    private fun entity(key: String = "app1") = ApplicationEntity.empty().copy(
        key = key,
        pubKey = accountKey,
        localKey = "ab".repeat(32),
    )

    @Before
    fun setUp() {
        raw = relaxedApplicationDaos()
        daos = ApplicationDaos.cached(raw)
        delegate = raw.applicationDao
        dao = daos.applicationDao
    }

    private fun stubGetAll(vararg entities: ApplicationEntity) {
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

        daos.applicationWriteDao.insertApplication(entity())

        dao.getAll(accountKey)
        coVerify(exactly = 2) { delegate.getAll(accountKey) }
    }

    @Test
    fun `insertAll evicts the owning account's cached list`() = runBlocking {
        stubGetAll(entity())
        dao.getAll(accountKey)

        daos.applicationWriteDao.insertAll(listOf(entity()))

        dao.getAll(accountKey)
        coVerify(exactly = 2) { delegate.getAll(accountKey) }
    }

    @Test
    fun `insertApplicationWithPermissions evicts the owning account's cached list`() = runBlocking {
        stubGetAll(entity())
        dao.getAll(accountKey)

        val withPermissions = ApplicationWithPermissions(entity(), mutableListOf())
        daos.permissionWriteDao.insertApplicationWithPermissions(withPermissions)

        dao.getAll(accountKey)
        coVerify(exactly = 2) { delegate.getAll(accountKey) }
    }

    @Test
    fun `delete by entity evicts the owning account's cached list`() = runBlocking {
        stubGetAll(entity())
        dao.getAll(accountKey)

        daos.applicationWriteDao.delete(entity())

        dao.getAll(accountKey)
        coVerify(exactly = 2) { delegate.getAll(accountKey) }
    }

    @Test
    fun `delete by key evicts all cached lists`() = runBlocking {
        stubGetAll(entity())
        dao.getAll(accountKey)

        daos.applicationWriteDao.delete("app1")

        dao.getAll(accountKey)
        coVerify(exactly = 2) { delegate.getAll(accountKey) }
    }

    @Test
    fun `updateLastUsed evicts all cached lists`() = runBlocking {
        stubGetAll(entity())
        dao.getAll(accountKey)

        daos.applicationWriteDao.updateLastUsed("app1", 123L)

        dao.getAll(accountKey)
        coVerify(exactly = 2) { delegate.getAll(accountKey) }
    }

    @Test
    fun `updateNameAndIcon evicts all cached lists`() = runBlocking {
        stubGetAll(entity())
        dao.getAll(accountKey)

        daos.applicationWriteDao.updateNameAndIcon("app1", "name", "icon")

        dao.getAll(accountKey)
        coVerify(exactly = 2) { delegate.getAll(accountKey) }
    }

    @Test
    fun `replaceSecretRaw evicts all cached lists`() = runBlocking {
        stubGetAll(entity())
        dao.getAll(accountKey)

        coEvery { raw.applicationWriteDao.replaceSecretRaw(any(), any(), any()) } returns 1
        daos.applicationWriteDao.replaceSecretRaw("app1", "old", "new")

        dao.getAll(accountKey)
        coVerify(exactly = 2) { delegate.getAll(accountKey) }
    }

    @Test
    fun `replaceLocalKeyRaw evicts all cached lists`() = runBlocking {
        stubGetAll(entity())
        dao.getAll(accountKey)

        coEvery { raw.applicationWriteDao.replaceLocalKeyRaw(any(), any(), any()) } returns 1
        daos.applicationWriteDao.replaceLocalKeyRaw("app1", "old", "new")

        dao.getAll(accountKey)
        coVerify(exactly = 2) { delegate.getAll(accountKey) }
    }

    @Test
    fun `deleteOldApplications evicts cached lists when rows are removed`() = runBlocking {
        stubGetAll(entity())
        dao.getAll(accountKey)

        coEvery { raw.permissionMaintenanceDao.deleteOldApplications(any()) } returns 1
        daos.permissionMaintenanceDao.deleteOldApplications(0L)

        dao.getAll(accountKey)
        coVerify(exactly = 2) { delegate.getAll(accountKey) }
    }

    @Test
    fun `permission-only writes do not evict the cached list`() = runBlocking {
        stubGetAll(entity())
        dao.getAll(accountKey)

        daos.permissionWriteDao.deletePermissions("app1")

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

    /**
     * Runs [read] on another thread, blocks it inside the delegate until [revoke]
     * has completed, then lets the stale delegate result return. Models a signing
     * request racing a revocation from the UI.
     */
    private fun <T> raceReadAgainstRevocation(
        stubFirstRead: (blockUntilRevoked: () -> Unit) -> Unit,
        read: () -> T,
        revoke: suspend () -> Unit,
    ) = runBlocking {
        val readStarted = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        stubFirstRead {
            readStarted.countDown()
            check(releaseRead.await(5, TimeUnit.SECONDS))
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val pending = executor.submit<T> { read() }
            assertTrue(readStarted.await(5, TimeUnit.SECONDS))
            revoke()
            releaseRead.countDown()
            pending.get(5, TimeUnit.SECONDS)
        } finally {
            releaseRead.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `read overlapping a permission revocation does not cache the stale grant`() {
        val grant = ApplicationPermissionsEntity(null, "app1", "SIGN_EVENT", 1, true, 4, Long.MAX_VALUE, 0)
        var firstRead = true
        raceReadAgainstRevocation(
            stubFirstRead = { block ->
                every { raw.permissionLookupDao.getPermission("app1", "SIGN_EVENT", 1) } answers {
                    if (firstRead) {
                        firstRead = false
                        block()
                        grant
                    } else {
                        null
                    }
                }
            },
            read = { daos.permissionLookupDao.getPermission("app1", "SIGN_EVENT", 1) },
            revoke = { daos.permissionWriteDao.deletePermissions("app1") },
        )

        assertNull(daos.permissionLookupDao.getPermission("app1", "SIGN_EVENT", 1))
    }

    @Test
    fun `read overlapping an app deletion does not cache the stale sign policy`() {
        var firstRead = true
        raceReadAgainstRevocation(
            stubFirstRead = { block ->
                every { raw.permissionLookupDao.getSignPolicy("app1") } answers {
                    if (firstRead) {
                        firstRead = false
                        block()
                        2
                    } else {
                        null
                    }
                }
            },
            read = { daos.permissionLookupDao.getSignPolicy("app1") },
            revoke = { daos.applicationWriteDao.delete("app1") },
        )

        assertNull(daos.permissionLookupDao.getSignPolicy("app1"))
    }

    @Test
    fun `getAll overlapping an app deletion does not cache the stale list`() {
        var firstRead = true
        raceReadAgainstRevocation(
            stubFirstRead = { block ->
                coEvery { delegate.getAll(accountKey) } answers {
                    if (firstRead) {
                        firstRead = false
                        block()
                        listOf(entity())
                    } else {
                        emptyList()
                    }
                }
            },
            read = { runBlocking { dao.getAll(accountKey) } },
            revoke = { daos.applicationWriteDao.delete(entity()) },
        )

        assertEquals(emptyList<ApplicationEntity>(), runBlocking { dao.getAll(accountKey) })
    }

    @Test
    fun `older suspended read cannot refill a cache invalidated by connection insertion`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val existing = entity("existing")
        val added = entity("added")
        var reads = 0
        coEvery { delegate.getAll(accountKey) } coAnswers {
            reads++
            if (reads == 1) {
                started.complete(Unit)
                release.await()
                listOf(existing)
            } else {
                listOf(existing, added)
            }
        }
        val oldRead = async(start = CoroutineStart.UNDISPATCHED) { dao.getAll(accountKey) }
        started.await()
        daos.applicationWriteDao.insertApplication(added)
        release.complete(Unit)
        assertEquals(listOf(existing), oldRead.await())
        assertEquals(listOf(existing, added), dao.getAll(accountKey))
        assertEquals(listOf(existing, added), dao.getAll(accountKey))
        coVerify(exactly = 2) { delegate.getAll(accountKey) }
    }

    @Test
    fun `older suspended read cannot restore a deleted connection`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val removed = entity("removed")
        var reads = 0
        coEvery { delegate.getAll(accountKey) } coAnswers {
            reads++
            if (reads == 1) {
                started.complete(Unit)
                release.await()
                listOf(removed)
            } else {
                emptyList()
            }
        }
        val oldRead = async(start = CoroutineStart.UNDISPATCHED) { dao.getAll(accountKey) }
        started.await()
        daos.applicationWriteDao.delete(removed)
        release.complete(Unit)
        oldRead.await()
        assertEquals(emptyList<ApplicationEntity>(), dao.getAll(accountKey))
        coVerify(exactly = 2) { delegate.getAll(accountKey) }
    }
}
