package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.AppRecord
import com.greenart7c3.nostrsigner.desktop.core.AppWithPermissions
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sender-chosen created_at must neither poison the subscription `since` nor
 * let stale requests be processed again (the desktop port lacked Android's
 * +/-5 minute window).
 */
class BunkerFreshnessTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.invalid/")!!

    private fun computeSince(): Long {
        val method = AmberDesktop.engine.javaClass.getDeclaredMethod("computeSince").apply { isAccessible = true }
        return method.invoke(AmberDesktop.engine) as Long
    }

    @Test
    fun futureDatedEventDoesNotMoveSinceIntoTheFuture() = runBlocking {
        val now = TimeUtils.now()
        val tag = KeyPair().pubKey.toHexKey()
        val attacker = NostrSignerInternal(KeyPair())
        val event: Event = attacker.signerSync.sign(now + 900, NostrConnectEvent.KIND, arrayOf(arrayOf("p", tag)), "garbage")

        AmberDesktop.engine.consume(event, relay)

        assertTrue(computeSince() <= TimeUtils.now())
    }

    @Test
    fun staleLogoutIsIgnored() = runBlocking {
        val account = AccountManager.addAccount(KeyPair(), name = "freshness", signPolicy = 1)
        val store = AmberDesktop.store(account.npub)
        val clientKp = KeyPair()
        val client = NostrSignerInternal(clientKp)
        val clientHex = clientKp.pubKey.toHexKey()
        store.upsert(AppWithPermissions(AppRecord(key = clientHex, name = "c", relays = listOf(relay.url), pubKey = account.hexKey, isConnected = true, signPolicy = 1)))

        val content = client.nip44Encrypt("""{"id":"old-logout","method":"logout","params":[]}""", account.hexKey)
        val oldLogout: Event = client.signerSync.sign(TimeUtils.now() - 6 * 3600, NostrConnectEvent.KIND, arrayOf(arrayOf("p", account.hexKey)), content)

        AmberDesktop.engine.consume(oldLogout, relay)

        // Dropped before processing: never logged as a new request, app untouched.
        assertFalse(store.logs.value.any { oldLogout.id in it.message })
        assertNotNull(store.getByKey(clientHex))
    }
}
