package com.greenart7c3.nostrsigner.desktop.ui

import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.EncryptedContent
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.SignerType
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
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

/** What the incoming-request card summarizes and its "See details" dialog shows. */
class RequestDetailsTest {
    companion object {
        private lateinit var account: DesktopAccount

        @JvmStatic
        @BeforeClass
        fun setUp() {
            val tmp = File.createTempFile("amber-details", "").apply {
                delete()
                mkdirs()
                deleteOnExit()
            }
            System.setProperty("user.home", tmp.absolutePath)
            account = runBlocking { AccountManager.addAccount(KeyPair()) }
        }
    }

    @Before
    fun reset() {
        AmberDesktop.engine.pending.value = emptyList()
        UiState.selectedRequestId.value = null
        UiState.detailsRequestId.value = null
    }

    private fun req(
        type: SignerType,
        preview: String = "",
        result: String = "",
        id: String = type.name,
    ) = PendingBunkerRequest(
        request = BunkerRequest(id, type.name.lowercase(), arrayOf()),
        type = type,
        account = account,
        localKey = "k$id",
        relays = emptyList(),
        preview = preview,
        result = result,
        encryptedContent = EncryptedContent.classify(preview),
    )

    private val note = """{"kind":1,"content":"gm nostr","tags":[["t","amber"],["p","abc"]],"created_at":1700000000}"""

    @Test
    fun signEventShowsTheContentAndDetailsTheSignedEvent() {
        val signed = """{"id":"00","pubkey":"${account.hexKey}","created_at":1700000000,"kind":1,"tags":[["t","amber"]],"content":"gm nostr","sig":"00"}"""
        val details = RequestDetails.of(req(SignerType.SIGN_EVENT, preview = note, result = signed))
        assertTrue(details is RequestDetails.OfEvent)
        assertEquals("gm nostr", details!!.summary)
        val event = (details as RequestDetails.OfEvent).event
        assertEquals(account.hexKey, event.pubKey)
        assertEquals(1700000000L, event.createdAt)
        assertEquals(listOf(listOf("t", "amber")), event.tags)
    }

    @Test
    fun authEventsSummarizeTheRelay() {
        val auth = """{"kind":22242,"content":"","tags":[["relay","wss://relay.example"],["challenge","x"]],"created_at":1700000000}"""
        assertEquals("wss://relay.example", RequestDetails.of(req(SignerType.SIGN_EVENT, preview = auth))!!.summary)
    }

    @Test
    fun encryptedPayloadsAreShownByWhatTheyHold() {
        val event = RequestDetails.of(req(SignerType.NIP44_DECRYPT, preview = note))
        assertTrue(event is RequestDetails.OfEvent)
        assertEquals("gm nostr", event!!.summary)

        val tags = RequestDetails.of(req(SignerType.NIP04_ENCRYPT, preview = """[["p","abc"],["e","def"]]"""))
        assertTrue(tags is RequestDetails.OfTags)
        assertEquals("""["p", "abc"], ["e", "def"]""", tags!!.summary)

        val text = RequestDetails.of(req(SignerType.NIP44_V3_ENCRYPT, preview = "hello"))
        assertEquals(RequestDetails.OfText("hello"), text)
    }

    @Test
    fun requestsWithoutContentHaveNoDetails() {
        assertNull(RequestDetails.of(req(SignerType.CONNECT)))
        assertNull(RequestDetails.of(req(SignerType.GET_PUBLIC_KEY, preview = account.npub)))
    }

    @Test
    fun detailsShortcutOpensOnlyForRequestsWithDetails() {
        val connect = req(SignerType.CONNECT)
        val sign = req(SignerType.SIGN_EVENT, preview = note)
        AmberDesktop.engine.pending.value = listOf(connect, sign)

        UiState.selectedRequestId.value = connect.request.id
        assertFalse(UiState.showSelectedDetails())
        assertNull(UiState.detailsRequestId.value)

        UiState.selectedRequestId.value = sign.request.id
        assertTrue(UiState.showSelectedDetails())
        assertEquals(sign.request.id, UiState.detailsRequestId.value)

        // Answered elsewhere (shortcut, notification, expiry): the dialog closes.
        UiState.pruneRequestState(listOf(connect))
        assertNull(UiState.detailsRequestId.value)
    }
}
