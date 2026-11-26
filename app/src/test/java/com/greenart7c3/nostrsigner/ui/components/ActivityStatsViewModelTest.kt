package com.greenart7c3.nostrsigner.ui.components

import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.database.HistoryStatsDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityStatsViewModelTest {
    private val dao = mockk<HistoryStatsDao> {
        coEvery { countAcceptedSince(any()) } returns 10
        coEvery { countRejectedSince(any()) } returns 2
        coEvery { countAcceptedSince(any(), any()) } returns 4
        coEvery { countRejectedSince(any(), any()) } returns 1
    }
    private val accountRepository = mockk<AccountRepository> {
        every { getHistoryDatabase(any()).statsDao() } returns dao
    }

    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun ActivityStatsViewModel.awaitStats(): List<WindowStats> {
        repeat(50) {
            stats.value?.let { return it }
            Thread.sleep(100)
        }
        error("the stats were never loaded")
    }

    @Test
    fun `an app's card counts only that app's requests, in three windows`() {
        val viewModel = ActivityStatsViewModel(accountRepository)
        viewModel.load("npub1", pkKey = "com.example.app")

        val stats = viewModel.awaitStats()
        assertEquals(3, stats.size)
        assertEquals(listOf(4L, 4L, 4L), stats.map { it.accepted })
        assertEquals(listOf(5L, 5L, 5L), stats.map { it.total })
        coVerify(exactly = 0) { dao.countAcceptedSince(any()) }
    }

    @Test
    fun `the account's card counts every request`() {
        val viewModel = ActivityStatsViewModel(accountRepository)
        viewModel.load("npub1", pkKey = null)

        assertEquals(listOf(12L, 12L, 12L), viewModel.awaitStats().map { it.total })
    }

    @Test
    fun `opening the card again does not query again`() {
        val viewModel = ActivityStatsViewModel(accountRepository)
        viewModel.load("npub1", pkKey = null)
        viewModel.awaitStats()
        viewModel.load("npub1", pkKey = null)
        Thread.sleep(200)

        coVerify(exactly = 3) { dao.countAcceptedSince(any()) }
    }

    @Test
    fun `coming back to the screen loads fresh counts`() {
        val viewModel = ActivityStatsViewModel(accountRepository)
        viewModel.load("npub1", pkKey = null)
        viewModel.awaitStats()

        viewModel.forget()
        assertEquals(null, viewModel.stats.value)
        coEvery { dao.countAcceptedSince(any()) } returns 11
        viewModel.load("npub1", pkKey = null)

        assertEquals(listOf(13L, 13L, 13L), viewModel.awaitStats().map { it.total })
    }
}
