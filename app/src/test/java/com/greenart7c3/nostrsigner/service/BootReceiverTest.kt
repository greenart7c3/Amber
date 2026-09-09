package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.content.Intent
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BootReceiverTest {
    private lateinit var amber: Amber

    @Before
    fun setUp() {
        amber = mockk(relaxed = true)
        installAmberInstance(amber)
        mockkObject(AmberLog)
        every { AmberLog.d(any(), any<String>()) } returns Unit
        mockkObject(BuildFlavorChecker)
        every { BuildFlavorChecker.isOfflineFlavor() } returns false
        mockkObject(LocalPreferences)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun receive(action: String, startServiceOnBoot: Boolean) = runTest {
        every { LocalPreferences.getStartServiceOnBoot(any()) } returns startServiceOnBoot
        val intent = mockk<Intent>()
        every { intent.action } returns action
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        BootReceiver(scope).onReceive(mockk<Context>(), intent)
        advanceUntilIdle()
    }

    @Test
    fun `package replaced with toggle off does not start service`() {
        receive(Intent.ACTION_MY_PACKAGE_REPLACED, startServiceOnBoot = false)
        verify(exactly = 0) { amber.startService() }
    }

    @Test
    fun `package replaced with toggle on starts service`() {
        receive(Intent.ACTION_MY_PACKAGE_REPLACED, startServiceOnBoot = true)
        verify(exactly = 1) { amber.startService() }
    }

    @Test
    fun `boot completed with toggle off does not start service`() {
        receive(Intent.ACTION_BOOT_COMPLETED, startServiceOnBoot = false)
        verify(exactly = 0) { amber.startService() }
    }

    @Test
    fun `boot completed with toggle on starts service`() {
        receive(Intent.ACTION_BOOT_COMPLETED, startServiceOnBoot = true)
        verify(exactly = 1) { amber.startService() }
    }
}
