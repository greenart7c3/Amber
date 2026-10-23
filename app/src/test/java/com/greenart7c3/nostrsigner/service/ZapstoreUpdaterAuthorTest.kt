package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ZapstoreUpdaterAuthorTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.d(any(), any(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        installAmberInstance(mockk<Amber>(relaxed = true))
        mockkObject(LocalPreferences)
        every { LocalPreferences.loadSettingsFromEncryptedStorage(any()) } returns AmberSettings()
    }

    @After
    fun tearDown() {
        scope.cancel()
        unmockkObject(LocalPreferences)
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `validly signed release from a non-developer key is ignored`() = runBlocking {
        val updater = ZapstoreUpdater(mockk<NostrClient>(relaxed = true), scope)
        val subIdField = ZapstoreUpdater::class.java.getDeclaredField("releaseSubId").apply { isAccessible = true }
        val releaseSubId = subIdField.get(updater) as String

        val tags = arrayOf(
            arrayOf("d", "com.greenart7c3.nostrsigner@99.1.0"),
            arrayOf("i", "com.greenart7c3.nostrsigner"),
            arrayOf("version", "99.1.0"),
            arrayOf("url", "https://attacker.invalid/x.apk"),
            arrayOf("x", "ab".repeat(32)),
        )
        val forged: Event = NostrSignerSync(KeyPair()).signNormal(1_700_000_000L, 30063, tags, "")
        assertNotEquals(Amber.DEVELOPER_HEX_KEY, forged.pubKey)

        updater.onIncomingMessage(mockk<IRelayClient>(relaxed = true), forged.toJson(), EventMessage(releaseSubId, forged))
        // Release processing runs on the updater's own coroutine; give it time to act.
        delay(1_500)

        assertNull(updater.latestRelease.value)
    }
}
