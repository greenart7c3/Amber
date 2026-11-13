package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.common.generateBunkerPrivKey
import com.greenart7c3.nostrsigner.common.localPubKeyFromPrivKey
import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.AppRecord
import com.greenart7c3.nostrsigner.desktop.core.AppWithPermissions
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Quartz parses a request whose params are missing or malformed into a
 * BunkerRequestInvalid that keeps its method, so it reaches the request path as
 * that method. Even from a fully trusted app it must be refused with an error
 * reply, never signed, crashed on or left waiting for approval.
 */
class InvalidParamsTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.invalid/")!!

    @Test
    fun malformedRequestsAreRefused() = runBlocking {
        val account = AccountManager.addAccount(KeyPair(), name = "invalid-params", signPolicy = 1)
        val client = NostrSignerInternal(KeyPair())
        val clientHex = client.pubKey
        val localKey = generateBunkerPrivKey()
        AmberDesktop.store(account.npub).upsert(
            AppWithPermissions(AppRecord(key = clientHex, name = "invalid-params", relays = listOf(relay.url), pubKey = account.hexKey, isConnected = true, signPolicy = 2, localKey = localKey)),
        )
        AmberDesktop.engine.updateFilter()
        val store = AmberDesktop.store(account.npub)
        val connectionHex = localPubKeyFromPrivKey(localKey)

        val requests = listOf(
            """{"id":"bad-sign","method":"sign_event","params":["not an event"]}""",
            """{"id":"empty-sign","method":"sign_event","params":[]}""",
            """{"id":"empty-nip44","method":"nip44_encrypt","params":[]}""",
            """{"id":"no-v3-kind","method":"nip44v3_encrypt","params":["${KeyPair().pubKey.toHexKey()}"]}""",
        )
        requests.forEach { json ->
            val content = client.nip44Encrypt(json, connectionHex)
            AmberDesktop.engine.consume(client.signerSync.sign(TimeUtils.now(), 24133, arrayOf(arrayOf("p", connectionHex)), content), relay)
        }

        withTimeout(20.seconds) {
            while (store.logs.value.count { it.message.startsWith("Rejecting request that cannot be fulfilled") } < requests.size) delay(50.milliseconds)
        }
        assertFalse(store.history.value.any { it.appKey == clientHex && it.accepted })
        assertFalse(AmberDesktop.engine.pending.value.any { it.account.npub == account.npub })
    }
}
