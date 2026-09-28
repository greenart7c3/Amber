package com.greenart7c3.nostrsigner

import com.greenart7c3.nostrsigner.database.LogDatabase
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.ProxyAccountMetadata
import com.greenart7c3.nostrsigner.service.RemoteBunkerClient
import com.greenart7c3.nostrsigner.service.installAmberInstance
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Regression test for the proxy-mode latency fix: awaiting a NIP-46 response must
 * not wait behind publish confirmation. A bunker relay may never send an OK
 * (slow/silent relay, NIP-42 auth race), yet the bunker itself may already have
 * received and answered the request — the response must resolve while publish
 * confirmation is still pending.
 */
class RemoteBunkerClientTest {
    private lateinit var amber: Amber
    private val client = mockk<NostrClient>(relaxed = true)
    private val signer = mockk<NostrSignerInternal>(relaxed = true)
    private var capturedId: String? = null

    companion object {
        private val REMOTE_PUB = "ab".repeat(32)
        private const val RELAY_URL = "wss://relay.example.com"
    }

    @Before
    fun setUp() {
        amber = mockk()
        every { amber.applicationIOScope } returns CoroutineScope(Dispatchers.Unconfined)
        every { amber.client } returns client
        every { amber.getLogDatabase(any()) } returns mockk<LogDatabase>(relaxed = true)
        installAmberInstance(amber)

        // Publish never confirms: the first attempt hangs past the test timeout.
        mockkStatic("com.vitorpamplona.quartz.nip01Core.relay.client.accessories.NostrClientPublishExtKt")
        coEvery { client.publishAndConfirm(any(), any(), any()) } coAnswers {
            delay(60_000)
            false
        }

        coEvery { signer.nip44Encrypt(any(), any()) } answers {
            capturedId = JacksonMapper.mapper.readTree(firstArg<String>()).get("id").asText()
            "enc"
        }
        every { signer.signerSync.sign<Event>(any(), any(), any(), any()) } returns mockk<Event>(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun proxyAccount(): Account = Account(
        signer = signer,
        hexKey = REMOTE_PUB,
        npub = "npub1proxytest",
        name = MutableStateFlow(""),
        picture = MutableStateFlow(""),
        signPolicy = 1,
        didBackup = true,
        scope = CoroutineScope(Dispatchers.Unconfined),
        proxy = ProxyAccountMetadata(
            remotePubkey = REMOTE_PUB,
            relays = listOf(NormalizedRelayUrl(RELAY_URL)),
            bunkerName = "bunker",
            nostrConnectSecret = "",
        ),
    )

    @Test
    fun `request returns response even when publish never confirms`() {
        runBlocking {
            val requestDeferred = async {
                RemoteBunkerClient.request(proxyAccount(), "sign_message", listOf("hello"))
            }
            launch {
                delay(200)
                RemoteBunkerClient.deliverResponse(BunkerResponse(capturedId!!, "sig", null))
            }

            val response = withTimeout(5_000) { requestDeferred.await() }

            assertEquals("sig", response?.result)
            // The response arrived while publish confirmation was pending; cancelling
            // the publish job must stop the retry loop after the first attempt.
            coVerify(exactly = 1) { client.publishAndConfirm(any(), any(), any()) }
        }
    }
}
