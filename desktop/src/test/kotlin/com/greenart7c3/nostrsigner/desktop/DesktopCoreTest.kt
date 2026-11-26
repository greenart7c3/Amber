package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.generateBunkerPrivKey
import com.greenart7c3.nostrsigner.common.localPubKeyFromPrivKey
import com.greenart7c3.nostrsigner.common.relays.SenderRateLimiter
import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AccountStore
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.AppPermissionRecord
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.DesktopKeyStore
import com.greenart7c3.nostrsigner.desktop.core.DesktopSettings
import com.greenart7c3.nostrsigner.desktop.core.HistoryRecord
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.addPending
import com.greenart7c3.nostrsigner.desktop.core.constantTimeEquals
import com.greenart7c3.nostrsigner.desktop.core.isRemembered
import com.greenart7c3.nostrsigner.desktop.core.removePending
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.utils.TimeUtils
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class DesktopCoreTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun isolateDataDirFromARealInstall() {
            val tmp = File.createTempFile("amber-test", "").apply {
                delete()
                mkdirs()
                deleteOnExit()
            }
            System.setProperty("user.home", tmp.absolutePath)
        }
    }

    @Test
    fun keystoreEncryptsAndDecrypts() = runBlocking {
        val secret = "nsec-super-secret-payload"
        val encrypted = DesktopKeyStore.encrypt(secret, "test/secret")
        assertTrue(encrypted != secret)
        assertEquals(secret, DesktopKeyStore.decrypt(encrypted, "test/secret"))
    }

    @Test
    fun freshInstallsLockAfterOneHour() {
        assertEquals(60, DesktopSettings().autoLockMinutes)
    }

    @Test
    fun defaultRelaysMatchTheAndroidApp() {
        assertEquals(
            "must match defaultAppRelays in the Android AmberSettings.kt",
            listOf(
                "wss://auth.nostr1.com/",
                "wss://bucket.coracle.social/",
                "wss://nrs.primal.net/",
                "wss://relay.nip46.com/",
            ),
            DesktopSettings().defaultRelays,
        )
        assertEquals(4, DesktopSettings().normalizedDefaultRelays().size)
    }

    @Test
    fun parsesHexAndNsecKeys() {
        val hex = generateBunkerPrivKey()
        val fromHex = AccountManager.parseKey(hex).getOrThrow()
        assertEquals(hex, fromHex.privKey!!.toHexKey())

        val nsec = fromHex.privKey!!.toNsec()
        val fromNsec = AccountManager.parseKey(nsec).getOrThrow()
        assertEquals(hex, fromNsec.privKey!!.toHexKey())
    }

    @Test
    fun rejectsGarbageKeys() {
        assertTrue(AccountManager.parseKey("not-a-key").isFailure)
    }

    @Test
    fun generatedSeedWordsProduceAKey() {
        val words = AccountManager.generateSeedWords()
        assertEquals(12, words.size)
        val keyPair = AccountManager.parseKey(words.joinToString(" ")).getOrThrow()
        assertNotNull(keyPair.privKey)
    }

    @Test
    fun localPubKeyDerivation() {
        val priv = generateBunkerPrivKey()
        val pub = localPubKeyFromPrivKey(priv)
        assertEquals(64, pub.length)
    }

    @Test
    fun isRememberedMirrorsAndroidBehavior() {
        assertEquals("sign policy 2 always auto-accepts", true, isRemembered(2, null))
        assertNull("no stored permission asks the user", isRemembered(1, null))

        val accepted = AppPermissionRecord("SIGN_EVENT", 1, true, RememberType.ALWAYS.screenCode, Long.MAX_VALUE / 1000, 0)
        assertEquals(true, isRemembered(1, accepted))

        assertEquals(false, isRemembered(1, rejectedAlways))

        val expired = AppPermissionRecord("SIGN_EVENT", 1, true, RememberType.FIVE_MINUTES.screenCode, TimeUtils.now() - 10, 0)
        assertNull(isRemembered(1, expired))
    }

    private val rejectedAlways = AppPermissionRecord("SIGN_EVENT", 1, false, RememberType.ALWAYS.screenCode, 0, Long.MAX_VALUE / 1000)

    @Test
    fun `an active rejection wins over the fully trust sign policy`() {
        assertEquals(false, isRemembered(2, rejectedAlways))
    }

    @Test
    fun `a leftover timestamp on the side the grant did not choose decides nothing`() {
        val denialWithLeftoverAccept = AppPermissionRecord("SIGN_EVENT", 1, false, RememberType.ALWAYS.screenCode, Long.MAX_VALUE / 1000, 0)
        assertNull(isRemembered(1, denialWithLeftoverAccept))
        val acceptWithLeftoverReject = AppPermissionRecord("SIGN_EVENT", 1, true, RememberType.ALWAYS.screenCode, 0, Long.MAX_VALUE / 1000)
        assertNull(isRemembered(1, acceptWithLeftoverReject))
    }

    private fun pingReusingRequestIdX(localKey: String, account: DesktopAccount) = PendingBunkerRequest(
        request = BunkerRequest("X", "ping", emptyArray()),
        type = SignerType.PING,
        account = account,
        localKey = localKey,
        relays = emptyList(),
    )

    @Test
    fun pendingQueueDedupsOnAccountConnectionAndId() = runBlocking {
        val engine = AmberDesktop.engine
        val accountA = AccountManager.addAccount(KeyPair(), name = "dedup-a")
        val accountB = AccountManager.addAccount(KeyPair(), name = "dedup-b")
        val first = pingReusingRequestIdX("conn-one", accountA)

        engine.addPending(first)
        engine.addPending(pingReusingRequestIdX("conn-two", accountA))
        engine.addPending(pingReusingRequestIdX("conn-one", accountB))
        assertEquals(
            "a client-chosen id arriving for another connection or account must not be swallowed by the first",
            3,
            engine.pending.value.count { it.request.id == "X" },
        )

        engine.addPending(first.copy())
        assertEquals("the very same account, connection and id is still collapsed", 3, engine.pending.value.count { it.request.id == "X" })

        engine.removePending(first)
        assertEquals("removal matches the full key, not the bare client id", 2, engine.pending.value.count { it.request.id == "X" })
        engine.removePending(pingReusingRequestIdX("conn-two", accountA))
        engine.removePending(pingReusingRequestIdX("conn-one", accountB))
        assertEquals(0, engine.pending.value.count { it.request.id == "X" })
    }

    @Test
    fun rateLimiterDropsBurstsAndBoundsItsMap() {
        val limiter = SenderRateLimiter(capacity = 3, windowSeconds = 60, maxSenders = 4)
        val start = 1_000_000L
        val sender = "wss://relay|senderpubkey"

        repeat(3) { assertTrue("a fresh sender gets its whole budget at once", limiter.allow(sender, start)) }
        assertFalse("burst beyond budget dropped", limiter.allow(sender, start))

        assertTrue("half a window later one token is back", limiter.allow(sender, start + 30_000))
        assertFalse("half a window later only one token is back", limiter.allow(sender, start + 30_000))

        assertTrue("a full window restores the whole budget", limiter.allow(sender, start + 90_000))

        repeat(6) { limiter.allow("wss://relay|sender-$it", start) }
        assertTrue("senders are evicted oldest-first, so the map stays bounded", limiter.trackedSenders <= 4)
    }

    @Test
    fun `a fresh key per event does not evade the aggregate rate limit`() {
        val limiter = SenderRateLimiter(capacity = 10, windowSeconds = 60, aggregateCapacity = 20)
        val start = 1_000_000L

        assertEquals(20, (0 until 500).count { limiter.allow("wss://relay|fresh-$it", start) })
    }

    @Test
    fun `one noisy sender only spends its own share of the aggregate rate limit`() {
        val start = 1_000_000L
        val other = SenderRateLimiter(capacity = 10, windowSeconds = 60, aggregateCapacity = 20)
        repeat(1000) { other.allow("wss://relay|noisy", start) }
        assertEquals(10, (0 until 10).count { other.allow("wss://relay|newcomer-$it", start) })
    }

    @Test
    fun constantTimeEqualsComparesFullBytes() {
        assertTrue(constantTimeEquals("same-secret", "same-secret"))
        assertTrue(constantTimeEquals("", ""))
        assertFalse(constantTimeEquals("same-secret", "differ-secret"))
        assertFalse(constantTimeEquals("same-secret", "same-secretx"))
        assertFalse(constantTimeEquals("same-secretx", "same-secret"))
    }

    @Test
    fun logWritesAreDebouncedUntilFlush() = runBlocking {
        val account = AccountManager.addAccount(KeyPair(), name = "debounce")
        val store = AmberDesktop.store(account.npub)
        store.addLog("wss://relay", "bunker", "volatile line")
        store.addHistory(HistoryRecord("app-key", "ping", null, TimeUtils.now(), true))

        assertTrue("debounced logs must not hit the disk before the flush", AccountStore(account.npub).logs.value.isEmpty())
        assertTrue("debounced history must not hit the disk before the flush", AccountStore(account.npub).history.value.isEmpty())

        store.flushVolatile()
        assertEquals(listOf("volatile line"), AccountStore(account.npub).logs.value.map { it.message })
        assertEquals(listOf("ping"), AccountStore(account.npub).history.value.map { it.type })
    }
}
