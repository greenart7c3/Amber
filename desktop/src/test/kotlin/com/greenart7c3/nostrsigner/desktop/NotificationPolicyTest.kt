package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.common.APP_NAME_MAX
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.sanitizeAppName
import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AccountRecord
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.allowsQuickApprove
import com.greenart7c3.nostrsigner.desktop.core.notificationAccountLine
import com.greenart7c3.nostrsigner.desktop.core.notificationTitle
import com.greenart7c3.nostrsigner.desktop.core.toShortenHex
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * A notification or popup shows only the client-chosen app name, so it may
 * offer one-click Approve only for low-risk requests, and names the signing
 * account when there is more than one.
 */
class NotificationPolicyTest {
    companion object {
        private lateinit var account: DesktopAccount

        @JvmStatic
        @BeforeClass
        fun setUp() {
            val tmp = File.createTempFile("amber-notify-policy", "").apply {
                delete()
                mkdirs()
                deleteOnExit()
            }
            System.setProperty("user.home", tmp.absolutePath)
            account = runBlocking { AccountManager.addAccount(KeyPair(), name = "main") }
        }
    }

    private fun req(type: SignerType, kind: Int? = null) = PendingBunkerRequest(
        request = BunkerRequest(type.name, type.name.lowercase(), arrayOf()),
        type = type,
        account = account,
        localKey = "k",
        relays = emptyList(),
        kind = kind,
    )

    @Test
    fun lowRiskRequestsCanBeApprovedFromANotification() {
        assertTrue(req(SignerType.SIGN_EVENT, kind = 1).allowsQuickApprove())
        assertTrue(req(SignerType.SIGN_EVENT, kind = 7).allowsQuickApprove())
        assertTrue(req(SignerType.NIP44_ENCRYPT).allowsQuickApprove())
        assertTrue(req(SignerType.NIP04_ENCRYPT).allowsQuickApprove())
        assertTrue(req(SignerType.GET_PUBLIC_KEY).allowsQuickApprove())
    }

    @Test
    fun sensitiveRequestsNeedTheirCard() {
        listOf(0, 3, 5, 10002, 22242, 27235, 30078).forEach {
            assertFalse("kind $it", req(SignerType.SIGN_EVENT, kind = it).allowsQuickApprove())
        }
        listOf(
            SignerType.NIP04_DECRYPT,
            SignerType.NIP44_DECRYPT,
            SignerType.NIP44_V3_DECRYPT,
            SignerType.DECRYPT_ZAP_EVENT,
            SignerType.SIGN_PSBT,
            SignerType.CONNECT,
            SignerType.SWITCH_RELAYS,
        ).forEach { assertFalse(it.name, req(it).allowsQuickApprove()) }
        // A sign_event whose kind could not be read is not low-risk either.
        assertFalse(req(SignerType.SIGN_EVENT, kind = null).allowsQuickApprove())
    }

    @Test
    fun theAccountIsNamedOnlyWhenThereIsMoreThanOne() {
        val mine = AccountRecord(npub = account.npub, name = "main")
        val other = AccountRecord(npub = "npub1other", name = "other")

        assertNull(req(SignerType.SIGN_EVENT, 1).notificationAccountLine(listOf(mine), "en"))
        assertEquals(
            "Account: main · ${account.npub.toShortenHex()}",
            req(SignerType.SIGN_EVENT, 1).notificationAccountLine(listOf(mine, other), "en"),
        )
    }

    @Test
    fun aClientChosenNameCannotFakeLinesOrDirection() {
        // Line breaks and tabs become single spaces: no fake second line.
        assertEquals("Wallet Amber: approve to continue", sanitizeAppName("Wallet\nAmber:\tapprove  to continue"))
        // Bidi overrides and zero-width characters are dropped.
        assertEquals("evilgpj.exe", sanitizeAppName("evil\u202Egpj.exe\u200B"))
        assertEquals("ab", sanitizeAppName("a\u0000\u0007b"))
        // Ordinary names, including non-Latin ones, are kept as they are.
        assertEquals("Amethyst", sanitizeAppName("  Amethyst  "))
        assertEquals("ノストラ 🟣", sanitizeAppName("ノストラ 🟣"))
    }

    @Test
    fun aLongNameIsCut() {
        val cut = sanitizeAppName("x".repeat(200))
        assertEquals(APP_NAME_MAX, cut.codePointCount(0, cut.length))
        assertTrue(cut.endsWith("…"))
    }

    @Test
    fun aNameThatSanitizesToNothingFallsBackToTheKey() {
        val blank = req(SignerType.SIGN_EVENT, 1).copy(appName = "\u202E\u200B\n", localKey = "ab".repeat(32))
        assertEquals("ab".repeat(32).toShortenHex(), blank.notificationTitle())
    }
}
