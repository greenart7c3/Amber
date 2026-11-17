package com.greenart7c3.nostrsigner.ui.components

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import com.greenart7c3.nostrsigner.common.EncryptionType
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.RelayClient
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryDao
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.service.BunkerResponder
import com.greenart7c3.nostrsigner.service.MultiEventScreenIntents
import com.greenart7c3.nostrsigner.service.newTestAccount
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.utils.TimeUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class BunkerMultiEventViewModelTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val account = newTestAccount(scope)
    private val dao = mockk<ApplicationDao>(relaxed = true)
    private val historyDao = mockk<HistoryDao>(relaxed = true)
    private val accountRepository = mockk<AccountRepository> {
        every { dao(any()) } returns dao
        every { getHistoryDatabase(any()).dao() } returns historyDao
    }
    private val settingsStore = object : SettingsStore {
        override var settings = AmberSettings()
    }
    private val bunkerResponder = mockk<BunkerResponder>(relaxed = true)
    private val relayClient = mockk<RelayClient>(relaxed = true)
    private val notificationManager = mockk<NotificationManager>(relaxed = true)
    private val appContext = mockk<Context> {
        every { getSystemService(NotificationManager::class.java) } returns notificationManager
    }
    private val activity = mockk<Activity>(relaxed = true)

    private val savedApplication = ApplicationWithPermissions(
        application = ApplicationEntity(
            "client-key",
            "",
            listOf(),
            "",
            "",
            "",
            account.hexKey,
            true,
            "",
            false,
            account.signPolicy,
            true,
            0L,
            lastUsed = TimeUtils.now(),
        ),
        permissions = mutableListOf(),
    )

    private fun signRequest(id: String, content: String, localKey: String = "client-key") = AmberBunkerRequest(
        request = BunkerRequestSign(
            id = id,
            event = EventTemplate(createdAt = 1735689600, kind = 1, tags = arrayOf(arrayOf("t", "amber")), content = content),
        ),
        localKey = localKey,
        relays = listOf(),
        currentAccount = "",
        nostrConnectSecret = "",
        closeApplication = false,
        name = "",
        signedEvent = Event(
            id = "0".repeat(64),
            pubKey = "11".repeat(32),
            createdAt = 1735689600,
            kind = 1,
            tags = arrayOf(arrayOf("t", "amber")),
            content = content,
            sig = "77".repeat(64),
        ),
        encryptedData = null,
        encryptionType = EncryptionType.NIP44,
        isNostrConnectUri = false,
    )

    private fun viewModel() = BunkerMultiEventViewModel(appContext, settingsStore, accountRepository, relayClient, bunkerResponder, scope, account)

    private fun response(id: String): BunkerResponse {
        val slot = slot<BunkerResponse>()
        coVerify(timeout = 5_000) {
            bunkerResponder.sendBunkerResponse(any(), any(), capture(slot), any(), any(), any(), any())
        }
        assertEquals(id, slot.captured.id)
        return slot.captured
    }

    @Before
    fun setUp() {
        MultiEventScreenIntents.checkedStates.clear()
        coEvery { dao.getByKey(any()) } returns savedApplication
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `confirming an approved sign request returns the signed event, saves the app and writes history`() {
        val viewModel = viewModel()
        val request = signRequest("id-1", "Hello Nostr!")
        viewModel.initChoices(listOf(request))

        viewModel.confirm(listOf(request), activity, {})

        val sent = response("id-1")
        assertNull(sent.error)
        assertEquals(request.signedEvent!!.toJson(), sent.result)
        verify(timeout = 5_000) { activity.intent = null }
        verify { notificationManager.cancel(request.queueKey.hashCode()) }
        coVerify(timeout = 5_000) { dao.insertApplicationWithPermissions(savedApplication) }
        val history = slot<List<HistoryEntity>>()
        coVerify(timeout = 5_000) { historyDao.addHistory(capture(history), account.npub) }
        assertEquals(listOf(true), history.captured.map { it.accepted })
        assertEquals("SIGN_EVENT", history.captured.single().type)
        // Remember my choice defaults to NEVER: only the app row is saved, once.
        coVerify(exactly = 1) { dao.insertApplicationWithPermissions(savedApplication) }
    }

    @Test
    fun `confirming a denied sign request answers with an error and records the rejection`() {
        val viewModel = viewModel()
        val request = signRequest("id-2", "deny me")
        viewModel.initChoices(listOf(request))
        MultiEventScreenIntents.checkedStates[request.queueKey] = false

        viewModel.confirm(listOf(request), activity, {})

        val sent = response("id-2")
        assertEquals("user rejected", sent.error)
        val history = slot<List<HistoryEntity>>()
        coVerify(timeout = 5_000) { historyDao.addHistory(capture(history), account.npub) }
        assertEquals(listOf(false), history.captured.map { it.accepted })
    }

    @Test
    fun `a request whose app was revoked while queued answers with an error and saves nothing`() {
        coEvery { dao.getByKey(any()) } returns null
        val viewModel = viewModel()
        val request = signRequest("id-3", "revoked app")
        viewModel.initChoices(listOf(request))

        viewModel.confirm(listOf(request), activity, {})

        assertEquals("user rejected", response("id-3").error)
        coVerify(timeout = 5_000, exactly = 0) { dao.insertApplicationWithPermissions(any()) }
        coVerify(exactly = 0) { historyDao.addHistory(any(), any()) }
    }
}
