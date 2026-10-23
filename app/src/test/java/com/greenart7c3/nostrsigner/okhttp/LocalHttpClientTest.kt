package com.greenart7c3.nostrsigner.okhttp

import com.greenart7c3.nostrsigner.AmberLog
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Private (local network) URLs skip Tor. A redirect from one to a public host
 * would be dialed on that direct client and reveal the user's IP, so the
 * client chosen for them must not follow redirects.
 */
class LocalHttpClientTest {
    @Before
    fun setUp() {
        mockkObject(AmberLog)
        every { AmberLog.d(any(), any<String>()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(AmberLog)
    }

    @Test
    fun localClientNeverFollowsRedirects() {
        val local = HttpClientManager.getLocalHttpClient()
        assertFalse(local.followRedirects)
        assertFalse(local.followSslRedirects)
    }

    @Test
    fun localClientIsRebuiltWithoutRedirectsAfterSettingsChange() {
        HttpClientManager.getLocalHttpClient()
        HttpClientManager.setDefaultUserAgent("Amber-test-${System.nanoTime()}")
        assertFalse(HttpClientManager.getLocalHttpClient().followRedirects)
    }

    @Test
    fun publicClientsKeepFollowingRedirects() {
        assertTrue(HttpClientManager.getHttpClient(useProxy = false).followRedirects)
        assertTrue(HttpClientManager.getHttpClient(useProxy = true).followRedirects)
    }
}
