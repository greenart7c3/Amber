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
 * A connection whose "delete after" time has passed must stop working at
 * once, not only when the prune loop (every 15 s, and never while locked)
 * gets to it.
 */
class ExpiredConnectionTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.invalid/")!!

    private suspend fun signRequest(client: NostrSignerInternal, connectionHex: String, id: String): Event {
        val template = """{"kind":1,"content":"expiry $id","tags":[],"created_at":${TimeUtils.now()}}"""
        val escaped = template.replace("\\", "\\\\").replace("\"", "\\\"")
        val json = """{"id":"$id","method":"sign_event","params":["$escaped"]}"""
        val content = client.nip44Encrypt(json, connectionHex)
        return client.signerSync.sign(TimeUtils.now(), 24133, arrayOf(arrayOf("p", connectionHex)), content)
    }

    /** A fully trusted connection (signs everything unasked) expiring at [deleteAfter]. */
    private suspend fun fullyTrustedApp(name: String, deleteAfter: Long): Triple<String, NostrSignerInternal, String> {
        val account = AccountManager.addAccount(KeyPair(), name = name, signPolicy = 1)
        val client = KeyPair()
        val clientHex = client.pubKey.toHexKey()
        AmberDesktop.store(account.npub).upsert(
            AppWithPermissions(AppRecord(key = clientHex, name = name, relays = listOf(relay.url), pubKey = account.hexKey, isConnected = true, signPolicy = 2, deleteAfter = deleteAfter, localKey = generateBunkerPrivKey())),
        )
        AmberDesktop.engine.updateFilter()
        return Triple(account.npub, NostrSignerInternal(client), clientHex)
    }

    private fun connectionHex(npub: String, clientHex: String) = localPubKeyFromPrivKey(AmberDesktop.store(npub).getByKey(clientHex)!!.app.localKey)

    @Test
    fun anExpiredConnectionIsNotSignedFor() = runBlocking {
        val (npub, client, clientHex) = fullyTrustedApp("expired", deleteAfter = TimeUtils.now() - 10)
        val store = AmberDesktop.store(npub)

        AmberDesktop.engine.consume(signRequest(client, connectionHex(npub, clientHex), "expired-1"), relay)
        delay(1_000)

        assertFalse(store.history.value.any { it.appKey == clientHex && it.accepted })
        assertTrue(store.logs.value.any { it.message == "Connection expired for $clientHex" })
    }

    @Test
    fun aConnectionThatHasNotExpiredStillSigns() = runBlocking {
        val (npub, client, clientHex) = fullyTrustedApp("not-expired", deleteAfter = TimeUtils.now() + 3_600)
        val store = AmberDesktop.store(npub)

        AmberDesktop.engine.consume(signRequest(client, connectionHex(npub, clientHex), "live-1"), relay)

        withTimeout(20_000) {
            while (store.history.value.none { it.appKey == clientHex && it.accepted }) delay(50)
        }
    }
}
