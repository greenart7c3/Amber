package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.database.ApplicationDao
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
    private val amber = mockk<Amber>(relaxed = true) {
        every { applicationIOScope } returns CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        every { getMainActivity() } returns activity
        every { dao(any()) } returns dao
        every { getDatabase(any()).dao() } returns dao
    }
    private val context = mockk<Context> { every { applicationContext } returns amber }

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

    @Before
    fun setUp() {
        // The rejection result is an Intent; the JVM test stubs do not implement putExtra.
        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Boolean>()) } answers { self as Intent }
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } answers { self as Intent }
    }

    @After
    fun tearDown() = unmockkAll()

    private fun reject(key: String): List<IntentResultType> {
        // Filled on the rejection's coroutine, read by the test thread.
        val removed = CopyOnWriteArrayList<IntentResultType>()
        IntentUtils.sendRejection(
            context = context,
            key = key,
            account = newTestAccount(CoroutineScope(Dispatchers.Unconfined)),
            intentData = intentData,
            appName = "",
            rememberType = RememberType.NEVER,
            onLoading = {},
            onRemoveIntentData = { _, type -> removed += type },
        )
        return removed
    }

    // onRemoveIntentData runs after finishAndRemoveTask, so waiting for the finish is not enough.
    private fun awaitRemoved(removed: List<IntentResultType>): List<IntentResultType> = runBlocking {
        withTimeout(5_000) { while (removed.isEmpty()) delay(10) }
        removed.toList()
    }

    // A web request (no calling package) could not be rejected: Reject left the sheet open.
    @Test
    fun `rejecting a web request closes it without saving anything for the shared null key`() {
        val removed = reject("null")

        verify(timeout = 5_000) { activity.finishAndRemoveTask() }
        assertEquals(listOf(IntentResultType.REMOVE), awaitRemoved(removed))
        verify(exactly = 0) { amber.getDatabase(any()) }
        coVerify(exactly = 0) { dao.insertApplicationWithPermissions(any()) }
    }

    @Test
    fun `rejecting a native app's request still records it`() {
        val removed = reject("com.example.app")

        coVerify(timeout = 5_000) { dao.insertApplicationWithPermissions(any()) }
        verify(timeout = 5_000) { activity.finishAndRemoveTask() }
        assertEquals(listOf(IntentResultType.REMOVE), awaitRemoved(removed))
    }
}
