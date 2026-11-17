package com.greenart7c3.nostrsigner.ui.components

import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
        val viewModel = IntentSingleEventViewModel(accountRepository, "npub1", packageName = null)

        Thread.sleep(200)
        verify(exactly = 0) { accountRepository.getDatabase(any()) }
        assertNull(viewModel.application.value)
    }

    @Test
    fun `a native app's request loads its saved application`() {
        val viewModel = IntentSingleEventViewModel(accountRepository, "npub1", packageName = "com.example.app")

        // The load runs on Dispatchers.IO.
        coVerify(timeout = 5_000) { dao.getByKey("com.example.app") }
        Thread.sleep(100)
        assertSame(saved, viewModel.application.value)
    }
}
