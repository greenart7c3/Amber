package com.greenart7c3.nostrsigner.ui.components

import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.EncryptionType
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Approving a nostrconnect URI whose client key matches a saved app used to
 * merge silently into that app (its grants kept, its relays, name and icon
 * replaced). It must be confirmed like a same-name match.
 */
class ConnectionToReplaceTest {
    private val clientKey = "ab".repeat(32)
    private val damus = ApplicationWithPermissions(ApplicationEntity.empty().copy(key = clientKey, name = "Damus"), mutableListOf())
    private val dao = mockk<ApplicationDao>()

    private fun connect(name: String, isNostrConnectUri: Boolean) = AmberBunkerRequest(
        request = BunkerRequestConnect(id = "1", remoteKey = clientKey),
        localKey = clientKey,
        relays = emptyList(),
        currentAccount = "npub1",
        nostrConnectSecret = "",
        closeApplication = true,
        name = name,
        signedEvent = null,
        encryptedData = null,
        encryptionType = EncryptionType.NIP44,
        isNostrConnectUri = isNostrConnectUri,
    )

    @Test
    fun nostrConnectUriReusingASavedKeyMustBeConfirmed() = runBlocking {
        coEvery { dao.getByKey(clientKey) } returns damus
        coEvery { dao.getByName(any()) } returns null

        // The URI claims a different name, so the same-name check alone missed it.
        val existing = connectionToReplace(dao, connect("Totally New App", isNostrConnectUri = true), clientKey)

        assertEquals("Damus", existing?.app?.application?.name)
        assertFalse("a second connection can't share the key", existing!!.canCreateNew)
    }

    @Test
    fun sameNameStillOffersANewConnection() = runBlocking {
        coEvery { dao.getByKey(any()) } returns null
        coEvery { dao.getByName("Damus") } returns damus

        val existing = connectionToReplace(dao, connect("Damus", isNostrConnectUri = true), "cd".repeat(32))

        assertTrue(existing!!.canCreateNew)
    }

    @Test
    fun bunkerReconnectUnderTheSameKeyIsNotPrompted() = runBlocking {
        coEvery { dao.getByKey(clientKey) } returns damus
        coEvery { dao.getByName(any()) } returns null

        assertNull(connectionToReplace(dao, connect("", isNostrConnectUri = false), clientKey))
    }

    @Test
    fun newClientIsNotPrompted() = runBlocking {
        coEvery { dao.getByKey(any()) } returns null
        coEvery { dao.getByName(any()) } returns null

        assertNull(connectionToReplace(dao, connect("Fresh", isNostrConnectUri = true), clientKey))
        assertNull(connectionToReplace(dao, connect("", isNostrConnectUri = true), clientKey))
    }
}
