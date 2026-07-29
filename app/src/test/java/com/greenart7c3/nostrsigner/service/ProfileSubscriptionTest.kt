package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.models.ProfileFetchInterval
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.utils.TimeUtils
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test

class ProfileSubscriptionTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sentSubIds = Collections.synchronizedList(mutableListOf<String>())
    private lateinit var client: NostrClient
    private lateinit var subscription: ProfileSubscription

    @Before
    fun setUp() {
        client = mockk(relaxed = true)
        every { client.subscribe(capture(sentSubIds), any()) } returns Unit

        installAmber(ProfileFetchInterval.ALWAYS)

        mockkObject(LocalPreferences)
        every { LocalPreferences.loadSettingsFromEncryptedStorage(any()) } returns AmberSettings()
        every { LocalPreferences.getUserRelays(any(), any()) } returns emptyList()
        every { LocalPreferences.getLastMetadataUpdate(any(), any()) } returns 0L
        every { LocalPreferences.getLastCheck(any(), any()) } returns 0L
        every { LocalPreferences.setLastCheck(any(), any(), any()) } just Runs
        every { LocalPreferences.setLastMetadataUpdate(any(), any(), any()) } just Runs

        subscription = ProfileSubscription(client, mockk(relaxed = true), scope)
    }

    @After
    fun tearDown() {
        unmockkObject(LocalPreferences)
        scope.cancel()
    }

    private fun installAmber(interval: ProfileFetchInterval) {
        val amber = mockk<Amber>(relaxed = true)
        every { amber.settings } returns AmberSettings(profileFetchInterval = interval)
        installAmberInstance(amber)
    }

    @Test
    fun `updateFilter subscribes to the user relay list when interval is ALWAYS`() = runBlocking {
        // updateFilter is a no-op on the offline flavor by design
        assumeFalse(BuildFlavorChecker.isOfflineFlavor())
        subscription.updateFilter(newTestAccount(scope))
        verify(exactly = 1) { client.subscribe(any(), any()) }
    }

    @Test
    fun `updateFilter does not subscribe when fetch interval is NEVER`() = runBlocking {
        assumeFalse(BuildFlavorChecker.isOfflineFlavor())
        installAmber(ProfileFetchInterval.NEVER)
        subscription.updateFilter(newTestAccount(scope))
        verify(exactly = 0) { client.subscribe(any(), any()) }
    }

    @Test
    fun `closeSub unsubscribes the account subscriptions`() = runBlocking {
        assumeFalse(BuildFlavorChecker.isOfflineFlavor())
        val account = newTestAccount(scope)
        subscription.updateFilter(account)
        subscription.closeSub(account)
        verify(exactly = 1) { client.unsubscribe(any()) }
    }

    /**
     * Regression test for the NegativeArraySizeException crash: updateFilters iterated
     * accounts.values while the UI thread added/removed accounts on a plain LinkedHashMap.
     * Uses a large tracked set and a throttled interval (no relay work) so the
     * iteration-vs-mutation race window is hit thousands of times.
     */
    @Test
    fun `updateFilters does not throw when accounts are added and removed concurrently`() {
        installAmber(ProfileFetchInterval.ONE_HOUR)
        every { LocalPreferences.getLastCheck(any(), any()) } returns TimeUtils.now()

        val tracked = Collections.synchronizedList(mutableListOf<Account>())
        runBlocking {
            repeat(200) {
                val account = newTestAccount(scope)
                tracked.add(account)
                subscription.updateFilter(account)
            }
        }

        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val threads = mutableListOf<Thread>()

        repeat(3) {
            threads += Thread {
                repeat(100) {
                    try {
                        runBlocking { subscription.updateFilters() }
                    } catch (e: Throwable) {
                        errors.add(e)
                    }
                }
            }
        }
        // Churn threads recycle the existing pool: closeSub + updateFilter on the same
        // accounts gives endless structural map mutations without allocating new objects.
        repeat(2) {
            threads += Thread {
                repeat(400) {
                    try {
                        tracked.randomOrNull()?.let { account ->
                            subscription.closeSub(account)
                            runBlocking { subscription.updateFilter(account) }
                        }
                    } catch (e: Throwable) {
                        errors.add(e)
                    }
                }
            }
        }
        repeat(2) {
            threads += Thread {
                repeat(400) {
                    try {
                        tracked.randomOrNull()?.let { subscription.closeSub(it) }
                    } catch (e: Throwable) {
                        errors.add(e)
                    }
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertTrue(
            "Expected no exceptions, got: ${errors.firstOrNull()?.stackTraceToString()}",
            errors.isEmpty(),
        )
    }

    /**
     * EOSE handling looks up subscription ids in the same maps that updateFilter/closeSub
     * mutate; these run on relay I/O threads and coroutines at the same time.
     */
    @Test
    fun `onIncomingMessage does not throw when subscriptions change concurrently`() {
        val relay = mockk<IRelayClient>()
        every { relay.url } returns AmberSettings().defaultProfileRelays.first()
        val event = mockk<Event>()
        every { event.kind } returns -1

        val accounts = (1..8).map { newTestAccount(scope) }
        runBlocking { accounts.forEach { subscription.updateFilter(it) } }

        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val threads = mutableListOf<Thread>()

        repeat(3) {
            threads += Thread {
                repeat(200) {
                    try {
                        val subId = sentSubIds.toList().randomOrNull() ?: return@repeat
                        subscription.onIncomingMessage(relay, "", EoseMessage(subId))
                        subscription.onIncomingMessage(relay, "", EventMessage(subId, event))
                    } catch (e: Throwable) {
                        errors.add(e)
                    }
                }
            }
        }
        threads += Thread {
            repeat(100) {
                try {
                    runBlocking { subscription.updateFilters() }
                } catch (e: Throwable) {
                    errors.add(e)
                }
            }
        }
        threads += Thread {
            repeat(50) {
                try {
                    subscription.closeSub(accounts.random())
                } catch (e: Throwable) {
                    errors.add(e)
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertTrue(
            "Expected no exceptions, got: ${errors.firstOrNull()?.stackTraceToString()}",
            errors.isEmpty(),
        )
    }
}
