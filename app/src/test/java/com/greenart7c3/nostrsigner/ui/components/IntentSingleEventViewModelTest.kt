package com.greenart7c3.nostrsigner.ui.components

import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.IntentData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IntentSingleEventViewModelTest {
    private val saved = mockk<ApplicationWithPermissions>()
    private val dao = mockk<ApplicationDao> { coEvery { getByKey(any()) } returns saved }
    private val accountRepository = mockk<AccountRepository> {
        every { getDatabase(any()).dao() } returns dao
    }

    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // Browser deep links share the "null" key: a grant given to one site must not answer another.
    @Test
    fun `a request without a calling package never loads the shared application`() {
        val viewModel = IntentSingleEventViewModel(accountRepository)

        viewModel.load(mockk<IntentData>(), "npub1", packageName = null)

        Thread.sleep(200)
        verify(exactly = 0) { accountRepository.getDatabase(any()) }
        assertNull(viewModel.loaded.value)
    }

    @Test
    fun `a native app's request loads its saved application`() {
        val viewModel = IntentSingleEventViewModel(accountRepository)
        val request = mockk<IntentData>()

        viewModel.load(request, "npub1", packageName = "com.example.app")

        // The load runs on Dispatchers.IO.
        coVerify(timeout = 5_000) { dao.getByKey("com.example.app") }
        Thread.sleep(100)
        assertEquals(LoadedIntentApplication(request, saved), viewModel.loaded.value)
    }

    // The calling app picks the request id: a later request reusing it gets this ViewModel
    // back, and must be judged on the grants saved now, not on the first request's load.
    @Test
    fun `a later request under the same key is loaded again`() {
        val viewModel = IntentSingleEventViewModel(accountRepository)
        viewModel.load(mockk<IntentData>(), "npub1", packageName = "com.example.app")
        coVerify(timeout = 5_000) { dao.getByKey("com.example.app") }
        Thread.sleep(100)

        coEvery { dao.getByKey("com.example.app") } returns null
        val second = mockk<IntentData>()
        viewModel.load(second, "npub1", packageName = "com.example.app")

        coVerify(timeout = 5_000, exactly = 2) { dao.getByKey("com.example.app") }
        Thread.sleep(100)
        assertEquals(LoadedIntentApplication(second, null), viewModel.loaded.value)
    }
}
