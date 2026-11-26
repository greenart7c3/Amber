package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.EncryptionScope
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.pruneExpired
import com.greenart7c3.nostrsigner.desktop.ui.BatchDecisions
import com.greenart7c3.nostrsigner.desktop.ui.RequestChoices
import com.greenart7c3.nostrsigner.desktop.ui.RequestKeyboard
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
        RequestChoices.rememberChoices.value = emptyMap()
        RequestChoices.scopeChoices.value = emptyMap()
        BatchDecisions.batchApproved.value = emptyMap()
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

        assertEquals("NIP-04 and NIP-44 default to all methods, as on Android", EncryptionScope.ALL, RequestChoices.scopeChoiceFor(v2))
        assertEquals("NIP-44 v3 defaults to this kind only, as on Android", EncryptionScope.SPECIFIC, RequestChoices.scopeChoiceFor(v3))

        UiState.selectedRequestId.value = v2.queueKey
        assertTrue(RequestKeyboard.toggleSelectedScope())
        assertEquals(EncryptionScope.SPECIFIC, RequestChoices.scopeChoiceFor(v2))
        assertTrue(RequestKeyboard.toggleSelectedScope())
        assertEquals(EncryptionScope.ALL, RequestChoices.scopeChoiceFor(v2))

        UiState.selectedRequestId.value = v3.queueKey
        assertTrue(RequestKeyboard.toggleSelectedScope())
        assertEquals(EncryptionScope.ALL, RequestChoices.scopeChoiceFor(v3))

        UiState.selectedRequestId.value = sign.queueKey
        assertFalse("the scope key leaves a request that is not encrypt or decrypt alone", RequestKeyboard.toggleSelectedScope())
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

        assertEquals(
            "the expired relay request goes, the unexpired and the never-expiring nostrconnect one stay",
            listOf("fresh", "uri"),
            AmberDesktop.engine.pending.value.map { it.request.id },
        )
    }

    @Test
    fun upDownSelectionClampsAtEnds() = runBlocking {
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
        UiState.selectedRequestId.value = reqs[0].queueKey

        UiState.moveRequestSelection(1)
        assertEquals(reqs[1].queueKey, UiState.selectedRequestId.value)
        UiState.moveRequestSelection(1)
        assertEquals(reqs[2].queueKey, UiState.selectedRequestId.value)
        UiState.moveRequestSelection(1)
        assertEquals("clamps at the last item", reqs[2].queueKey, UiState.selectedRequestId.value)
        UiState.moveRequestSelection(-5)
        assertEquals("clamps at the first item", reqs[0].queueKey, UiState.selectedRequestId.value)
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
        UiState.selectedRequestId.value = req.queueKey

        assertEquals(RememberType.NEVER, RequestChoices.rememberChoiceFor(req.queueKey))
        RequestKeyboard.cycleRememberChoice(1)
        assertEquals(RememberType.FIVE_MINUTES, RequestChoices.rememberChoiceFor(req.queueKey))
        RequestChoices.setRememberChoice(req.queueKey, RememberType.NEVER)
        RequestKeyboard.cycleRememberChoice(-1)
        assertEquals("wraps backwards from the first entry to the last", RememberType.ALWAYS, RequestChoices.rememberChoiceFor(req.queueKey))
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
        UiState.selectedRequestId.value = req.queueKey

        RequestKeyboard.cycleRememberChoice(1)
        assertEquals("a connect's remember choice stays unchanged", RememberType.NEVER, RequestChoices.rememberChoiceFor(req.queueKey))
    }

    private class BatchRequests(private val account: DesktopAccount) {
        private fun req(id: String, type: SignerType, localKey: String, kind: Int? = null) = PendingBunkerRequest(
            request = BunkerRequest(id, type.name.lowercase(), arrayOf()),
            type = type,
            account = account,
            localKey = localKey,
            relays = emptyList(),
            kind = kind,
        )

        val note = req("n1", SignerType.SIGN_EVENT, "app1", kind = 1)
        val decrypt = req("d1", SignerType.NIP44_DECRYPT, "app1")
        val note2 = req("n2", SignerType.SIGN_EVENT, "app1", kind = 1)
        val other = req("o1", SignerType.SIGN_EVENT, "app2", kind = 7)
        val connect = req("c1", SignerType.CONNECT, "app3")

        fun queueWithoutConnect() {
            AmberDesktop.engine.pending.value = listOf(decrypt, note, other, note2)
        }
    }

    private suspend fun batchRequests() = BatchRequests(
        com.greenart7c3.nostrsigner.desktop.core.AccountManager.addAccount(
            com.vitorpamplona.quartz.nip01Core.crypto.KeyPair(),
        ),
    )

    @Test
    fun connectOrOnlyRequestIsShownAloneElseBatched() = runBlocking {
        val r = batchRequests()

        assertTrue(com.greenart7c3.nostrsigner.desktop.ui.singleRequest(listOf(r.note)) === r.note)
        assertNull(com.greenart7c3.nostrsigner.desktop.ui.singleRequest(listOf(r.note, r.decrypt)))
        assertTrue(
            "a connect is never batched, it is shown alone first",
            com.greenart7c3.nostrsigner.desktop.ui.singleRequest(listOf(r.note, r.connect, r.decrypt)) === r.connect,
        )
    }

    @Test
    fun `a batch groups per app in arrival order then per type with sign before decrypt and kinds together`() = runBlocking {
        val r = batchRequests()

        val apps = com.greenart7c3.nostrsigner.desktop.ui.batchApps(listOf(r.decrypt, r.note, r.other, r.note2, r.connect))
        assertEquals(2, apps.size)
        assertEquals(listOf(listOf(r.note, r.note2), listOf(r.decrypt)), apps[0].groups.map { it.requests })
        assertEquals(listOf(r.other), apps[1].requests)
    }

    @Test
    fun `in a batch the remember shortcut sets the selected request's whole group only`() = runBlocking {
        val r = batchRequests()
        r.queueWithoutConnect()
        UiState.selectedRequestId.value = r.note2.queueKey

        RequestKeyboard.cycleRememberChoice(1)

        assertEquals(RememberType.FIVE_MINUTES, RequestChoices.rememberChoiceFor(r.note.queueKey))
        assertEquals(RememberType.FIVE_MINUTES, RequestChoices.rememberChoiceFor(r.note2.queueKey))
        assertEquals(RememberType.NEVER, RequestChoices.rememberChoiceFor(r.decrypt.queueKey))
    }

    @Test
    fun `moving down in a batch follows the screen order note note2 decrypt other`() = runBlocking {
        val r = batchRequests()
        r.queueWithoutConnect()
        UiState.selectedRequestId.value = r.note2.queueKey

        UiState.moveRequestSelection(1)

        assertEquals(r.decrypt.queueKey, UiState.selectedRequestId.value)
    }

    @Test
    fun `only shown batch requests get a decision and Space flips the selected one`() = runBlocking {
        val r = batchRequests()
        r.queueWithoutConnect()
        UiState.selectedRequestId.value = r.decrypt.queueKey

        assertFalse("a request the batch screen has not shown gets no decision", RequestKeyboard.toggleSelectedBatchApproval())
        BatchDecisions.seedBatch(listOf(r.decrypt, r.note))
        assertTrue(RequestKeyboard.toggleSelectedBatchApproval())
        assertEquals(mapOf(r.decrypt.queueKey to false, r.note.queueKey to true), BatchDecisions.batchApproved.value)
    }

    @Test
    fun `Shift+Space approves a partly denied section, then denies it all, leaving other sections untouched`() = runBlocking {
        val r = batchRequests()
        r.queueWithoutConnect()
        BatchDecisions.setBatchApproved(listOf(r.decrypt), false)
        BatchDecisions.seedBatch(listOf(r.decrypt, r.note, r.other, r.note2))
        BatchDecisions.setBatchApproved(listOf(r.note), false)
        UiState.selectedRequestId.value = r.note2.queueKey

        assertTrue(RequestKeyboard.toggleSelectedBatchApproval(wholeGroup = true))
        assertTrue(BatchDecisions.batchApproved.value[r.note.queueKey] == true && BatchDecisions.batchApproved.value[r.note2.queueKey] == true)
        assertTrue(RequestKeyboard.toggleSelectedBatchApproval(wholeGroup = true))
        assertTrue(BatchDecisions.batchApproved.value[r.note.queueKey] == false && BatchDecisions.batchApproved.value[r.note2.queueKey] == false)
        assertEquals("other section untouched", false, BatchDecisions.batchApproved.value[r.decrypt.queueKey])
    }

    @Test
    fun `pruning drops the batch decisions of answered requests`() = runBlocking {
        val r = batchRequests()
        r.queueWithoutConnect()
        BatchDecisions.seedBatch(listOf(r.decrypt, r.note, r.other, r.note2))
        BatchDecisions.setBatchApproved(listOf(r.note), false)

        UiState.pruneRequestState(listOf(r.note))

        assertEquals(mapOf(r.note.queueKey to false), BatchDecisions.batchApproved.value)
    }

    @Test
    fun pruneDropsStaleSelectionAndChoices() {
        UiState.selectedRequestId.value = "gone"
        RequestChoices.rememberChoices.value = mapOf("gone" to RememberType.ALWAYS)
        UiState.pruneRequestState(emptyList())
        assertNull(UiState.selectedRequestId.value)
        assertEquals(emptyMap<String, RememberType>(), RequestChoices.rememberChoices.value)
    }

    @Test
    fun collidingClientIdsKeepSelectionAndChoicesSeparate() = runBlocking {
        val account = com.greenart7c3.nostrsigner.desktop.core.AccountManager.addAccount(
            com.vitorpamplona.quartz.nip01Core.crypto.KeyPair(),
        )
        fun req(localKey: String) = PendingBunkerRequest(
            request = BunkerRequest("shared-id", "sign_event", arrayOf()),
            type = SignerType.SIGN_EVENT,
            account = account,
            localKey = localKey,
            relays = emptyList(),
        )
        val clientA = req("conn-a")
        val clientB = req("conn-b")
        assertTrue("both cards carry the same client-chosen id", clientA.request.id == clientB.request.id)
        assertTrue("but their queue keys differ", clientA.queueKey != clientB.queueKey)
        assertTrue(clientA.queueKey.endsWith("|shared-id"))
        AmberDesktop.engine.pending.value = listOf(clientA, clientB)

        UiState.selectedRequestId.value = clientB.queueKey
        assertTrue("both entries are addressable even though their request ids match", UiState.selectedRequest() === clientB)

        RequestChoices.setRememberChoice(clientB.queueKey, RememberType.ALWAYS)
        assertEquals("a remember choice set on B's card never applies to A's", RememberType.NEVER, RequestChoices.rememberChoiceFor(clientA.queueKey))
        assertEquals(RememberType.ALWAYS, RequestChoices.rememberChoiceFor(clientB.queueKey))

        UiState.moveRequestSelection(-1)
        assertEquals("keyboard selection moves between the two distinct entries", clientA.queueKey, UiState.selectedRequestId.value)
        assertEquals(clientA, UiState.selectedRequest())

        UiState.pruneRequestState(listOf(clientA, clientB))
        assertEquals("pruning keeps choices whose queue key still exists", RememberType.ALWAYS, RequestChoices.rememberChoiceFor(clientB.queueKey))
    }
}
