package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.vitorpamplona.quartz.utils.TimeUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApplicationBackupRestoreTest {
    private val dao = mockk<ApplicationDao>(relaxed = true)

    @Before
    fun setUp() {
        val amber = mockk<Amber>(relaxed = true)
        every { amber.dao(any()) } returns dao
        installAmberInstance(amber)
    }

    private fun app(key: String, deleteAfter: Long = 0) = BackupApplication(
        key = key,
        name = key,
        relays = emptyList(),
        url = "",
        icon = "",
        description = "",
        pubKey = "",
        isConnected = true,
        secret = "",
        useSecret = false,
        signPolicy = 1,
        closeApplication = true,
        deleteAfter = deleteAfter,
        lastUsed = 0,
        permissions = emptyList(),
    )

    @Test
    fun `an app is expired only once a set deleteAfter has passed`() {
        assertTrue(ApplicationBackup.isExpired(app("a", deleteAfter = 1_500), now = 2_000))
        assertFalse(ApplicationBackup.isExpired(app("a", deleteAfter = 3_000), now = 2_000))
        assertFalse(ApplicationBackup.isExpired(app("a", deleteAfter = 0), now = 2_000))
    }

    @Test
    fun `restoreFromPayload skips expired apps`() = runBlocking {
        val now = TimeUtils.now()
        val payload = BackupPayload(
            applications = listOf(app("expired", deleteAfter = now - 60), app("pending", deleteAfter = now + 3_600), app("forever")),
        )
        val written = mutableListOf<ApplicationWithPermissions>()
        coEvery { dao.insertApplicationWithPermissions(capture(written)) } returns Unit

        val result = ApplicationBackup.restoreFromPayload("npub1test", payload)

        assertEquals(RestoreResult.Success(2, 0), result)
        assertEquals(listOf("pending", "forever"), written.map { it.application.key })
    }
}
