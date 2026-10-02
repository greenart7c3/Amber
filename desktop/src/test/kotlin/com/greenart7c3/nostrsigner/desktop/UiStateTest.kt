package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.EncryptionScope
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.SignerType
import com.greenart7c3.nostrsigner.desktop.ui.UiState
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

/** Selection + remember-cycling logic behind the incoming-requests shortcuts. */
class UiStateTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun isolateDataDir() {
            val tmp = File.createTempFile("amber-uistate", "").apply {
                delete()
                mkdirs()
                deleteOnExit()
            }
            System.setProperty("user.home", tmp.absolutePath)
        }
    }

    @Before
    fun reset() {
        AmberDesktop.engine.pending.value = emptyList()
        UiState.selectedRequestId.value = null
        UiState.rememberChoices.value = emptyMap()
        UiState.scopeChoices.value = emptyMap()
    }

    @Test
    fun scopeShortcutTogglesEncryptionScope() = runBlocking {
        val account = com.greenart7c3.nostrsigner.desktop.core.AccountManager.addAccount(
            com.vitorpamplona.quartz.nip01Core.crypto.KeyPair(),
        )
        fun req(id: String, type: SignerType) = PendingBunkerRequest(
            request = BunkerRequest(id, type.name.lowercase(), arrayOf()),
            type = type,
            account = account,
            localKey = "k$id",
            relays = emptyList(),
        )
        val v2 = req("v2", SignerType.NIP44_DECRYPT)
        val v3 = req("v3", SignerType.NIP44_V3_ENCRYPT)
        val sign = req("sign", SignerType.SIGN_EVENT)
        AmberDesktop.engine.pending.value = listOf(v2, v3, sign)

        // Android defaults: NIP-04/44 all methods, NIP-44 v3 this kind only.
        assertEquals(EncryptionScope.ALL, UiState.scopeChoiceFor(v2))
        assertEquals(EncryptionScope.SPECIFIC, UiState.scopeChoiceFor(v3))

        UiState.selectedRequestId.value = "v2"
        assertTrue(UiState.toggleSelectedScope())
        assertEquals(EncryptionScope.SPECIFIC, UiState.scopeChoiceFor(v2))
        assertTrue(UiState.toggleSelectedScope())
        assertEquals(EncryptionScope.ALL, UiState.scopeChoiceFor(v2))

        UiState.selectedRequestId.value = "v3"
        assertTrue(UiState.toggleSelectedScope())
        assertEquals(EncryptionScope.ALL, UiState.scopeChoiceFor(v3))

        // Not an encrypt/decrypt request: the key is left alone.
        UiState.selectedRequestId.value = "sign"
        assertFalse(UiState.toggleSelectedScope())
    }

    @Test
    fun pruneExpiredDropsOnlyStaleRequests() = runBlocking {
        val account = com.greenart7c3.nostrsigner.desktop.core.AccountManager.addAccount(
            com.vitorpamplona.quartz.nip01Core.crypto.KeyPair(),
        )
        fun req(id: String, expiresAt: Long?) = PendingBunkerRequest(
            request = BunkerRequest(id, "sign_event", arrayOf()),
            type = SignerType.SIGN_EVENT,
            account = account,
            localKey = "k$id",
            relays = emptyList(),
            expiresAt = expiresAt,
        )
        AmberDesktop.engine.pending.value = listOf(req("stale", 100), req("fresh", 200), req("uri", null))

        AmberDesktop.engine.pruneExpired(now = 150)

        // Expired relay request goes; unexpired and never-expiring (nostrconnect://) stay.
        assertEquals(listOf("fresh", "uri"), AmberDesktop.engine.pending.value.map { it.request.id })
    }

    @Test
    fun upDownSelectionClampsAtEnds() = runBlocking {
        // Needs a real account for PendingBunkerRequest; create one.
        val account = com.greenart7c3.nostrsigner.desktop.core.AccountManager.addAccount(
            com.vitorpamplona.quartz.nip01Core.crypto.KeyPair(),
        )
        val reqs = listOf("a", "b", "c").map {
            PendingBunkerRequest(
                request = BunkerRequest(it, "sign_event", arrayOf()),
                type = SignerType.SIGN_EVENT,
                account = account,
                localKey = "k$it",
                relays = emptyList(),
            )
        }
        AmberDesktop.engine.pending.value = reqs
        UiState.selectedRequestId.value = "a"

        UiState.moveRequestSelection(1)
        assertEquals("b", UiState.selectedRequestId.value)
        UiState.moveRequestSelection(1)
        assertEquals("c", UiState.selectedRequestId.value)
        UiState.moveRequestSelection(1) // clamps at the last item
        assertEquals("c", UiState.selectedRequestId.value)
        UiState.moveRequestSelection(-5) // clamps at the first
        assertEquals("a", UiState.selectedRequestId.value)
    }

    @Test
    fun rememberChoiceCyclesAndWrapsForNonConnect() = runBlocking {
        val account = com.greenart7c3.nostrsigner.desktop.core.AccountManager.addAccount(
            com.vitorpamplona.quartz.nip01Core.crypto.KeyPair(),
        )
        val req = PendingBunkerRequest(
            request = BunkerRequest("x", "sign_event", arrayOf()),
            type = SignerType.SIGN_EVENT,
            account = account,
            localKey = "kx",
            relays = emptyList(),
        )
        AmberDesktop.engine.pending.value = listOf(req)
        UiState.selectedRequestId.value = "x"

        assertEquals(RememberType.NEVER, UiState.rememberChoiceFor("x"))
        UiState.cycleRememberChoice(1)
        assertEquals(RememberType.FIVE_MINUTES, UiState.rememberChoiceFor("x"))
        // Wrap backwards from the first entry to the last.
        UiState.setRememberChoice("x", RememberType.NEVER)
        UiState.cycleRememberChoice(-1)
        assertEquals(RememberType.ALWAYS, UiState.rememberChoiceFor("x"))
    }

    @Test
    fun connectRequestsIgnoreRememberCycling() = runBlocking {
        val account = com.greenart7c3.nostrsigner.desktop.core.AccountManager.addAccount(
            com.vitorpamplona.quartz.nip01Core.crypto.KeyPair(),
        )
        val req = PendingBunkerRequest(
            request = BunkerRequest("c", "connect", arrayOf()),
            type = SignerType.CONNECT,
            account = account,
            localKey = "kc",
            relays = emptyList(),
        )
        AmberDesktop.engine.pending.value = listOf(req)
        UiState.selectedRequestId.value = "c"

        UiState.cycleRememberChoice(1)
        assertEquals(RememberType.NEVER, UiState.rememberChoiceFor("c")) // unchanged
    }

    @Test
    fun pruneDropsStaleSelectionAndChoices() {
        UiState.selectedRequestId.value = "gone"
        UiState.rememberChoices.value = mapOf("gone" to RememberType.ALWAYS)
        UiState.pruneRequestState(emptyList())
        assertNull(UiState.selectedRequestId.value)
        assertEquals(emptyMap<String, RememberType>(), UiState.rememberChoices.value)
    }
}
