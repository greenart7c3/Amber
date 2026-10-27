package com.greenart7c3.nostrsigner.service

import androidx.collection.LruCache
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.CachingApplicationDao
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test

/**
 * An unauthenticated sender picks a NIP-46 event's created_at; a future value
 * must never become the subscription `since`, or relays would filter out every
 * legitimate request until the clock catches up.
 */
class NotificationSinceClampTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() {
        scope.cancel()
        unmockkObject(LocalPreferences)
    }

    @Test
    fun `future-dated cached event does not push since past now`() = runBlocking {
        // The offline flavor never subscribes to relays.
        assumeFalse(BuildFlavorChecker.isOfflineFlavor())
        val client = mockk<NostrClient>(relaxed = true)
        val captured = mutableListOf<Map<NormalizedRelayUrl, List<Filter>>>()
        every { client.subscribe(any(), capture(captured)) } returns Unit
        val dao = mockk<ApplicationDao>()
        val db = mockk<AppDatabase>()
        every { db.dao() } returns dao
        val amber = mockk<Amber>(relaxed = true)
        every { amber.getDatabase(any()) } returns db
        every { amber.dao(any()) } answers { CachingApplicationDao(dao) }
        val cache = LruCache<String, Long>(512)
        every { amber.notificationCache } returns cache
        installAmberInstance(amber)
        mockkObject(LocalPreferences)
        val account = newTestAccount(scope)
        coEvery { LocalPreferences.allAccounts(any()) } returns listOf(account)
        coEvery { dao.getAll(account.hexKey) } returns listOf(
            ApplicationEntity.empty().copy(key = "c1", relays = listOf(NormalizedRelayUrl("wss://r1.example.com/")), localKey = "ab".repeat(32)),
        )
        every { dao.getAllRelayLists() } returns emptyList()

        val before = TimeUtils.now()
        cache.put("legit", before - 30)
        cache.put("attacker", before + 300)
        NotificationSubscription(client, mockk(relaxed = true)).updateFilter()
        val after = TimeUtils.now()

        val sinces = captured.flatMap { subs -> subs.values.flatten().mapNotNull { it.since } }
        assertTrue("since values: $sinces", sinces.isNotEmpty() && sinces.all { it <= after })
    }
}
