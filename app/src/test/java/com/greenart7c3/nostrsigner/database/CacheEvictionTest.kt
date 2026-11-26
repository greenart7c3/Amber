package com.greenart7c3.nostrsigner.database

import com.vitorpamplona.quartz.utils.TimeUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CacheEvictionTest(private val case: WriteCase) {
    enum class Scope { APP, ALL, LIST_ONLY, NONE }

    class WriteCase(
        private val name: String,
        val scope: Scope,
        val evictsList: Boolean,
        val stubRawWrite: (raw: ApplicationDaos, duringWrite: () -> Unit) -> Unit,
        val write: suspend (cached: ApplicationDaos) -> Unit,
    ) {
        override fun toString() = name
    }

    private val raw = relaxedApplicationDaos()
    private val daos = ApplicationDaos.cached(raw)
    private val lookup = daos.permissionLookupDao

    private val signPolicies = mutableMapOf(APP_A to 1, APP_B to 1)
    private val grants = mutableMapOf(APP_A to grant(APP_A, accepted = true), APP_B to grant(APP_B, accepted = true))
    private val deleteAfters = mutableMapOf(APP_A to 0L, APP_B to 0L)

    @Volatile
    private var holdNextPermissionRead: (() -> Unit)? = null

    @Before
    fun setUp() {
        val rawLookup = raw.permissionLookupDao
        every { rawLookup.getSignPolicy(any()) } answers { signPolicies[firstArg()] }
        every { rawLookup.getDeleteAfter(any()) } answers { deleteAfters[firstArg()] }
        every { rawLookup.getPermission(any(), TYPE, KIND) } answers {
            val stored = grants[firstArg()]
            holdNextPermissionRead?.also { holdNextPermissionRead = null }?.invoke()
            stored
        }
        coEvery { raw.applicationDao.getAll(ACCOUNT) } returns emptyList()
    }

    private fun changeStoredRowsOfA() {
        signPolicies[APP_A] = 2
        grants[APP_A] = grant(APP_A, accepted = false)
        deleteAfters[APP_A] = TimeUtils.now() + 3_600
    }

    private fun warm(key: String) {
        lookup.getSignPolicy(key)
        lookup.getPermission(key, TYPE, KIND)
        lookup.getDeleteAfter(key)
    }

    private fun assertServes(key: String, signPolicy: Int, accepted: Boolean, deleteAfter: Long) {
        assertEquals(signPolicy, lookup.getSignPolicy(key))
        assertEquals(accepted, lookup.getPermission(key, TYPE, KIND)?.acceptable)
        assertEquals(deleteAfter, lookup.getDeleteAfter(key))
    }

    private fun assertRawReads(key: String, times: Int) {
        val rawLookup = raw.permissionLookupDao
        verify(exactly = times) { rawLookup.getSignPolicy(key) }
        verify(exactly = times) { rawLookup.getPermission(key, TYPE, KIND) }
        verify(exactly = times) { rawLookup.getDeleteAfter(key) }
    }

    @Test
    fun `write refetches what it changed and keeps every other entry cached`() = runBlocking {
        case.stubRawWrite(raw) {}
        warm(APP_A)
        warm(APP_B)
        daos.applicationDao.getAll(ACCOUNT)
        val oldDeleteAfterOfA = deleteAfters.getValue(APP_A)
        changeStoredRowsOfA()

        case.write(daos)

        val evictsA = case.scope == Scope.APP || case.scope == Scope.ALL
        if (evictsA) {
            assertServes(APP_A, signPolicy = 2, accepted = false, deleteAfter = deleteAfters.getValue(APP_A))
        } else {
            assertServes(APP_A, signPolicy = 1, accepted = true, deleteAfter = oldDeleteAfterOfA)
        }
        assertServes(APP_B, signPolicy = 1, accepted = true, deleteAfter = 0L)
        assertRawReads(APP_A, times = if (evictsA) 2 else 1)
        assertRawReads(APP_B, times = if (case.scope == Scope.ALL) 2 else 1)
        daos.applicationDao.getAll(ACCOUNT)
        coVerify(exactly = if (case.evictsList) 2 else 1) { raw.applicationDao.getAll(ACCOUNT) }
    }

    @Test
    fun `lookup racing the write never caches the row the write replaced`() = runBlocking {
        assumeTrue(case.scope == Scope.APP || case.scope == Scope.ALL)
        val executor = Executors.newSingleThreadExecutor()
        val readStarted = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        lateinit var racingRead: Future<ApplicationPermissionsEntity?>
        try {
            case.stubRawWrite(raw) {
                holdNextPermissionRead = {
                    readStarted.countDown()
                    check(releaseRead.await(5, TimeUnit.SECONDS))
                }
                racingRead = executor.submit<ApplicationPermissionsEntity?> { lookup.getPermission(APP_A, TYPE, KIND) }
                assertTrue(readStarted.await(5, TimeUnit.SECONDS))
                changeStoredRowsOfA()
            }

            case.write(daos)
            releaseRead.countDown()

            assertEquals(true, racingRead.get(5, TimeUnit.SECONDS)?.acceptable)
            assertEquals(false, lookup.getPermission(APP_A, TYPE, KIND)?.acceptable)
        } finally {
            releaseRead.countDown()
            executor.shutdownNow()
        }
    }

    companion object {
        private const val ACCOUNT = "account-hex"
        private const val APP_A = "app-a"
        private const val APP_B = "app-b"
        private const val TYPE = "SIGN_EVENT"
        private const val KIND = 1

        private fun grant(key: String, accepted: Boolean) = ApplicationPermissionsEntity(null, key, TYPE, KIND, accepted, 4, Long.MAX_VALUE, Long.MAX_VALUE)

        private fun application() = ApplicationEntity.empty().copy(key = APP_A, pubKey = ACCOUNT)

        private fun <T> returning(value: T, duringWrite: () -> Unit): T {
            duringWrite()
            return value
        }

        private fun applicationRowWrites() = listOf(
            WriteCase(
                "insertApplication",
                Scope.APP,
                evictsList = true,
                { raw, during -> coEvery { raw.applicationWriteDao.insertApplication(any()) } answers { returning(1L, during) } },
                { it.applicationWriteDao.insertApplication(application()) },
            ),
            WriteCase(
                "insertAll",
                Scope.APP,
                evictsList = true,
                { raw, during -> every { raw.applicationWriteDao.insertAll(any()) } answers { returning(listOf(1L), during) } },
                { it.applicationWriteDao.insertAll(listOf(application())) },
            ),
            WriteCase(
                "delete(entity)",
                Scope.APP,
                evictsList = true,
                { raw, during -> coEvery { raw.applicationWriteDao.delete(any<ApplicationEntity>()) } answers { during() } },
                { it.applicationWriteDao.delete(application()) },
            ),
            WriteCase(
                "delete(key)",
                Scope.APP,
                evictsList = true,
                { raw, during -> coEvery { raw.applicationWriteDao.delete(APP_A) } answers { during() } },
                { it.applicationWriteDao.delete(APP_A) },
            ),
            WriteCase(
                "updateNameAndIcon",
                Scope.APP,
                evictsList = true,
                { raw, during -> coEvery { raw.applicationWriteDao.updateNameAndIcon(APP_A, any(), any()) } answers { during() } },
                { it.applicationWriteDao.updateNameAndIcon(APP_A, "name", "icon") },
            ),
        )

        private fun listOnlyWrites() = listOf(
            WriteCase(
                "updateLastUsed",
                Scope.LIST_ONLY,
                evictsList = true,
                { raw, during -> coEvery { raw.applicationWriteDao.updateLastUsed(APP_A, any()) } answers { during() } },
                { it.applicationWriteDao.updateLastUsed(APP_A, 1L) },
            ),
            WriteCase(
                "replaceSecretRaw",
                Scope.LIST_ONLY,
                evictsList = true,
                { raw, during -> coEvery { raw.applicationWriteDao.replaceSecretRaw(APP_A, any(), any()) } answers { returning(1, during) } },
                { it.applicationWriteDao.replaceSecretRaw(APP_A, "old", "new") },
            ),
            WriteCase(
                "replaceLocalKeyRaw",
                Scope.LIST_ONLY,
                evictsList = true,
                { raw, during -> coEvery { raw.applicationWriteDao.replaceLocalKeyRaw(APP_A, any(), any()) } answers { returning(1, during) } },
                { it.applicationWriteDao.replaceLocalKeyRaw(APP_A, "old", "new") },
            ),
        )

        private fun permissionInserts() = listOf(
            WriteCase(
                "insertPermissions",
                Scope.APP,
                evictsList = false,
                { raw, during -> coEvery { raw.permissionWriteDao.insertPermissions(any()) } answers { returning(listOf(1L), during) } },
                { it.permissionWriteDao.insertPermissions(listOf(grant(APP_A, accepted = false))) },
            ),
            WriteCase(
                "insertPermissions2",
                Scope.APP,
                evictsList = false,
                { raw, during -> coEvery { raw.permissionWriteDao.insertPermissions2(any()) } answers { returning(listOf(1L), during) } },
                { it.permissionWriteDao.insertPermissions2(listOf(grant(APP_A, accepted = false))) },
            ),
            WriteCase(
                "insertApplicationWithPermissions",
                Scope.APP,
                evictsList = true,
                { raw, during -> coEvery { raw.permissionWriteDao.insertApplicationWithPermissions(any()) } answers { during() } },
                { it.permissionWriteDao.insertApplicationWithPermissions(ApplicationWithPermissions(application(), mutableListOf())) },
            ),
        )

        private fun permissionDeletes() = listOf(
            WriteCase(
                "deletePermissions(key)",
                Scope.APP,
                evictsList = false,
                { raw, during -> coEvery { raw.permissionWriteDao.deletePermissions(APP_A) } answers { during() } },
                { it.permissionWriteDao.deletePermissions(APP_A) },
            ),
            WriteCase(
                "deletePermissions(key, type)",
                Scope.APP,
                evictsList = false,
                { raw, during -> coEvery { raw.permissionWriteDao.deletePermissions(APP_A, TYPE) } answers { during() } },
                { it.permissionWriteDao.deletePermissions(APP_A, TYPE) },
            ),
            WriteCase(
                "deletePermissions(key, type, kind)",
                Scope.APP,
                evictsList = false,
                { raw, during -> coEvery { raw.permissionWriteDao.deletePermissions(APP_A, TYPE, KIND) } answers { during() } },
                { it.permissionWriteDao.deletePermissions(APP_A, TYPE, KIND) },
            ),
            WriteCase(
                "deletePermissions(key, type, kind, relay)",
                Scope.APP,
                evictsList = false,
                { raw, during -> coEvery { raw.permissionWriteDao.deletePermissions(APP_A, TYPE, KIND, "wss://r") } answers { during() } },
                { it.permissionWriteDao.deletePermissions(APP_A, TYPE, KIND, "wss://r") },
            ),
            WriteCase(
                "deletePermissionsForKind",
                Scope.APP,
                evictsList = false,
                { raw, during -> coEvery { raw.permissionWriteDao.deletePermissionsForKind(APP_A, TYPE, KIND) } answers { during() } },
                { it.permissionWriteDao.deletePermissionsForKind(APP_A, TYPE, KIND) },
            ),
        )

        private fun maintenanceWrites() = listOf(
            WriteCase(
                "updateExpiredPermissions changing rows",
                Scope.ALL,
                evictsList = false,
                { raw, during -> every { raw.permissionMaintenanceDao.updateExpiredPermissions(any()) } answers { returning(1, during) } },
                { it.permissionMaintenanceDao.updateExpiredPermissions(1L) },
            ),
            WriteCase(
                "updateExpiredPermissions changing nothing",
                Scope.NONE,
                evictsList = false,
                { raw, during -> every { raw.permissionMaintenanceDao.updateExpiredPermissions(any()) } answers { returning(0, during) } },
                { it.permissionMaintenanceDao.updateExpiredPermissions(1L) },
            ),
            WriteCase(
                "deleteOldApplications deleting rows",
                Scope.ALL,
                evictsList = true,
                { raw, during -> coEvery { raw.permissionMaintenanceDao.deleteOldApplications(any()) } answers { returning(1, during) } },
                { it.permissionMaintenanceDao.deleteOldApplications(1L) },
            ),
            WriteCase(
                "deleteOldApplications deleting nothing",
                Scope.NONE,
                evictsList = false,
                { raw, during -> coEvery { raw.permissionMaintenanceDao.deleteOldApplications(any()) } answers { returning(0, during) } },
                { it.permissionMaintenanceDao.deleteOldApplications(1L) },
            ),
            WriteCase(
                "deletePermission",
                Scope.APP,
                evictsList = false,
                { raw, during -> coEvery { raw.permissionMaintenanceDao.deletePermission(any()) } answers { during() } },
                { it.permissionMaintenanceDao.deletePermission(grant(APP_A, accepted = true)) },
            ),
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases() = applicationRowWrites() + listOnlyWrites() + permissionInserts() + permissionDeletes() + maintenanceWrites()
    }
}
