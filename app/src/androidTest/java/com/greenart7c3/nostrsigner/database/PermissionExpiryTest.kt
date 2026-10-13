package com.greenart7c3.nostrsigner.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionExpiryTest {
    @Test
    fun activeGrantsAndDenialsSurviveCleanupUntilTheirExpiry() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.dao()
            dao.insertApplicationRaw(ApplicationEntity.empty().copy(key = "caller"))
            dao.insertPermissionsRaw(
                listOf(
                    ApplicationPermissionsEntity(1, "caller", "SIGN_EVENT", 1, true, 7, 200, 0),
                    ApplicationPermissionsEntity(2, "caller", "SIGN_EVENT", 2, false, 7, 0, 200),
                ),
            )
            dao.updateExpiredPermissions(100)
            assertEquals(200L, dao.getPermission("caller", "SIGN_EVENT", 1)?.acceptUntil)
            assertEquals(200L, dao.getPermission("caller", "SIGN_EVENT", 2)?.rejectUntil)
            dao.updateExpiredPermissions(200)
            assertEquals(0L, dao.getPermission("caller", "SIGN_EVENT", 1)?.acceptUntil)
            assertEquals(0L, dao.getPermission("caller", "SIGN_EVENT", 2)?.rejectUntil)
        } finally {
            database.close()
        }
    }
}
