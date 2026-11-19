package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.common.generateBunkerPrivKey
import com.greenart7c3.nostrsigner.common.localPubKeyFromPrivKey
import com.greenart7c3.nostrsigner.common.relays.LocalRelayServer.Status
import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.AppPermissionRecord
import com.greenart7c3.nostrsigner.desktop.core.AppRecord
import com.greenart7c3.nostrsigner.desktop.core.AppWithPermissions
import com.greenart7c3.nostrsigner.desktop.core.LocalBunkerRelay
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.TimeUtils
import java.net.ServerSocket
import java.net.URLEncoder
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Like Android, an approval whose reply never reaches the client is taken back:
 * the client did not get its answer, so nothing it never heard of stays granted.
 */
class ApprovalRevertTest {
    private val nip44Grant = AppPermissionRecord("NIP44_ENCRYPT", null, true, RememberType.ALWAYS.screenCode, Long.MAX_VALUE / 1000, 0)

    // A relay nothing listens on: every publish to it fails.
    private fun deadRelay(): NormalizedRelayUrl {
        val port = ServerSocket(0).use { it.localPort }
        return RelayUrlNormalizer.normalizeOrNull("ws://127.0.0.1:$port")!!
    }

    private suspend fun signRequest(client: NostrSignerInternal, connectionHex: String, id: String): Event {
        val template = """{"kind":1,"content":"revert $id","tags":[],"created_at":${TimeUtils.now()}}"""
        val escaped = template.replace("\\", "\\\\").replace("\"", "\\\"")
        val json = """{"id":"$id","method":"sign_event","params":["$escaped"]}"""
        return client.signerSync.sign(TimeUtils.now(), 24133, arrayOf(arrayOf("p", connectionHex)), client.nip44Encrypt(json, connectionHex))
    }

    /** Approves, remembering it, a sign_event card from a connection on [relay]; returns the connection afterwards. */
    private suspend fun CoroutineScope.approveSignEvent(relay: NormalizedRelayUrl, name: String): AppWithPermissions? {
        val account = AccountManager.addAccount(KeyPair(), name = name, signPolicy = 1)
        val store = AmberDesktop.store(account.npub)
        val clientKp = KeyPair()
        val clientHex = clientKp.pubKey.toHexKey()
        val connectionKey = generateBunkerPrivKey()
        store.upsert(
            AppWithPermissions(
                AppRecord(key = clientHex, name = name, relays = listOf(relay.url), pubKey = account.hexKey, isConnected = true, signPolicy = 1, localKey = connectionKey),
                mutableListOf(nip44Grant),
            ),
        )
        AmberDesktop.engine.updateFilter()

        launch { AmberDesktop.engine.consume(signRequest(NostrSignerInternal(clientKp), localPubKeyFromPrivKey(connectionKey), "$name-1"), relay) }
        val card = withTimeout(20.seconds) {
            while (AmberDesktop.engine.pending.value.none { it.localKey == clientHex }) delay(50.milliseconds)
            AmberDesktop.engine.pending.value.first { it.localKey == clientHex }
        }
        withTimeout(90.seconds) { AmberDesktop.engine.approve(card, RememberType.ALWAYS).join() }
        return store.getByKey(clientHex)
    }

    private fun AppWithPermissions?.signsKind1() = this?.permissions.orEmpty().any { it.type == "SIGN_EVENT" && it.kind == 1 && it.acceptable }

    @Test
    fun aGrantIsTakenBackWhenTheReplyFails() = runBlocking {
        val app = approveSignEvent(deadRelay(), "revert-failed")

        assertTrue(!app.signsKind1())
        // What the connection was granted before stays.
        assertEquals(listOf(nip44Grant), app?.permissions)
    }

    @Test
    fun aGrantStaysWhenTheReplyIsDelivered() = runBlocking {
        LocalBunkerRelay.stop()
        assertEquals(Status.RUNNING, LocalBunkerRelay.start(0))
        try {
            AmberDesktop.engine.start()
            val app = approveSignEvent(LocalBunkerRelay.relayUrl() ?: error("relay did not start"), "revert-delivered")
            assertTrue(app.signsKind1())
        } finally {
            LocalBunkerRelay.stop()
        }
    }

    @Test
    fun aNostrConnectConnectionIsDroppedWhenTheReplyFails() = runBlocking {
        val account = AccountManager.addAccount(KeyPair(), name = "revert-connect", signPolicy = 1)
        val clientHex = KeyPair().pubKey.toHexKey()
        val relay = URLEncoder.encode(deadRelay().url, "utf-8")
        assertNull(AmberDesktop.engine.addNostrConnect("nostrconnect://$clientHex?relay=$relay&name=revert-app", account))
        val card = withTimeout(20.seconds) {
            while (AmberDesktop.engine.pending.value.none { it.localKey == clientHex }) delay(50.milliseconds)
            AmberDesktop.engine.pending.value.first { it.localKey == clientHex }
        }

        withTimeout(90.seconds) { AmberDesktop.engine.approve(card, RememberType.ALWAYS, signPolicy = 1).join() }

        assertNull(AmberDesktop.store(account.npub).getByKey(clientHex))
    }
}
