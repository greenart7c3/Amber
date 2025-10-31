package com.greenart7c3.nostrsigner.models

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import org.junit.Assert.assertEquals
import org.junit.Test

class AmberBunkerRequestTest {
    @Test
    fun `encryption type survives serialization`() {
        val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")
        requireNotNull(relay) { "Expected relay URL to normalize" }

        val request =
            AmberBunkerRequest(
                request = BunkerRequestConnect(remoteKey = HEX_KEY, secret = "secret", permissions = null),
                localKey = HEX_KEY,
                relays = listOf(relay),
                currentAccount = "npub1test",
                nostrConnectSecret = "secret",
                closeApplication = true,
                name = "Test App",
                signedEvent = null,
                encryptDecryptResponse = null,
                encryptionType = EncryptionType.NIP04,
            )

        val json = request.toJson()
        val roundTrip = AmberBunkerRequest.mapper.readValue(json, AmberBunkerRequest::class.java)

        assertEquals(EncryptionType.NIP04, roundTrip.encryptionType)
    }

    private companion object {
        private const val HEX_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
