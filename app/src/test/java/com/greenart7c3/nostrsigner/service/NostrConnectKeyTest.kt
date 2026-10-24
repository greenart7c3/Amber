package com.greenart7c3.nostrsigner.service

import android.content.Intent
import android.os.Bundle
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * A nostrconnect:// URI names the client key that becomes the connection's
 * identity; only a lowercase hex public key may queue a connect request.
 */
class NostrConnectKeyTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        val amber = mockk<Amber>(relaxed = true)
        every { amber.applicationIOScope } returns scope
        installAmberInstance(amber)
        mockkObject(AmberLog)
        every { AmberLog.e(any(), any(), any()) } returns Unit
        mockkObject(BunkerRequestUtils)
        every { BunkerRequestUtils.addRequest(any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(BunkerRequestUtils)
        unmockkObject(AmberLog)
    }

    private fun connect(key: String) {
        val extras = mockk<Bundle>(relaxed = true)
        every { extras.getString("id") } returns "nc-test"
        val intent = mockk<Intent>(relaxed = true)
        every { intent.extras } returns extras
        every { intent.dataString } returns "nostrconnect://$key?relay=wss%3A%2F%2Frelay.example&name=test"
        NostrConnectUtils.getIntentFromNostrConnect(intent, newTestAccount(scope))
    }

    @Test
    fun `a malformed key queues nothing`() {
        val valid = KeyPair().pubKey.toHexKey()
        listOf(valid.uppercase(), valid.dropLast(1) + "g", valid.dropLast(1), "").forEach { connect(it) }

        verify(exactly = 0) { BunkerRequestUtils.addRequest(any()) }
    }

    @Test
    fun `a lowercase hex key queues the connect`() {
        connect(KeyPair().pubKey.toHexKey())

        verify(exactly = 1) { BunkerRequestUtils.addRequest(any()) }
    }
}
