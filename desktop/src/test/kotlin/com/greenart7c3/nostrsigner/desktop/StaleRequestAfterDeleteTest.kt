package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.common.generateBunkerPrivKey
import com.greenart7c3.nostrsigner.common.localPubKeyFromPrivKey
import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.AppRecord
import com.greenart7c3.nostrsigner.desktop.core.AppWithPermissions
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A request queued for approval must not bring its app back when the user
 * deleted the app before answering the card.
 */
class StaleRequestAfterDeleteTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.invalid/")!!

    private suspend fun signRequest(client: NostrSignerInternal, connectionHex: String, id: String): Event {
        val template = """{"kind":1,"content":"stale $id","tags":[],"created_at":${TimeUtils.now()}}"""
        val escaped = template.replace("\\", "\\\\").replace("\"", "\\\"")
        val json = """{"id":"$id","method":"sign_event","params":["$escaped"]}"""
        val content = client.nip44Encrypt(json, connectionHex)
        return client.signerSync.sign(TimeUtils.now(), 24133, arrayOf(arrayOf("p", connectionHex)), content)
    }

    /** Queues a sign_event card for a connected app, deletes the app, and returns the stale card. */
    private suspend fun CoroutineScope.staleCard(accountPolicy: Int, name: String): Triple<PendingBunkerRequest, String, String> {
        val account = AccountManager.addAccount(KeyPair(), name = name, signPolicy = accountPolicy)
        val store = AmberDesktop.store(account.npub)
        val clientKp = KeyPair()
        val client = NostrSignerInternal(clientKp)
        val clientHex = clientKp.pubKey.toHexKey()
        val connectionKey = generateBunkerPrivKey()
        store.upsert(AppWithPermissions(AppRecord(key = clientHex, name = name, relays = listOf(relay.url), pubKey = account.hexKey, isConnected = true, signPolicy = 1, localKey = connectionKey)))
        AmberDesktop.engine.updateFilter()

        launch { AmberDesktop.engine.consume(signRequest(client, localPubKeyFromPrivKey(connectionKey), "$name-1"), relay) }
        val card = withTimeout(20.seconds) {
            while (AmberDesktop.engine.pending.value.none { it.localKey == clientHex }) delay(50.milliseconds)
            AmberDesktop.engine.pending.value.first { it.localKey == clientHex }
        }
        store.delete(clientHex)
        return Triple(card, account.npub, clientHex)
    }

    @Test
    fun rejectingAStaleCardDoesNotRecreateTheApp() = runBlocking {
        // Account default "fully trust" is the worst case: a recreated app would auto-sign.
        val (card, npub, clientHex) = staleCard(accountPolicy = 2, name = "stale-reject")

        withTimeoutOrNull(20.seconds) { AmberDesktop.engine.reject(card, RememberType.NEVER).join() }

        assertNull(AmberDesktop.store(npub).getByKey(clientHex))
    }

    @Test
    fun approvingAStaleCardDoesNotRecreateTheApp() = runBlocking {
        val (card, npub, clientHex) = staleCard(accountPolicy = 1, name = "stale-approve")

        withTimeoutOrNull(20.seconds) { AmberDesktop.engine.approve(card, RememberType.ALWAYS).join() }

        val store = AmberDesktop.store(npub)
        assertNull(store.getByKey(clientHex))
        assertFalse(store.history.value.any { it.appKey == clientHex && it.accepted })
    }
}
