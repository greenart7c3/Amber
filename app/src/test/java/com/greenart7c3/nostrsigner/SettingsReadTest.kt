package com.greenart7c3.nostrsigner

import android.content.Context
import com.greenart7c3.nostrsigner.models.ProfileFetchInterval
import com.greenart7c3.nostrsigner.models.TorMode
import com.greenart7c3.nostrsigner.models.UpdateChannel
import com.greenart7c3.nostrsigner.models.defaultAppRelays
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** How stored settings are read back; existing installs depend on every one of these. */
class SettingsReadTest {
    private val prefs = FakePreferences()
    private val context = mockk<Context> { every { getSharedPreferences("prefs", Context.MODE_PRIVATE) } returns prefs }

    @Before
    fun setUp() {
        mockkObject(HttpClientManager)
        every { HttpClientManager.setDefaultProxyOnPort(any()) } just runs
    }

    @After
    fun tearDown() = unmockkObject(HttpClientManager)

    private fun read() = LocalPreferences.loadSettingsAtStartup(context)

    @Test
    fun `nothing stored reads as the defaults with no proxy`() {
        val settings = read()

        assertEquals(TorMode.DISABLED, settings.torMode)
        assertEquals(defaultAppRelays, settings.defaultRelays)
        assertNull(settings.localBunkerPort)
        verify(exactly = 0) { HttpClientManager.setDefaultProxyOnPort(any()) }
    }

    // Installs from before TorMode only have the use_proxy boolean.
    @Test
    fun `the old use_proxy flag reads as Orbot on the stored port`() {
        prefs.values["use_proxy"] = true
        prefs.values["proxy_port"] = 9150

        assertEquals(TorMode.ORBOT, read().torMode)
        verify { HttpClientManager.setDefaultProxyOnPort(9150) }
    }

    @Test
    fun `an unknown stored name reads as the default`() {
        prefs.values["update_channel"] = "NIGHTLY"
        prefs.values["profile_fetch_interval"] = "SOMETIMES"
        prefs.values["tor_mode"] = "SNOWFLAKE"

        val settings = read()
        assertEquals(UpdateChannel.STABLE, settings.updateChannel)
        assertEquals(ProfileFetchInterval.FIFTEEN_MINUTES, settings.profileFetchInterval)
        assertEquals(TorMode.DISABLED, settings.torMode)
    }

    @Test
    fun `the pre-rename trust score key is removed`() {
        prefs.values["trust_score_enabled"] = true

        read()
        assertFalse(prefs.values.containsKey("trust_score_enabled"))
    }

    @Test
    fun `a stored local bunker port is kept`() {
        prefs.values["local_bunker_port"] = 4869

        assertEquals(4869, read().localBunkerPort)
    }
}
