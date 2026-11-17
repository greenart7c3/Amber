package com.greenart7c3.nostrsigner.ui.components

import com.greenart7c3.nostrsigner.common.EncryptionType
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.service.BunkerResponder
import com.greenart7c3.nostrsigner.ui.RememberType
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BunkerSingleEventViewModelTest {
    private val clientKey = "ab".repeat(32)
    private val saved = ApplicationWithPermissions(ApplicationEntity.empty().copy(key = clientKey, name = "Damus"), mutableListOf())
    private val dao = mockk<ApplicationDao>(relaxed = true)
    private val accountRepository = mockk<AccountRepository> { every { getDatabase(any()).dao() } returns dao }
    private val responder = mockk<BunkerResponder>(relaxed = true)
    private val account = mockk<Account> { every { npub } returns "npub1" }
    private val approval = ConnectApproval(null, 0, true, RememberType.ALWAYS, 0L, account)

    private fun connect(secret: String? = null, isNostrConnectUri: Boolean = true) = AmberBunkerRequest(
        request = BunkerRequestConnect(id = "1", remoteKey = clientKey, secret = secret),
        localKey = clientKey,
        relays = emptyList(),
        currentAccount = "npub1",
        nostrConnectSecret = "",
        closeApplication = true,
        name = "",
        signedEvent = null,
        encryptedData = null,
        encryptionType = EncryptionType.NIP44,
        isNostrConnectUri = isNostrConnectUri,
    )

    private fun viewModel(request: AmberBunkerRequest) = BunkerSingleEventViewModel(accountRepository, responder, CoroutineScope(Dispatchers.Unconfined), request, "npub1")

    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a connect with a secret loads the app saved under that secret`() {
        coEvery { dao.getBySecret("s3cret") } returns saved

        val viewModel = viewModel(connect(secret = "s3cret"))

        // The load runs on Dispatchers.IO.
        coVerify(timeout = 5_000) { dao.getBySecret("s3cret") }
        Thread.sleep(100)
        assertEquals("Damus", viewModel.application.value?.application?.name)
        coVerify(exactly = 0) { dao.getByKey(any()) }
    }

    @Test
    fun `a connect replacing nothing is answered at once`() {
        coEvery { dao.getByKey(any()) } returns null
        coEvery { dao.getByName(any()) } returns null
        val request = connect()
        val viewModel = viewModel(request)

        viewModel.approveConnect(BunkerSingleRequest(request, account, null), approval, onLoading = {})

        verify(timeout = 5_000) { responder.sendResult(oldKey = "", account = account, key = clientKey, response = "ack", bunkerRequest = any(), kind = null, onLoading = any(), permissions = null, appName = any(), signPolicy = 0, shouldCloseApplication = true, rememberType = RememberType.ALWAYS, deleteAfter = 0L) }
        assertNull(viewModel.replacePrompt.value)
    }

    @Test
    fun `a nostrconnect URI reusing a saved key asks before replacing it`() {
        coEvery { dao.getByKey(clientKey) } returns saved
        val request = connect()
        val viewModel = viewModel(request)

        viewModel.approveConnect(BunkerSingleRequest(request, account, null), approval, onLoading = {})

        coVerify(timeout = 5_000) { dao.getByKey(clientKey) }
        Thread.sleep(100)
        val prompt = viewModel.replacePrompt.value!!
        assertEquals("Damus", prompt.existingName)
        assertFalse(prompt.canCreateNew)
        verify(exactly = 0) { responder.sendResult(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }

        viewModel.replaceConnection(onLoading = {})

        verify { responder.sendResult(oldKey = clientKey, account = account, key = clientKey, response = "ack", bunkerRequest = any(), kind = null, onLoading = any(), permissions = null, appName = any(), signPolicy = 0, shouldCloseApplication = true, rememberType = RememberType.ALWAYS, deleteAfter = 0L) }
        assertNull("an answered prompt never comes back", viewModel.replacePrompt.value)
    }
}
