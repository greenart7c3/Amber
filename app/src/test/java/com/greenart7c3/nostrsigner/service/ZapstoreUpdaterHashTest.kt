package com.greenart7c3.nostrsigner.service

import android.content.Context
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.LocalPreferences
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The release's hash is the only integrity check on the downloaded APK, so a
 * release without one must be refused before anything is downloaded.
 */
class ZapstoreUpdaterHashTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(AmberLog, LocalPreferences)
        every { AmberLog.e(any(), any<String>()) } returns Unit
        every { AmberLog.d(any(), any<String>()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(AmberLog, LocalPreferences)
        scope.cancel()
    }

    private suspend fun downloadApk(release: ZapstoreRelease): Any? {
        val updater = ZapstoreUpdater(mockk(relaxed = true), scope)
        val fn = ZapstoreUpdater::class.java.getDeclaredMethod(
            "downloadApk",
            Context::class.java,
            ZapstoreRelease::class.java,
            Continuation::class.java,
        ).apply { isAccessible = true }
        return suspendCoroutineUninterceptedOrReturn { c -> fn.invoke(updater, context, release, c) }
    }

    @Test
    fun releaseWithoutAHashIsRefusedBeforeDownloading() = runBlocking {
        val release = ZapstoreRelease(version = "99.0.0", url = "https://cdn.example/amber.apk", hash = null, createdAt = 1)

        assertNull(downloadApk(release))

        // Refused before the download setup: no settings load, no cache file.
        coVerify(exactly = 0) { LocalPreferences.loadSettingsFromEncryptedStorage(any()) }
        verify(exactly = 0) { context.cacheDir }
    }
}
