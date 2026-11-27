package com.greenart7c3.nostrsigner.service

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.PermissionWriteDao
import com.greenart7c3.nostrsigner.database.relaxedApplicationDaos
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.models.ReturnType
import com.greenart7c3.nostrsigner.ui.RememberType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class IntentRejectionTest {
    private val activity = mockk<AppCompatActivity>(relaxed = true)
    private val dao = mockk<ApplicationDao>(relaxed = true) { coEvery { getByKey(any()) } returns null }
    private val permissionWriteDao = mockk<PermissionWriteDao>(relaxed = true)
    private val accounts = mockk<AccountRepository>(relaxed = true) {
        every { daos(any()) } returns relaxedApplicationDaos(applicationDao = dao, permissionWriteDao = permissionWriteDao)
        every { getDatabase(any()).dao() } returns dao
    }

    private val intentData = IntentData(
        data = "",
        name = "",
        type = SignerType.SIGN_EVENT,
        pubKey = "",
        id = "1",
        callBackUrl = null,
        compression = CompressionType.NONE,
        returnType = ReturnType.SIGNATURE,
        permissions = null,
        currentAccount = "",
        route = null,
        event = null,
        encryptedData = null,
    )

    @Suppress("NoComments")
    @Before
    fun setUp() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        IntentUtils.install(accounts, scope, EphemeralPayloadStore(scope)) { activity }
        // The rejection result is an Intent; the JVM test stubs do not implement putExtra.
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Boolean>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } answers { self as Intent }
    }

    @After
    fun tearDown() = unmockkAll()

    private fun reject(key: String): List<IntentResultType> {
        val removedAcrossThreads = CopyOnWriteArrayList<IntentResultType>()
        IntentUtils.sendRejection(
            key = key,
            account = newTestAccount(CoroutineScope(Dispatchers.Unconfined)),
            intentData = intentData,
            appName = "",
            rememberType = RememberType.NEVER,
            onLoading = {},
            onRemoveIntentData = { _, type -> removedAcrossThreads += type },
        )
        return removedAcrossThreads
    }

    private fun awaitRemovedAfterFinish(removed: List<IntentResultType>): List<IntentResultType> = runBlocking {
        withTimeout(5_000) { while (removed.isEmpty()) delay(10) }
        removed.toList()
    }

    @Test
    fun `rejecting a web request closes it without saving anything for the shared null key`() {
        val removed = reject("null")

        verify(timeout = 5_000) { activity.finishAndRemoveTask() }
        assertEquals(listOf(IntentResultType.REMOVE), awaitRemovedAfterFinish(removed))
        verify(exactly = 0) { accounts.getDatabase(any()) }
        coVerify(exactly = 0) { permissionWriteDao.insertApplicationWithPermissions(any()) }
    }

    @Test
    fun `rejecting a native app's request still records it`() {
        val removed = reject("com.example.app")

        coVerify(timeout = 5_000) { permissionWriteDao.insertApplicationWithPermissions(any()) }
        verify(timeout = 5_000) { activity.finishAndRemoveTask() }
        assertEquals(listOf(IntentResultType.REMOVE), awaitRemovedAfterFinish(removed))
    }
}
