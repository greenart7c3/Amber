package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.common.generateBunkerPrivKey
import com.greenart7c3.nostrsigner.common.localPubKeyFromPrivKey
import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.AppPermissionRecord
import com.greenart7c3.nostrsigner.desktop.core.AppRecord
import com.greenart7c3.nostrsigner.desktop.core.AppWithPermissions
import com.greenart7c3.nostrsigner.desktop.core.RememberType
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
 * A whole-NIP grant (a client's `nip:1` connect permission) decides a sign
 * request for a kind of that NIP, as on Android, instead of asking every time.
 */
class NipGrantTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.invalid/")!!

    @Test
    fun aWholeNipGrantSignsItsKinds() = runBlocking {
        val account = AccountManager.addAccount(KeyPair(), name = "nip-grant", signPolicy = 1)
        val client = NostrSignerInternal(KeyPair())
        val localKey = generateBunkerPrivKey()
        val nipGrant = AppPermissionRecord("NIP", 1, true, RememberType.ALWAYS.screenCode, Long.MAX_VALUE / 1000, 0)
        AmberDesktop.store(account.npub).upsert(
            AppWithPermissions(
                AppRecord(key = client.pubKey, name = "nip-grant", relays = listOf(relay.url), pubKey = account.hexKey, isConnected = true, signPolicy = 1, localKey = localKey),
                mutableListOf(nipGrant),
            ),
        )
        AmberDesktop.engine.updateFilter()
        val store = AmberDesktop.store(account.npub)
        val connectionHex = localPubKeyFromPrivKey(localKey)

        val template = """{\"kind\":1,\"content\":\"nip grant\",\"tags\":[],\"created_at\":${TimeUtils.now()}}"""
        val content = client.nip44Encrypt("""{"id":"nip-1","method":"sign_event","params":["$template"]}""", connectionHex)
        AmberDesktop.engine.consume(client.signerSync.sign(TimeUtils.now(), 24133, arrayOf(arrayOf("p", connectionHex)), content), relay)

        withTimeout(20.seconds) {
            while (store.history.value.none { it.appKey == client.pubKey && it.accepted }) delay(50.milliseconds)
        }
        assertFalse(AmberDesktop.engine.pending.value.any { it.account.npub == account.npub })
    }
}
