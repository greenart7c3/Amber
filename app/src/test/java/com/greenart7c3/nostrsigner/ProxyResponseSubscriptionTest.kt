package com.greenart7c3.nostrsigner

import android.content.Context
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.ProxyAccountMetadata
import com.greenart7c3.nostrsigner.service.ProxyResponseSubscription
import com.greenart7c3.nostrsigner.service.installAmberInstance
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test

/**
 * Regression tests for the proxy-response hot path: resolution of an incoming
 * kind-24133 response must come from the in-memory index built by
 * [ProxyResponseSubscription.updateFilter], never from per-event Keystore-backed
 * account loads.
 */
class ProxyResponseSubscriptionTest {
    private val context = mockk<Context>(relaxed = true)
    private val client = mockk<NostrClient>(relaxed = true)
    private lateinit var subscription: ProxyResponseSubscription
    private lateinit var proxyAccount: Account
    private lateinit var normalAccount: Account

    companion object {
        private val PROXY_LOCAL_PUB = "cd".repeat(32)
        private val NORMAL_LOCAL_PUB = "ef".repeat(32)
        private val UNKNOWN_PUB = "01".repeat(32)
        private val BUNKER_PUB = "ab".repeat(32)
    }

    @Before
    fun setUp() {
        val amber = mockk<Amber>()
        every { amber.applicationIOScope } returns CoroutineScope(Dispatchers.Unconfined)
        installAmberInstance(amber)

        subscription = ProxyResponseSubscription(client, context)

        proxyAccount = accountFor(
            PROXY_LOCAL_PUB,
            npub = "npub1proxy",
            proxy = ProxyAccountMetadata(
                remotePubkey = BUNKER_PUB,
                relays = listOf(NormalizedRelayUrl("wss://relay.example.com")),
                bunkerName = "bunker",
                nostrConnectSecret = "",
            ),
        )
        normalAccount = accountFor(NORMAL_LOCAL_PUB, npub = "npub1normal", proxy = null)

        mockkObject(LocalPreferences)
        coEvery { LocalPreferences.allAccounts(any()) } returns listOf(proxyAccount, normalAccount)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /** Read-only keypair (pubkey given, no crypto) so the local pubkey hex is deterministic. */
    private fun accountFor(localPubHex: String, npub: String, proxy: ProxyAccountMetadata?): Account = Account(
        signer = NostrSignerInternal(KeyPair(pubKey = localPubHex.hexToByteArray())),
        hexKey = BUNKER_PUB,
        npub = npub,
        name = MutableStateFlow(""),
        picture = MutableStateFlow(""),
        signPolicy = 1,
        didBackup = true,
        scope = CoroutineScope(Dispatchers.Unconfined),
        proxy = proxy,
    )

    // updateFilter() is a no-op on the offline flavor by design (no network),
    // so the proxy-account cache only exists in network flavors.
    private fun skipOnOfflineFlavor() {
        assumeFalse(BuildFlavorChecker.isOfflineFlavor())
    }

    @Test
    fun `updateFilter caches proxy accounts and does not touch encrypted storage`() = runBlocking {
        skipOnOfflineFlavor()
        subscription.updateFilter()

        assertEquals(proxyAccount, subscription.resolveForTaggedKey(PROXY_LOCAL_PUB))
        assertNull(subscription.resolveForTaggedKey(NORMAL_LOCAL_PUB))
        assertNull(subscription.resolveForTaggedKey(UNKNOWN_PUB))
        coVerify(exactly = 0) { LocalPreferences.loadFromEncryptedStorage(any(), any()) }
    }

    @Test
    fun `updateFilter prunes cache entries of removed accounts`() = runBlocking {
        skipOnOfflineFlavor()
        subscription.updateFilter()
        coEvery { LocalPreferences.allAccounts(any()) } returns emptyList()

        subscription.updateFilter()

        assertNull(subscription.resolveForTaggedKey(PROXY_LOCAL_PUB))
    }
}
