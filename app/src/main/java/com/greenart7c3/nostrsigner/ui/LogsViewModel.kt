package com.greenart7c3.nostrsigner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.database.LogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One log line as the log screens show it; [repeatCount] identical lines were merged into it. */
data class LogEntry(
    val time: Long,
    val url: String,
    val type: String,
    val message: String,
    val repeatCount: Int,
)

private fun LogEntity.toEntry() = LogEntry(time, url, type, message, repeatCount)

/** An account's operation log: the whole log, or one relay's, for the log screens. */
class LogsViewModel(
    private val accountRepository: AccountRepository,
) : ViewModel() {
    /** The log lines, newest first, 20 per page; only [url]'s when given. */
    fun logs(npub: String, url: String? = null): Flow<PagingData<LogEntry>> = Pager(
        PagingConfig(pageSize = 20, enablePlaceholders = false),
    ) {
        val dao = accountRepository.getLogDatabase(npub).readDao()
        if (url == null) dao.getLogsPaging() else dao.getLogsByUrlPaging(url)
    }.flow.map { page -> page.map { it.toEntry() } }

    /** Every log line, for the export. */
    suspend fun allLogs(npub: String): List<LogEntry> = withContext(Dispatchers.IO) {
        accountRepository.getLogDatabase(npub).readDao().getLogs().first().map { it.toEntry() }
    }

    fun clear(npub: String) {
        viewModelScope.launch(Dispatchers.IO) { accountRepository.getLogDatabase(npub).cleanupDao().clearLogs() }
    }

    companion object {
        fun factory(accountRepository: AccountRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { LogsViewModel(accountRepository) }
        }
    }
}
