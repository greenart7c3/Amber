package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.generateBunkerPrivKey
import com.greenart7c3.nostrsigner.common.localPubKeyFromPrivKey
import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AccountStore
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.AppPermissionRecord
import com.greenart7c3.nostrsigner.desktop.core.BunkerEngine
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.DesktopKeyStore
import com.greenart7c3.nostrsigner.desktop.core.DesktopSettings
import com.greenart7c3.nostrsigner.desktop.core.HistoryRecord
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.SenderRateLimiter
import com.greenart7c3.nostrsigner.desktop.core.constantTimeEquals
import com.greenart7c3.nostrsigner.desktop.core.isRemembered
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
        fun isolateDataDir() {
            // Point the app data dir at a scratch location so tests never touch
            // a real install.
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
        // Keep in sync with `defaultAppRelays` in the Android AmberSettings.kt.
        assertEquals(
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
        // Sign policy 2 always auto-accepts.
        assertEquals(true, isRemembered(2, null))
        // No stored permission -> ask the user.
        assertNull(isRemembered(1, null))

        val accepted = AppPermissionRecord("SIGN_EVENT", 1, true, RememberType.ALWAYS.screenCode, Long.MAX_VALUE / 1000, 0)
        assertEquals(true, isRemembered(1, accepted))

        val rejected = AppPermissionRecord("SIGN_EVENT", 1, false, RememberType.ALWAYS.screenCode, 0, Long.MAX_VALUE / 1000)
        assertEquals(false, isRemembered(1, rejected))

        val expired = AppPermissionRecord("SIGN_EVENT", 1, true, RememberType.FIVE_MINUTES.screenCode, TimeUtils.now() - 10, 0)
        assertNull(isRemembered(1, expired))

        // An active rejection wins over the "fully trust" sign policy.
        assertEquals(false, isRemembered(2, rejected))

        // A stale timestamp on the side acceptable does not select never decides.
        val denialWithLeftoverAccept = AppPermissionRecord("SIGN_EVENT", 1, false, RememberType.ALWAYS.screenCode, Long.MAX_VALUE / 1000, 0)
        assertNull(isRemembered(1, denialWithLeftoverAccept))
        val acceptWithLeftoverReject = AppPermissionRecord("SIGN_EVENT", 1, true, RememberType.ALWAYS.screenCode, 0, Long.MAX_VALUE / 1000)
        assertNull(isRemembered(1, acceptWithLeftoverReject))
    }

    @Test
    fun bunkerMethodMapping() {
        assertEquals(SignerType.CONNECT, BunkerEngine.typeFromMethod("connect"))
        assertEquals(SignerType.SIGN_EVENT, BunkerEngine.typeFromMethod("sign_event"))
        assertEquals(SignerType.NIP44_DECRYPT, BunkerEngine.typeFromMethod("nip44_decrypt"))
        assertEquals(SignerType.INVALID, BunkerEngine.typeFromMethod("bogus"))
    }

    @Test
    fun permissionTypeExpansionMatchesRequestPath() {
        // NIP-specific perms map 1:1 to the type the request path queries.
        assertEquals(listOf("NIP44_ENCRYPT"), BunkerEngine.expandPermissionTypes("nip44_encrypt"))
        assertEquals(listOf("SIGN_EVENT"), BunkerEngine.expandPermissionTypes("sign_event"))
        assertEquals(listOf("GET_PUBLIC_KEY"), BunkerEngine.expandPermissionTypes("get_public_key"))

        // Amber's content-type perms are stored as-is, like Android, and the
        // request path checks them before the whole-NIP grant.
        assertEquals(listOf("ENCRYPT_EVENT"), BunkerEngine.expandPermissionTypes("encrypt_event"))
        assertEquals(listOf("DECRYPT_CLEAR_TEXT"), BunkerEngine.expandPermissionTypes("decrypt_clear_text"))
        assertEquals(listOf("ENCRYPT_TAG_ARRAY"), BunkerEngine.expandPermissionTypes("encrypt_tag_array"))

        // A generic encrypt/decrypt perm grants both NIPs.
        assertEquals(
            setOf("NIP04_ENCRYPT", "NIP44_ENCRYPT"),
            BunkerEngine.expandPermissionTypes("encrypt").toSet(),
        )
    }

    private fun pendingEntry(id: String, localKey: String, account: DesktopAccount) = PendingBunkerRequest(
        request = BunkerRequest(id, "ping", emptyArray()),
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
        val first = pendingEntry("X", "conn-one", accountA)

        engine.addPending(first)
        // Relay clients choose their own request ids: the same id arriving for
        // another connection (or account) must not be swallowed by the first.
        engine.addPending(pendingEntry("X", "conn-two", accountA))
        engine.addPending(pendingEntry("X", "conn-one", accountB))
        assertEquals(3, engine.pending.value.count { it.request.id == "X" })

        // The very same (account, connection, id) is still collapsed.
        engine.addPending(first.copy())
        assertEquals(3, engine.pending.value.count { it.request.id == "X" })

        // Removal matches the full key, not the bare client id.
        engine.removePending(first)
        assertEquals(2, engine.pending.value.count { it.request.id == "X" })
        engine.removePending(pendingEntry("X", "conn-two", accountA))
        engine.removePending(pendingEntry("X", "conn-one", accountB))
        assertEquals(0, engine.pending.value.count { it.request.id == "X" })
    }

    @Test
    fun rateLimiterDropsBurstsAndBoundsItsMap() {
        val limiter = SenderRateLimiter(capacity = 3, windowSeconds = 60, maxSenders = 4)
        val start = 1_000_000L
        val sender = "wss://relay|senderpubkey"

        // A fresh sender gets its whole budget at once, then drops the excess.
        repeat(3) { assertTrue("within budget", limiter.allow(sender, start)) }
        assertFalse("burst beyond budget dropped", limiter.allow(sender, start))

        // Steady refill: half a window later exactly one token is back.
        assertTrue(limiter.allow(sender, start + 30_000))
        assertFalse(limiter.allow(sender, start + 30_000))

        // A full window restores the whole budget.
        assertTrue(limiter.allow(sender, start + 90_000))

        // Senders are evicted oldest-first, so the map cannot grow unbounded.
        repeat(6) { limiter.allow("wss://relay|sender-$it", start) }
        assertTrue("map bounded", limiter.trackedSenders <= 4)
    }

    @Test
    fun rateLimiterCapsFreshKeysInTotal() {
        val limiter = SenderRateLimiter(capacity = 10, windowSeconds = 60, aggregateCapacity = 20)
        val start = 1_000_000L

        // A fresh key per event no longer evades the limit.
        assertEquals(20, (0 until 500).count { limiter.allow("wss://relay|fresh-$it", start) })

        // One noisy sender only spends its own budget of the shared one.
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

        // Nothing hit the disk yet: the writes are debounced.
        assertTrue(AccountStore(account.npub).logs.value.isEmpty())
        assertTrue(AccountStore(account.npub).history.value.isEmpty())

        store.flushVolatile()
        assertEquals(listOf("volatile line"), AccountStore(account.npub).logs.value.map { it.message })
        assertEquals(listOf("ping"), AccountStore(account.npub).history.value.map { it.type })
    }
}
