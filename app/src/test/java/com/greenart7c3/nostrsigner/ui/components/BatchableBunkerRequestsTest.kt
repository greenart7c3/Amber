package com.greenart7c3.nostrsigner.ui.components

import com.greenart7c3.nostrsigner.common.EncryptionType
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestPing
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A connect approved from the batch screen skipped the account choice and the
 * replace-or-create-new prompt. Connects are now never part of a batch.
 */
class BatchableBunkerRequestsTest {
    private fun request(request: BunkerRequest) = AmberBunkerRequest(
        request = request,
        localKey = "ab".repeat(32),
        relays = emptyList(),
        currentAccount = "npub1",
        nostrConnectSecret = "",
        closeApplication = true,
        name = "",
        signedEvent = null,
        encryptedData = null,
        encryptionType = EncryptionType.NIP44,
        isNostrConnectUri = false,
    )

    @Test
    fun connectsAreLeftOutOfABatch() {
        val ping = request(BunkerRequestPing(id = "1"))
        val connect = request(BunkerRequestConnect(id = "2", remoteKey = "cd".repeat(32)))
        val pong = request(BunkerRequestPing(id = "3"))
        assertEquals(listOf(ping, pong), batchableBunkerRequests(listOf(ping, connect, pong)))
    }
}
