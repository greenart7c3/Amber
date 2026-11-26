package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.connectsThroughOtherKey
import com.greenart7c3.nostrsigner.common.generateBunkerPrivKey
import com.greenart7c3.nostrsigner.common.localPubKeyFromPrivKey
import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.AppRecord
import com.greenart7c3.nostrsigner.desktop.core.AppWithPermissions
import com.vitorpamplona.quartz.nip01Core.core.Event
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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A stored app may only talk to Amber through its own connection key. A
 * request it addresses to another connection's key used to be decrypted with
 * that key and answered under that connection's identity.
 */
class ConnectionKeyBindingTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.invalid/")!!

    private suspend fun signRequest(client: NostrSignerInternal, connectionPubKey: String, id: String): Event {
        val template = """{"kind":1,"content":"binding $id","tags":[],"created_at":${TimeUtils.now()}}"""
        val escaped = template.replace("\\", "\\\\").replace("\"", "\\\"")
        val json = """{"id":"$id","method":"sign_event","params":["$escaped"]}"""
        val content = client.nip44Encrypt(json, connectionPubKey)
        return client.signerSync.sign(TimeUtils.now(), 24133, arrayOf(arrayOf("p", connectionPubKey)), content)
    }

    @Test
    fun `a request to another connection's key is dropped while the same request on the app's own key is queued`() = runBlocking {
        val account = AccountManager.addAccount(KeyPair(), name = "binding", signPolicy = 1)
        val store = AmberDesktop.store(account.npub)
        val clientA = KeyPair()
        val clientAHex = clientA.pubKey.toHexKey()
        val privA = generateBunkerPrivKey()
        val privB = generateBunkerPrivKey()
        store.upsert(AppWithPermissions(AppRecord(key = clientAHex, name = "app-A", relays = listOf(relay.url), pubKey = account.hexKey, isConnected = true, signPolicy = 1, localKey = privA)))
        store.upsert(AppWithPermissions(AppRecord(key = KeyPair().pubKey.toHexKey(), name = "app-B", relays = listOf(relay.url), pubKey = account.hexKey, isConnected = true, signPolicy = 1, localKey = privB)))
        AmberDesktop.engine.updateFilter()
        val signer = NostrSignerInternal(clientA)

        AmberDesktop.engine.consume(signRequest(signer, localPubKeyFromPrivKey(privB), "cross"), relay)
        delay(1.seconds)
        assertFalse("app A addressing app B's connection key gets no card", AmberDesktop.engine.pending.value.any { it.request.id == "cross" })
        assertTrue("app A addressing app B's connection key is logged", store.logs.value.any { it.message.contains("reached another connection's key") })

        AmberDesktop.engine.consume(signRequest(signer, localPubKeyFromPrivKey(privA), "own"), relay)
        withTimeout(20.seconds) {
            while (AmberDesktop.engine.pending.value.none { it.request.id == "own" }) delay(50.milliseconds)
        }
    }

    @Test
    fun `a request to the account key itself is ignored, as every desktop connection has its own key`() = runBlocking {
        val account = AccountManager.addAccount(KeyPair(), name = "account-key", signPolicy = 1)
        val store = AmberDesktop.store(account.npub)
        val client = KeyPair()
        val clientHex = client.pubKey.toHexKey()
        store.upsert(AppWithPermissions(AppRecord(key = clientHex, name = "keyless", relays = listOf(relay.url), pubKey = account.hexKey, isConnected = true, signPolicy = 1)))
        AmberDesktop.engine.updateFilter()

        AmberDesktop.engine.consume(signRequest(NostrSignerInternal(client), account.hexKey, "to-account"), relay)
        delay(1.seconds)
        assertFalse(AmberDesktop.engine.pending.value.any { it.request.id == "to-account" })
        assertTrue(store.logs.value.none { it.message.contains("from $clientHex") })
    }

    @Test
    fun onlyNonConnectRequestsOnAnotherKeyAreRefused() {
        assertTrue(connectsThroughOtherKey(appLocalKey = "a", routedPrivKey = "b", type = SignerType.SIGN_EVENT))
        assertFalse("its own key is allowed", connectsThroughOtherKey(appLocalKey = "a", routedPrivKey = "a", type = SignerType.SIGN_EVENT))
        assertFalse("the account key, with no per-connection key, is allowed", connectsThroughOtherKey(appLocalKey = "a", routedPrivKey = "", type = SignerType.SIGN_EVENT))
        assertFalse("a connect is how a client attaches to a new key", connectsThroughOtherKey(appLocalKey = "a", routedPrivKey = "b", type = SignerType.CONNECT))
    }
}
