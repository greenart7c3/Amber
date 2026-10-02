package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AccountStore
import com.greenart7c3.nostrsigner.desktop.core.AppRecord
import com.greenart7c3.nostrsigner.desktop.core.AppWithPermissions
import com.greenart7c3.nostrsigner.desktop.core.DeleteAfterType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test

class DeleteAfterTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun isolateDataDir() {
            val tmp = File.createTempFile("amber-test", "").apply {
                delete()
                mkdirs()
                deleteOnExit()
            }
            System.setProperty("user.home", tmp.absolutePath)
        }
    }

    @Test
    fun deleteAtMatchesTheAndroidDurations() {
        val now = 1_000_000L
        assertEquals(0L, DeleteAfterType.NEVER.deleteAt(now))
        assertEquals(now + 300, DeleteAfterType.FIVE_MINUTES.deleteAt(now))
        assertEquals(now + 600, DeleteAfterType.TEN_MINUTES.deleteAt(now))
        assertEquals(now + 3600, DeleteAfterType.ONE_HOUR.deleteAt(now))
        assertEquals(now + 86400, DeleteAfterType.ONE_DAY.deleteAt(now))
        assertEquals(now + 604800, DeleteAfterType.ONE_WEEK.deleteAt(now))
    }

    @Test
    fun onlyExpiredConnectionsAreDeleted() {
        val store = AccountStore("npub1deleteaftertest")
        val now = 2_000_000L
        store.upsert(AppWithPermissions(AppRecord(key = "never", deleteAfter = 0L)))
        store.upsert(AppWithPermissions(AppRecord(key = "expired", deleteAfter = now - 1)))
        store.upsert(AppWithPermissions(AppRecord(key = "future", deleteAfter = now + 60)))

        assertEquals(1, store.deleteExpiredApps(now))
        assertEquals(setOf("never", "future"), store.apps.value.map { it.app.key }.toSet())
        assertEquals(0, store.deleteExpiredApps(now))
    }
}
