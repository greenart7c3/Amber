package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.generateBunkerPrivKey
import com.greenart7c3.nostrsigner.common.localPubKeyFromPrivKey
import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.AppPermissionRecord
import com.greenart7c3.nostrsigner.desktop.core.AppRecord
import com.greenart7c3.nostrsigner.desktop.core.AppWithPermissions
import com.greenart7c3.nostrsigner.desktop.core.EncryptionScope
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NIP-42 relay auth (kind 22242) requests behave like on Android: grants are
 * per relay ("this relay only") or for every relay, and a non-empty auth
 * whitelist rejects other relays without asking.
 */
class RelayAuthTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.invalid/")!!
    private val background = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private class Client(val npub: String, val appKey: String, val signer: NostrSignerInternal, val connectionHex: String)

    private suspend fun connectedClient(name: String, permissions: List<AppPermissionRecord> = emptyList()): Client {
        val account = AccountManager.addAccount(KeyPair(), name = name, signPolicy = 1)
        val clientKp = KeyPair()
        val clientHex = clientKp.pubKey.toHexKey()
        val connectionKey = generateBunkerPrivKey()
        AmberDesktop.store(account.npub).upsert(
            AppWithPermissions(
                AppRecord(key = clientHex, name = name, relays = listOf(relay.url), pubKey = account.hexKey, isConnected = true, signPolicy = 1, localKey = connectionKey),
                permissions.toMutableList(),
            ),
        )
        AmberDesktop.engine.updateFilter()
        return Client(account.npub, clientHex, NostrSignerInternal(clientKp), localPubKeyFromPrivKey(connectionKey))
    }

    private suspend fun authRequest(client: Client, id: String, authRelay: String): Event {
        val template = """{"kind":22242,"content":"","tags":[["relay","$authRelay"],["challenge","c-$id"]],"created_at":${TimeUtils.now()}}"""
        val escaped = template.replace("\\", "\\\\").replace("\"", "\\\"")
        val json = """{"id":"$id","method":"sign_event","params":["$escaped"]}"""
        val content = client.signer.nip44Encrypt(json, client.connectionHex)
        return client.signer.signerSync.sign(TimeUtils.now(), 24133, arrayOf(arrayOf("p", client.connectionHex)), content)
    }

    private fun pendingFor(client: Client, id: String): PendingBunkerRequest? = AmberDesktop.engine.pending.value.firstOrNull { it.localKey == client.appKey && it.request.id == id }

    private suspend fun awaitCard(client: Client, id: String): PendingBunkerRequest = withTimeout(20.seconds) {
        while (pendingFor(client, id) == null) delay(50.milliseconds)
        pendingFor(client, id)!!
    }

    private fun authHistory(client: Client) = AmberDesktop.store(client.npub).history.value.filter { it.appKey == client.appKey && it.kind == 22242 }

    /**
     * Sends an auth request and returns how it was answered without a card
     * (accepted or rejected, from the history it records), or null when it
     * was queued for approval. The reply itself goes to an unreachable relay,
     * so consume runs in the background.
     */
    private suspend fun answeredWithoutCard(client: Client, id: String, authRelay: String): Boolean? {
        val before = authHistory(client).size
        send(client, id, authRelay)
        return withTimeout(20.seconds) {
            while (pendingFor(client, id) == null && authHistory(client).size <= before) delay(50.milliseconds)
            if (pendingFor(client, id) != null) null else authHistory(client).last().accepted
        }
    }

    /** Hands an auth request to the engine without waiting for its reply to be published. */
    private suspend fun send(client: Client, id: String, authRelay: String) {
        val event = authRequest(client, id, authRelay)
        background.launch { AmberDesktop.engine.consume(event, relay) }
    }

    /**
     * Runs an approve/reject and waits until it is recorded (the grant is
     * saved before that), not until its reply to the unreachable relay gives up.
     */
    private suspend fun decide(client: Client, action: () -> Unit) {
        val before = authHistory(client).size
        action()
        withTimeout(20.seconds) { while (authHistory(client).size <= before) delay(50.milliseconds) }
    }

    @Test
    fun thisRelayOnlyGrantCoversOnlyThatRelay(): Unit = runBlocking {
        val client = connectedClient("relay-auth-specific")
        send(client, "a1", "wss://relay-a.example/")
        val card = awaitCard(client, "a1")
        assertTrue(card.isRelayAuth)
        assertEquals("relay-a.example", card.relayHost)
        assertEquals(EncryptionScope.SPECIFIC, card.defaultScope)

        decide(client) { AmberDesktop.engine.approve(card, RememberType.ALWAYS) }
        val grant = AmberDesktop.store(client.npub).getByKey(client.appKey)!!.permissions.single { it.kind == 22242 }
        assertEquals("relay-a.example", grant.relay)

        assertEquals(true, answeredWithoutCard(client, "a2", "wss://relay-a.example"))
        send(client, "b1", "wss://relay-b.example/")
        awaitCard(client, "b1")
    }

    @Test
    fun allRelaysGrantReplacesPerRelayGrants(): Unit = runBlocking {
        val client = connectedClient("relay-auth-all")
        send(client, "a1", "wss://relay-a.example/")
        val first = awaitCard(client, "a1")
        decide(client) { AmberDesktop.engine.reject(first, RememberType.ALWAYS) }
        assertEquals(false, answeredWithoutCard(client, "a2", "wss://relay-a.example/"))

        send(client, "b1", "wss://relay-b.example/")
        val second = awaitCard(client, "b1")
        decide(client) { AmberDesktop.engine.approve(second, RememberType.ALWAYS, encryptionScope = EncryptionScope.ALL) }

        val grants = AmberDesktop.store(client.npub).getByKey(client.appKey)!!.permissions.filter { it.kind == 22242 }
        assertEquals(listOf("*"), grants.map { it.relay })
        assertEquals(true, answeredWithoutCard(client, "a3", "wss://relay-a.example/"))
        assertEquals(true, answeredWithoutCard(client, "c1", "wss://relay-c.example/"))
    }

    @Test
    fun perRelayGrantWinsOverTheBasicAllRelaysGrant(): Unit = runBlocking {
        // The basic sign policy stores a blank-relay grant, which covers every relay.
        val basic = AppPermissionRecord(SignerType.SIGN_EVENT.toString(), 22242, true, RememberType.ALWAYS.screenCode, Long.MAX_VALUE / 1000, 0)
        val rejectA = AppPermissionRecord(SignerType.SIGN_EVENT.toString(), 22242, false, RememberType.ALWAYS.screenCode, 0, Long.MAX_VALUE / 1000, relay = "relay-a.example")
        val client = connectedClient("relay-auth-basic", listOf(basic, rejectA))

        assertEquals(false, answeredWithoutCard(client, "a1", "wss://relay-a.example/"))
        assertEquals(true, answeredWithoutCard(client, "b1", "wss://relay-b.example/"))
    }

    @Test
    fun whitelistRejectsOtherRelaysWithoutAsking(): Unit = runBlocking {
        val basic = AppPermissionRecord(SignerType.SIGN_EVENT.toString(), 22242, true, RememberType.ALWAYS.screenCode, Long.MAX_VALUE / 1000, 0)
        val client = connectedClient("relay-auth-whitelist", listOf(basic))
        SettingsStore.update { it.copy(authWhitelist = listOf("relay-a.example")) }
        try {
            assertEquals(false, answeredWithoutCard(client, "b1", "wss://relay-b.example/"))
            assertEquals(true, answeredWithoutCard(client, "a1", "wss://relay-a.example/"))
        } finally {
            SettingsStore.update { it.copy(authWhitelist = emptyList()) }
        }
    }

    @Test
    fun thisRelayOnlyChoiceForAnEventWithoutRelayIsNotRemembered(): Unit = runBlocking {
        val client = connectedClient("relay-auth-blank")
        send(client, "x1", "")
        val card = awaitCard(client, "x1")
        decide(client) { AmberDesktop.engine.approve(card, RememberType.ALWAYS) }
        assertFalse(AmberDesktop.store(client.npub).getByKey(client.appKey)!!.permissions.any { it.kind == 22242 })
    }
}
