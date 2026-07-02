package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AppPermissionRecord
import com.greenart7c3.nostrsigner.desktop.core.BunkerEngine
import com.greenart7c3.nostrsigner.desktop.core.DesktopKeyStore
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.SignerType
import com.greenart7c3.nostrsigner.desktop.core.generateBunkerPrivKey
import com.greenart7c3.nostrsigner.desktop.core.isRemembered
import com.greenart7c3.nostrsigner.desktop.core.localPubKeyFromPrivKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.utils.TimeUtils
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
        val encrypted = DesktopKeyStore.encrypt(secret)
        assertTrue(encrypted != secret)
        assertEquals(secret, DesktopKeyStore.decrypt(encrypted))
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
    }

    @Test
    fun bunkerMethodMapping() {
        assertEquals(SignerType.CONNECT, BunkerEngine.typeFromMethod("connect"))
        assertEquals(SignerType.SIGN_EVENT, BunkerEngine.typeFromMethod("sign_event"))
        assertEquals(SignerType.NIP44_DECRYPT, BunkerEngine.typeFromMethod("nip44_decrypt"))
        assertEquals(SignerType.INVALID, BunkerEngine.typeFromMethod("bogus"))
    }
}
