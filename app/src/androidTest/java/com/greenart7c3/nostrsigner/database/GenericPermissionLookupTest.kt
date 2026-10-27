package com.greenart7c3.nostrsigner.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * getPermission(key, type) had no kind/relay filter and no ORDER BY: a kind-
 * or relay-scoped row could answer for the generic grant, and among NULL-kind
 * duplicates (which the unique index allows) SQLite's row order decided.
 */
@RunWith(AndroidJUnit4::class)
class GenericPermissionLookupTest {
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

    private fun row(id: Int, type: String, accept: Boolean, kind: Int? = null, relay: String = "") = ApplicationPermissionsEntity(
        id = id,
        pkKey = "caller",
        type = type,
        kind = kind,
        acceptable = accept,
        rememberType = 4,
        acceptUntil = if (accept) Long.MAX_VALUE / 1000 else 0,
        rejectUntil = if (accept) 0 else Long.MAX_VALUE / 1000,
        relay = relay,
    )

    @Test
    fun scopedRowsNeverAnswerForTheGenericGrant() = withDao { dao ->
        dao.insertPermissionsRaw(
            listOf(
                row(1, "NIP44_DECRYPT", accept = true, kind = 4),
                row(2, "PING", accept = true, relay = "relay.example.com"),
            ),
        )
        assertNull(dao.getPermission("caller", "NIP44_DECRYPT"))
        assertNull(dao.getPermission("caller", "PING"))
    }

    @Test
    fun anExplicitRejectionWinsOverADuplicateAccept() = withDao { dao ->
        dao.insertPermissionsRaw(listOf(row(1, "GET_PUBLIC_KEY", accept = true), row(2, "GET_PUBLIC_KEY", accept = false), row(3, "GET_PUBLIC_KEY", accept = true)))
        assertEquals(2, dao.getPermission("caller", "GET_PUBLIC_KEY")!!.id)
    }

    @Test
    fun theNewestOfDuplicateAcceptsWins() = withDao { dao ->
        dao.insertPermissionsRaw(listOf(row(1, "SIGN_PSBT", accept = true), row(5, "SIGN_PSBT", accept = true)))
        assertEquals(5, dao.getPermission("caller", "SIGN_PSBT")!!.id)
    }
}
