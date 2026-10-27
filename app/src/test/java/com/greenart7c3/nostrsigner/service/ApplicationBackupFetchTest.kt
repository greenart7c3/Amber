package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.vitorpamplona.quartz.nip01Core.core.Event
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ApplicationBackupFetchTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val dao = mockk<ApplicationDao>(relaxed = true)

    @Before
    fun setUp() {
        val amber = mockk<Amber>(relaxed = true)
        every { amber.dao(any()) } returns dao
        installAmberInstance(amber)

        mockkObject(ApplicationBackup)
        coEvery { ApplicationBackup.resolveReadRelays(any()) } returns setOf(mockk())
    }

    @After
    fun tearDown() {
        unmockkObject(ApplicationBackup)
    }

    @Test
    fun `no backup event is reported as not found`() = runBlocking {
        coEvery { ApplicationBackup.fetchLatestBackupEvent(any(), any()) } returns null

        val result = ApplicationBackup.fetchLatestPayload(newTestAccount(scope))

        assertEquals(BackupFetchResult.NoBackupFound, result)
    }

    @Test
    fun `an undecryptable backup is reported as failed`() = runBlocking {
        coEvery { ApplicationBackup.fetchLatestBackupEvent(any(), any()) } returns mockk<Event>()
        coEvery { ApplicationBackup.decryptPayload(any(), any()) } returns null

        val result = ApplicationBackup.fetchLatestPayload(newTestAccount(scope))

        assertEquals(BackupFetchResult.Failed("Failed to decrypt backup"), result)
    }

    @Test
    fun `a backup is returned for review without being restored`() = runBlocking {
        val payload = BackupPayload(applications = emptyList())
        coEvery { ApplicationBackup.fetchLatestBackupEvent(any(), any()) } returns mockk<Event>()
        coEvery { ApplicationBackup.decryptPayload(any(), any()) } returns payload

        val result = ApplicationBackup.fetchLatestPayload(newTestAccount(scope))

        assertEquals(BackupFetchResult.Found(payload), result)
        coVerify(exactly = 0) { ApplicationBackup.restoreFromPayload(any(), any()) }
        coVerify(exactly = 0) { dao.insertApplicationWithPermissions(any()) }
    }
}
