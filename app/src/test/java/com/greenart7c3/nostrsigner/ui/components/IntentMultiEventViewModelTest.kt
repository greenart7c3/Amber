package com.greenart7c3.nostrsigner.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryDao
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.models.ReturnType
import com.greenart7c3.nostrsigner.models.ThemeMode
import com.greenart7c3.nostrsigner.service.MultiEventScreenIntents
import com.greenart7c3.nostrsigner.service.newTestAccount
import com.greenart7c3.nostrsigner.ui.RememberType
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.TimeUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IntentMultiEventViewModelTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val account = newTestAccount(scope)
    private val dao = mockk<ApplicationDao>(relaxed = true) { coEvery { getByKey(any()) } returns null }
    private val historyDao = mockk<HistoryDao>(relaxed = true)
    private val accountRepository = mockk<AccountRepository> {
        every { dao(any()) } returns dao
        every { getHistoryDatabase(any()).dao() } returns historyDao
    }
    private val settingsStore = object : SettingsStore {
        override var settings = AmberSettings()
        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    }
    private val activity = mockk<Activity>(relaxed = true)

    private fun intent(id: String, type: SignerType, result: String? = null, event: Event? = null) = IntentData(
        data = "data-$id",
        name = "",
        type = type,
        pubKey = "",
        id = id,
        callBackUrl = null,
        compression = CompressionType.NONE,
        returnType = ReturnType.SIGNATURE,
        permissions = null,
        currentAccount = "",
        route = null,
        event = event,
        encryptedData = result?.let { ClearTextEncryptedDataKind("ciphertext", it) },
    )

    @Before
    fun setUp() {
        // The result list goes back in an Intent; the JVM test stubs do not implement putExtra.
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } answers { self as Intent }
        MultiEventScreenIntents.checkedStates.clear()
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `each request returns its result, a rejection, or nothing when encryption failed`() {
        val event = mockk<Event> { every { sig } returns "sig-1" }

        assertEquals("sig-1", batchResult(intent("1", SignerType.SIGN_EVENT, event = event), approved = true)?.signature)
        assertEquals("plain", batchResult(intent("2", SignerType.NIP44_DECRYPT, result = "plain"), approved = true)?.result)
        assertNull(batchResult(intent("3", SignerType.NIP44_DECRYPT), approved = true))
        assertEquals(true, batchResult(intent("4", SignerType.NIP44_DECRYPT, result = "plain"), approved = false)?.rejected)
    }

    @Test
    fun `confirming saves the remembered grant and the history, returns the results and closes`() {
        val viewModel = IntentMultiEventViewModel(mockk<Context>(relaxed = true), settingsStore, accountRepository, scope, "com.example.app", account)
        val approved = intent("1", SignerType.NIP44_DECRYPT, result = "plain")
        val denied = intent("2", SignerType.NIP04_DECRYPT, result = "other")
        viewModel.initChoices(listOf(approved, denied))
        MultiEventScreenIntents.checkedStates[denied.queueKey] = false
        val groupKey = requestGroupKey(approved.type, null, approved.encryptedData, null)
        viewModel.choices.setRememberType(groupKey, RememberType.ALWAYS)
        val removed = mutableListOf<IntentResultType>()
        val saved = slot<ApplicationWithPermissions>()
        val history = slot<List<HistoryEntity>>()

        viewModel.confirm(listOf(approved, denied), activity, { _, type -> removed += type }, {})

        // Confirming runs on Dispatchers.IO; closing the activity is its last step.
        verify(timeout = 5_000) { activity.finishAndRemoveTask() }
        coVerify { dao.insertApplicationWithPermissions(capture(saved)) }
        assertTrue(saved.captured.permissions.any { it.type == "NIP44_DECRYPT" && it.acceptable })
        coVerify { historyDao.addHistory(capture(history), account.npub) }
        assertEquals(listOf(true, false), history.captured.map { it.accepted })
        verify { activity.setResult(Activity.RESULT_OK, any()) }
        assertEquals(listOf(IntentResultType.REMOVE), removed)
    }

    @Test
    fun `a remembered sign grant keeps its chosen duration`() {
        val viewModel = IntentMultiEventViewModel(mockk<Context>(relaxed = true), settingsStore, accountRepository, scope, "com.example.app", account)
        val event = Event(
            id = "2".repeat(64),
            pubKey = "11".repeat(32),
            createdAt = 1735689600,
            kind = 1,
            tags = arrayOf(arrayOf("t", "amber")),
            content = "remember an hour",
            sig = "77".repeat(64),
        )
        val approved = intent("10", SignerType.SIGN_EVENT, event = event)
        viewModel.initChoices(listOf(approved))
        viewModel.choices.setRememberType(requestGroupKey(SignerType.SIGN_EVENT, 1, null, null), RememberType.ONE_HOUR)

        viewModel.confirm(listOf(approved), activity, { _, _ -> }, {})

        val saved = slot<ApplicationWithPermissions>()
        coVerify(timeout = 5_000) { dao.insertApplicationWithPermissions(capture(saved)) }
        val grant = saved.captured.permissions.single { it.type == "SIGN_EVENT" }
        assertEquals(RememberType.ONE_HOUR.screenCode, grant.rememberType)
        assertTrue(grant.acceptUntil > TimeUtils.now())
    }
}
