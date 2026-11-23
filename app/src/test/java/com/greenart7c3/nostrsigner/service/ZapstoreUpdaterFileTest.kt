package com.greenart7c3.nostrsigner.service

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Which file events of a release become the update: Android APKs only, and no build
 * for another platform. (A release signed by the developer key, which a test cannot
 * sign, is what records the version of its file events; the test sets it directly.)
 */
class ZapstoreUpdaterFileTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        scope.cancel()
        unmockkStatic(android.util.Log::class)
    }

    /** The URL the updater offers after one file event with [tags], or null when it skips it. */
    private fun offeredUrl(vararg tags: Array<String>): String? = runBlocking {
        val updater = ZapstoreUpdater(mockk<NostrClient>(relaxed = true), scope, relayClient = mockk(relaxed = true), appContext = mockk(relaxed = true))
        val file: Event = NostrSignerSync(KeyPair()).signNormal(1_700_000_000L, 1063, arrayOf(*tags), "")

        @Suppress("UNCHECKED_CAST")
        val versions = ZapstoreUpdater::class.java.getDeclaredField("versionByEventId").apply { isAccessible = true }.get(updater) as MutableMap<String, Pair<String, Long>>
        versions[file.id] = "99.1.0" to file.createdAt
        val fileSubId = ZapstoreUpdater::class.java.getDeclaredField("fileSubId").apply { isAccessible = true }.get(updater) as String

        updater.onIncomingMessage(mockk<IRelayClient>(relaxed = true), file.toJson(), EventMessage(fileSubId, file))
        // Messages are handled on the updater's own coroutine; give it time to act.
        delay(1_000.milliseconds)
        updater.latestRelease.value?.url
    }

    @Test
    fun anApkUrlIsOffered() {
        assertEquals("https://x.invalid/amber.apk", offeredUrl(arrayOf("url", "https://x.invalid/amber.apk")))
    }

    @Test
    fun anAndroidMimeTypeIsOfferedWhateverTheUrl() {
        assertEquals("https://x.invalid/download", offeredUrl(arrayOf("url", "https://x.invalid/download"), arrayOf("m", "application/vnd.android.package-archive")))
    }

    @Test
    fun aUniversalBuildIsOffered() {
        assertEquals("https://x.invalid/amber.apk", offeredUrl(arrayOf("url", "https://x.invalid/amber.apk"), arrayOf("arch", "universal")))
    }

    @Test
    fun anythingButAnAndroidApkIsSkipped() {
        assertEquals(null, offeredUrl(arrayOf("url", "https://x.invalid/amber.zip"), arrayOf("m", "application/zip")))
        assertEquals(null, offeredUrl(arrayOf("url", "https://x.invalid/amber.apk"), arrayOf("platform", "ios")))
        assertEquals(null, offeredUrl(arrayOf("m", "application/vnd.android.package-archive")))
    }
}
