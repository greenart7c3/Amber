package com.greenart7c3.nostrsigner.service

import android.content.Context
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.data.RelayClient
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.models.ThemeMode
import com.greenart7c3.nostrsigner.models.TorMode
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test

class TorStartupTest {
    private lateinit var scope: CoroutineScope
    private lateinit var context: Context
    private lateinit var settingsStore: SettingsStore
    private lateinit var nostrClient: NostrClient
    private lateinit var relayClient: RelayClient
    private lateinit var isRunning: MutableStateFlow<Boolean>
    private lateinit var status: MutableStateFlow<TorStatus>

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        context = mockk(relaxed = true)
        settingsStore = object : SettingsStore {
            override var settings = AmberSettings(torMode = TorMode.DISABLED)
            override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        }
        nostrClient = mockk(relaxed = true)
        relayClient = mockk(relaxed = true)
        every { relayClient.client } returns nostrClient

        isRunning = MutableStateFlow(false)
        status = MutableStateFlow(TorStatus.Stopped)

        mockkObject(AmberLog, TorManager)
        every { AmberLog.d(any(), any()) } returns Unit
        every { AmberLog.e(any(), any()) } returns Unit
        every { TorManager.isRunning } returns isRunning
        every { TorManager.status } returns status
        every { TorManager.start(any(), any()) } returns Unit
        every { TorManager.stop() } returns Unit
        every { TorManager.showFailed() } returns Unit
        every { TorManager.showRetrying() } returns Unit
    }

    @After
    fun tearDown() {
        scope.cancel()
        unmockkObject(AmberLog, TorManager)
    }

    @Test
    fun `start with Tor disabled still registers observer for later switch to built-in Tor`() = runBlocking {
        assumeFalse(BuildFlavorChecker.isOfflineFlavor())
        val startup = TorStartup(context, settingsStore, relayClient, scope)

        assertTrue(startup.start())
        verify(exactly = 0) { TorManager.start(any(), any()) }

        settingsStore.settings = AmberSettings(torMode = TorMode.BUILTIN)
        isRunning.value = true

        coVerify(timeout = 2_000) { nostrClient.resetBackoff() }
        coVerify(timeout = 2_000) { relayClient.checkForNewRelaysAndUpdateAllFilters(true) }
    }

    @Test
    fun `waitIfNeeded suspends while built-in Tor is not running and resumes when it starts`() = runBlocking {
        assumeFalse(BuildFlavorChecker.isOfflineFlavor())
        settingsStore.settings = AmberSettings(torMode = TorMode.BUILTIN)
        val startup = TorStartup(context, settingsStore, relayClient, scope)

        var resumed = false
        val waiter = launch {
            startup.waitIfNeeded()
            resumed = true
        }
        kotlinx.coroutines.delay(50)
        assertFalse(resumed)

        isRunning.value = true
        waiter.join()
        assertTrue(resumed)
    }
}
