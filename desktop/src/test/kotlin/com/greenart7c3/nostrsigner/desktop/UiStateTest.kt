package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.SignerType
import com.greenart7c3.nostrsigner.desktop.ui.UiState
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
