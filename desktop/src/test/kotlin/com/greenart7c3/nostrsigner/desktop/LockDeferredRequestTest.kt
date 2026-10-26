package com.greenart7c3.nostrsigner.desktop

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A request the passphrase lock interrupted after consume() marked it seen
 * used to be dropped for good: the dedup set swallowed every redelivery.
 * Deferring it forgets the id and keeps `since` at or before it, so the
 * relays redeliver it after unlock and it is handled then.
 */
class LockDeferredRequestTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.invalid/")!!

    private suspend fun signRequest(client: NostrSignerInternal, connectionHex: String, id: String, createdAt: Long): Event {
        val template = """{"kind":1,"content":"deferred $id","tags":[],"created_at":$createdAt}"""
        val escaped = template.replace("\\", "\\\\").replace("\"", "\\\"")
        val json = """{"id":"$id","method":"sign_event","params":["$escaped"]}"""
        val content = client.nip44Encrypt(json, connectionHex)
        return client.signerSync.sign(createdAt, 24133, arrayOf(arrayOf("p", connectionHex)), content)
    }

    @Test
    fun aDeferredRequestIsHandledWhenRedelivered() = runBlocking {
        val engine = AmberDesktop.engine
        val account = AccountManager.addAccount(KeyPair(), name = "deferred", signPolicy = 1)
        val clientKp = KeyPair()
        val clientHex = clientKp.pubKey.toHexKey()
        val connectionKey = generateBunkerPrivKey()
        AmberDesktop.store(account.npub).upsert(
            AppWithPermissions(AppRecord(key = clientHex, name = "deferred", relays = listOf(relay.url), pubKey = account.hexKey, isConnected = true, signPolicy = 1, localKey = connectionKey)),
        )
        engine.updateFilter()
        val createdAt = TimeUtils.now() - 60
        val event = signRequest(NostrSignerInternal(clientKp), localPubKeyFromPrivKey(connectionKey), "deferred-1", createdAt)
        suspend fun queued() = engine.pending.value.any { it.request.id == "deferred-1" }

        engine.consume(event, relay)
        withTimeout(20_000) { while (!queued()) delay(50) }
        engine.pending.value.filter { it.request.id == "deferred-1" }.forEach { engine.removePending(it) }

        // Already seen: a redelivery is ignored.
        engine.consume(event, relay)
        delay(500)
        assertFalse(queued())

        // Deferred by a lock: `since` reaches back to it and the redelivery is handled.
        engine.deferUntilUnlock(event)
        assertTrue(engine.computeSince() <= createdAt)
        engine.consume(event, relay)
        withTimeout(20_000) { while (!queued()) delay(50) }
    }
}
