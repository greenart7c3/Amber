package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.database.ApplicationDaos
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.relaxedApplicationDaos
import com.vitorpamplona.quartz.utils.TimeUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F-13: a connection whose user-chosen `deleteAfter` lifetime has passed is
 * authorization-dead immediately — not only after the daily cleanup deletes
 * its row. The caching read layer, through which every request-time lookup
 * flows, must treat the expired row as absent.
 */
class ExpiredConnectionAuthTest {
    private val raw = relaxedApplicationDaos()
    private val daos = ApplicationDaos.cached(raw)
    private val delegate = raw.permissionLookupDao
    private val dao = daos.permissionLookupDao

    private fun entity(key: String, deleteAfter: Long) = ApplicationEntity(
        key = key,
        name = "app-$key",
        relays = emptyList(),
        url = "",
        icon = "",
        description = "",
        pubKey = "account-hex",
        isConnected = true,
        secret = "",
        useSecret = false,
        signPolicy = 2,
        closeApplication = true,
        deleteAfter = deleteAfter,
        lastUsed = 1L,
        localKey = "",
    )

    private fun wrapper(entity: ApplicationEntity) = ApplicationWithPermissions(entity, mutableListOf())

    @Test
    fun `sign policy of an expired connection reads as unknown`() {
        every { delegate.getDeleteAfter("expired") } returns TimeUtils.now() - 60
        every { delegate.getSignPolicy("expired") } returns 2

        assertNull(dao.getSignPolicy("expired"))
        // The gate is consulted on every read, so a cached pre-expiry answer
        // is never served even when the delegate still returns the row.
        assertNull(dao.getSignPolicy("expired"))
    }

    @Test
    fun `permissions of an expired connection read as unknown`() {
        every { delegate.getDeleteAfter("expired") } returns TimeUtils.now() - 60
        val perm = mockk<com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity>(relaxed = true)
        every { delegate.getPermission("expired", "sign_event") } returns perm
        every { delegate.getPermission("expired", "sign_event", 9734) } returns perm
        every { delegate.getPermissionAllKinds("expired", "sign_event") } returns perm
        every { delegate.getPermissionForRelay("expired", "sign_event", 9734, "wss://r") } returns perm
        every { delegate.getWildcardRelayPermission("expired", "sign_event", 9734) } returns perm

        assertNull(dao.getPermission("expired", "sign_event"))
        assertNull(dao.getPermission("expired", "sign_event", 9734))
        assertNull(dao.getPermissionAllKinds("expired", "sign_event"))
        assertNull(dao.getPermissionForRelay("expired", "sign_event", 9734, "wss://r"))
        assertNull(dao.getWildcardRelayPermission("expired", "sign_event", 9734))
    }

    @Test
    fun `a live connection keeps answering`() {
        every { delegate.getDeleteAfter("live") } returns 0L
        every { delegate.getDeleteAfter("future") } returns TimeUtils.now() + 3_600
        every { delegate.getSignPolicy("live") } returns 2
        every { delegate.getSignPolicy("future") } returns 1

        assertEquals(2, dao.getSignPolicy("live"))
        assertEquals(1, dao.getSignPolicy("future"))
    }

    @Test
    fun `an unknown app is unaffected by the expiry gate`() {
        every { delegate.getDeleteAfter("ghost") } returns null
        every { delegate.getSignPolicy("ghost") } returns 2

        assertEquals(2, dao.getSignPolicy("ghost"))
    }

    @Test
    fun `getAll drops expired rows and caches the filtered list`() = runBlocking {
        val expired = entity("expired", TimeUtils.now() - 60)
        val live = entity("live", 0L)
        coEvery { raw.applicationDao.getAll("account-hex") } returns listOf(expired, live)

        assertEquals(listOf(live), daos.applicationDao.getAll("account-hex"))
        // Cached value must be the filtered one, not a re-read of the delegate.
        assertEquals(listOf(live), daos.applicationDao.getAll("account-hex"))
        coVerify(exactly = 1) { raw.applicationDao.getAll("account-hex") }
    }

    @Test
    fun `getAllWithLocalKey drops expired rows`() = runBlocking {
        val expired = entity("expired", TimeUtils.now() - 60)
        val live = entity("live", TimeUtils.now() + 60)
        coEvery { raw.applicationListDao.getAllWithLocalKey("account-hex") } returns listOf(expired, live)

        assertEquals(listOf(live), daos.applicationListDao.getAllWithLocalKey("account-hex"))
    }

    @Test
    fun `getBySecret of an expired connection reads as unknown`() = runBlocking {
        coEvery { raw.applicationDao.getBySecret("secret") } returns wrapper(entity("expired", TimeUtils.now() - 60))

        assertNull(daos.applicationDao.getBySecret("secret"))
    }

    @Test
    fun `getBySecret of a live connection passes through`() = runBlocking {
        val live = wrapper(entity("live", 0L))
        coEvery { raw.applicationDao.getBySecret("secret") } returns live

        assertEquals(live, daos.applicationDao.getBySecret("secret"))
    }

    @Test
    fun `repeated lookups on a live connection read deleteAfter once`() {
        val deleteAfter = TimeUtils.now() + 3_600
        every { delegate.getDeleteAfter("live") } returns deleteAfter
        every { delegate.getSignPolicy("live") } returns 2
        every { delegate.getPermission("live", "sign_event", 1) } returns null

        repeat(3) {
            assertEquals(2, dao.getSignPolicy("live"))
            assertNull(dao.getPermission("live", "sign_event", 1))
        }
        assertEquals(deleteAfter, dao.getDeleteAfter("live"))

        verify(exactly = 1) { delegate.getDeleteAfter("live") }
        verify(exactly = 1) { delegate.getSignPolicy("live") }
    }

    @Test
    fun `a cached live connection expires once the clock passes deleteAfter`() {
        mockkObject(TimeUtils)
        try {
            every { TimeUtils.now() } returns 1_000L
            every { delegate.getDeleteAfter("live") } returns 1_100L
            every { delegate.getSignPolicy("live") } returns 2
            assertEquals(2, dao.getSignPolicy("live"))

            every { TimeUtils.now() } returns 1_099L
            assertEquals(2, dao.getSignPolicy("live"))

            every { TimeUtils.now() } returns 1_100L
            assertNull(dao.getSignPolicy("live"))
            assertNull(dao.getPermission("live", "sign_event"))

            verify(exactly = 1) { delegate.getDeleteAfter("live") }
        } finally {
            unmockkObject(TimeUtils)
        }
    }

    @Test
    fun `re-inserting a connection with a past deleteAfter expires it on the next lookup`() = runBlocking {
        every { delegate.getDeleteAfter("app") } returns 0L
        every { delegate.getSignPolicy("app") } returns 2
        assertEquals(2, dao.getSignPolicy("app"))

        val past = TimeUtils.now() - 60
        every { delegate.getDeleteAfter("app") } returns past
        daos.applicationWriteDao.insertApplication(entity("app", past))

        assertNull(dao.getSignPolicy("app"))
        verify(exactly = 2) { delegate.getDeleteAfter("app") }
    }

    @Test
    fun `saving a connection with its permissions refreshes the cached deleteAfter`() = runBlocking {
        every { delegate.getDeleteAfter("app") } returns 0L
        every { delegate.getSignPolicy("app") } returns 2
        assertEquals(2, dao.getSignPolicy("app"))

        val past = TimeUtils.now() - 60
        every { delegate.getDeleteAfter("app") } returns past
        daos.permissionWriteDao.insertApplicationWithPermissions(wrapper(entity("app", past)))

        assertNull(dao.getSignPolicy("app"))
        verify(exactly = 2) { delegate.getDeleteAfter("app") }
    }

    private fun assertWriteEvictsDeleteAfter(write: suspend () -> Unit) = runBlocking {
        every { delegate.getDeleteAfter("app") } returns 0L
        every { delegate.getSignPolicy("app") } returns 2
        dao.getSignPolicy("app")

        write()

        every { delegate.getDeleteAfter("app") } returns null
        every { delegate.getSignPolicy("app") } returns null
        assertNull(dao.getDeleteAfter("app"))
        assertNull(dao.getSignPolicy("app"))
        verify(exactly = 2) { delegate.getDeleteAfter("app") }
    }

    @Test
    fun `deleting a connection by key evicts its cached deleteAfter`() = assertWriteEvictsDeleteAfter {
        daos.applicationWriteDao.delete("app")
    }

    @Test
    fun `deleting a connection by entity evicts its cached deleteAfter`() = assertWriteEvictsDeleteAfter {
        daos.applicationWriteDao.delete(entity("app", 0L))
    }

    @Test
    fun `deleteOldApplications that removes rows evicts every cached deleteAfter`() = assertWriteEvictsDeleteAfter {
        coEvery { raw.permissionMaintenanceDao.deleteOldApplications(any()) } returns 1
        daos.permissionMaintenanceDao.deleteOldApplications(TimeUtils.now())
    }

    @Test
    fun `isExpired predicate boundaries`() {
        assertFalse(entity("a", 0L).isExpired())
        assertTrue(entity("a", TimeUtils.now() - 1).isExpired())
        assertTrue(entity("a", TimeUtils.now()).isExpired())
        assertFalse(entity("a", TimeUtils.now() + 1).isExpired())
    }
}
