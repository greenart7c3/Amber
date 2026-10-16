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
    private fun withDao(block: suspend (ApplicationDao) -> Unit) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.dao()
            dao.insertApplicationRaw(ApplicationEntity.empty().copy(key = "caller"))
            block(dao)
        } finally {
            database.close()
        }
    }

    @Test
    fun activeGrantsAndDenialsSurviveCleanupUntilTheirExpiry() = withDao { dao ->
        dao.insertPermissionsRaw(
            listOf(
                ApplicationPermissionsEntity(1, "caller", "SIGN_EVENT", 1, true, 7, 200, 0),
                ApplicationPermissionsEntity(2, "caller", "SIGN_EVENT", 2, false, 7, 0, 200),
            ),
        )
        assertEquals(0, dao.updateExpiredPermissions(100))
        assertEquals(200L, dao.getPermission("caller", "SIGN_EVENT", 1)?.acceptUntil)
        assertEquals(200L, dao.getPermission("caller", "SIGN_EVENT", 2)?.rejectUntil)
        assertEquals(2, dao.updateExpiredPermissions(200))
        assertEquals(0L, dao.getPermission("caller", "SIGN_EVENT", 1)?.acceptUntil)
        assertEquals(0L, dao.getPermission("caller", "SIGN_EVENT", 2)?.rejectUntil)
        // Already reset rows are not counted again.
        assertEquals(0, dao.updateExpiredPermissions(300))
    }

    @Test
    fun alwaysRowsAreKept() = withDao { dao ->
        dao.insertPermissionsRaw(
            listOf(ApplicationPermissionsEntity(1, "caller", "SIGN_EVENT", 1, true, 4, Long.MAX_VALUE / 1000, 0)),
        )
        assertEquals(0, dao.updateExpiredPermissions(100))
        assertEquals(4, dao.getPermission("caller", "SIGN_EVENT", 1)?.rememberType)
    }

    @Test
    fun staleTimestampsOnTheUnselectedSideAreCleared() = withDao { dao ->
        dao.insertPermissionsRaw(
            listOf(
                // Denial with a leftover future acceptUntil: must not stay acceptable-looking.
                ApplicationPermissionsEntity(1, "caller", "SIGN_EVENT", 1, false, 7, 500, 0),
                // Grant with a leftover future rejectUntil.
                ApplicationPermissionsEntity(2, "caller", "SIGN_EVENT", 2, true, 7, 0, 500),
            ),
        )
        assertEquals(2, dao.updateExpiredPermissions(100))
        listOf(1, 2).forEach { kind ->
            val permission = dao.getPermission("caller", "SIGN_EVENT", kind)
            assertEquals(0L, permission?.acceptUntil)
            assertEquals(0L, permission?.rejectUntil)
            assertEquals(0, permission?.rememberType)
        }
    }

    @Test
    fun askRowsLoseTheirLeftoverDuration() = withDao { dao ->
        dao.insertPermissionsRaw(
            listOf(ApplicationPermissionsEntity(1, "caller", "SIGN_EVENT", 1, true, 7, 0, 0)),
        )
        assertEquals(1, dao.updateExpiredPermissions(100))
        assertEquals(0, dao.getPermission("caller", "SIGN_EVENT", 1)?.rememberType)
    }
}
