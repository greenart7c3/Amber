package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.AmberLog
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TorManagerLifecycleTest {
    private val defaultDestroy = TorManager.destroyArtiClient
    private val defaultCheckBootstrapped = TorManager.checkBootstrapped
    private val defaultProgress = TorManager.readBootstrapProgressPermille
    private val defaultTimeout = TorManager.bootstrapTimeoutMs
    private val defaultPoll = TorManager.bootstrapPollMs
    private val destroyCalls = AtomicInteger(0)

    @Before
    fun setUp() {
        mockkObject(AmberLog)
        every { AmberLog.d(any(), any()) } returns Unit
        every { AmberLog.i(any(), any()) } returns Unit
        every { AmberLog.e(any(), any()) } returns Unit
        every { AmberLog.e(any(), any(), any()) } returns Unit

        TorManager.destroyArtiClient = { destroyCalls.incrementAndGet() }
        TorManager.stop()
        destroyCalls.set(0)
    }

    @After
    fun tearDown() {
        TorManager.destroyArtiClient = {}
        TorManager.stop()
        TorManager.destroyArtiClient = defaultDestroy
        TorManager.checkBootstrapped = defaultCheckBootstrapped
        TorManager.readBootstrapProgressPermille = defaultProgress
        TorManager.bootstrapTimeoutMs = defaultTimeout
        TorManager.bootstrapPollMs = defaultPoll
        unmockkObject(AmberLog)
    }

    @Test
    fun `stop invokes destroyArtiClient and sets status to Stopped`() {
        TorManager.showFailed()

        TorManager.stop()

        assertEquals(1, destroyCalls.get())
        assertFalse(TorManager.isRunning.value)
        assertEquals(TorStatus.Stopped, TorManager.status.value)
    }

    @Test
    fun `fail invokes destroyArtiClient and sets status to Failed`() {
        val failMethod = TorManager::class.java.getDeclaredMethod("fail", String::class.java).apply { isAccessible = true }
        failMethod.invoke(TorManager, "boom")

        assertEquals(1, destroyCalls.get())
        assertFalse(TorManager.isRunning.value)
        assertEquals(TorStatus.Failed("boom"), TorManager.status.value)
    }

    @Test
    fun `showFailed updates status to Failed`() {
        TorManager.showFailed()

        assertEquals(TorStatus.Failed(null), TorManager.status.value)
    }

    @Test
    fun `reportConnectedOnceBootstrapped exits on timeout, destroys Arti, and sets Failed`() = runBlocking {
        TorManager.checkBootstrapped = { 0 }
        TorManager.readBootstrapProgressPermille = { 250 }
        TorManager.bootstrapTimeoutMs = 80L
        TorManager.bootstrapPollMs = 10L

        TorManager.reportConnectedOnceBootstrapped()

        assertEquals(1, destroyCalls.get())
        assertFalse(TorManager.isRunning.value)
        assertTrue(TorManager.status.value is TorStatus.Failed)
    }

    @Test
    fun `reportConnectedOnceBootstrapped marks Tor running once bootstrapped`() = runBlocking {
        TorManager.checkBootstrapped = { 1 }
        TorManager.bootstrapTimeoutMs = 500L
        TorManager.bootstrapPollMs = 10L

        TorManager.reportConnectedOnceBootstrapped()

        assertEquals(0, destroyCalls.get())
        assertTrue(TorManager.isRunning.value)
        assertEquals(TorStatus.Connected, TorManager.status.value)
    }
}
