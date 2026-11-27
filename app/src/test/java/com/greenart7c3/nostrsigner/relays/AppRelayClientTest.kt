package com.greenart7c3.nostrsigner.relays

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.common.relays.RelayHealthTracker
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.models.ThemeMode
import com.greenart7c3.nostrsigner.models.TorMode
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.greenart7c3.nostrsigner.service.TorManager
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test

class AppRelayClientTest {
    private val publicRelay = NormalizedRelayUrl("wss://relay.example.com/")
    private val localRelay = NormalizedRelayUrl("ws://127.0.0.1:7777/")

    private lateinit var scope: CoroutineScope
    private lateinit var appContext: Context
    private lateinit var settingsStore: SettingsStore
    private lateinit var torRunning: MutableStateFlow<Boolean>
    private lateinit var torPort: MutableStateFlow<Int>
    private var settingsLoaded = true
    private var subscriptionsPresent = false
    private var relayClient: AppRelayClient? = null

    @Before
    fun setUp() {
        RelayHealthTracker.reset()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        appContext = mockk(relaxed = true)
        settingsLoaded = true
        subscriptionsPresent = false
        torRunning = MutableStateFlow(false)
        torPort = MutableStateFlow(0)

        settingsStore = object : SettingsStore {
            override var settings = AmberSettings(torMode = TorMode.DISABLED)
            override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        }

        mockkObject(AmberLog, TorManager, HttpClientManager)
        every { AmberLog.d(any(), any()) } returns Unit
        every { AmberLog.d(any(), any(), any()) } returns Unit
        every { AmberLog.e(any(), any()) } returns Unit
        every { AmberLog.e(any(), any(), any()) } returns Unit
        every { TorManager.isRunning } returns torRunning
        every { TorManager.socksPort } returns torPort
        every { HttpClientManager.getHttpClient(any()) } returns mockk(relaxed = true)
        every { HttpClientManager.getLocalHttpClient() } returns mockk(relaxed = true)

        mockkStatic(ContextCompat::class, android.util.Log::class)
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED
        every { appContext.getSystemService(Context.NOTIFICATION_SERVICE) } returns mockk<NotificationManager>(relaxed = true)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        relayClient?.client?.close()
        scope.cancel()
        RelayHealthTracker.reset()
        unmockkStatic(ContextCompat::class, android.util.Log::class)
        unmockkObject(AmberLog, TorManager, HttpClientManager)
    }

    private fun buildClient(): AppRelayClient = AppRelayClient(
        appContext = appContext,
        settingsStore = settingsStore,
        isSettingsLoaded = { settingsLoaded },
        scope = scope,
        updateSubscriptions = {
            val client = relayClient?.client ?: return@AppRelayClient
            if (subscriptionsPresent) {
                client.subscribe("bunker", mapOf(localRelay to listOf(Filter(kinds = listOf(24133)))))
            } else {
                client.unsubscribe("bunker")
            }
        },
        closeSubscriptions = {
            relayClient?.client?.unsubscribe("bunker")
        },
    ).also { relayClient = it }

    @Test
    fun `canConnect gates public relays until settings load and built-in Tor is ready`() {
        val rc = buildClient()

        settingsLoaded = false
        assertFalse(rc.factory.canConnect(publicRelay))
        assertTrue(rc.factory.canConnect(localRelay))

        settingsLoaded = true
        settingsStore.settings = AmberSettings(torMode = TorMode.BUILTIN)
        assertFalse(rc.factory.canConnect(publicRelay))
        assertTrue(rc.factory.canConnect(localRelay))

        torRunning.value = true
        torPort.value = 0
        assertFalse(rc.factory.canConnect(publicRelay))

        torPort.value = 41234
        assertTrue(rc.factory.canConnect(publicRelay))

        torRunning.value = false
        torPort.value = 0
        settingsStore.settings = AmberSettings(torMode = TorMode.ORBOT)
        assertTrue(rc.factory.canConnect(publicRelay))

        settingsStore.settings = AmberSettings(torMode = TorMode.DISABLED)
        assertTrue(rc.factory.canConnect(publicRelay))
    }

    @Test
    fun `checkForNewRelaysAndUpdateAllFilters keeps client idle without active subscriptions`() = runBlocking {
        assumeFalse(BuildFlavorChecker.isOfflineFlavor())
        val rc = buildClient()
        rc.client.disconnect()
        assertFalse(rc.client.isActive())

        subscriptionsPresent = false
        rc.checkForNewRelaysAndUpdateAllFilters()
        assertFalse(rc.client.isActive())

        subscriptionsPresent = true
        rc.checkForNewRelaysAndUpdateAllFilters()
        assertTrue(rc.client.isActive())

        subscriptionsPresent = false
        rc.checkForNewRelaysAndUpdateAllFilters()
        assertFalse(rc.client.isActive())
    }

    @Test
    fun `dropping the last subscription disconnects the client automatically once pool empties`() = runBlocking {
        val rc = buildClient()
        rc.client.subscribe("temp", mapOf(localRelay to listOf(Filter(kinds = listOf(1)))))
        withTimeout(3_000) {
            while (rc.client.availableRelaysFlow().value.isEmpty()) delay(20)
        }
        assertTrue(rc.client.isActive())

        rc.client.unsubscribe("temp")
        withTimeout(3_000) {
            while (rc.client.isActive()) delay(20)
        }
        assertFalse(rc.client.isActive())
    }

    @Test
    fun `reconnect clears dead relay tracking`() {
        val rc = buildClient()
        repeat(11) { RelayHealthTracker.recordFailure(publicRelay) }
        assertTrue(RelayHealthTracker.isDead(publicRelay))

        rc.reconnect()

        assertFalse(RelayHealthTracker.isDead(publicRelay))
    }
}
