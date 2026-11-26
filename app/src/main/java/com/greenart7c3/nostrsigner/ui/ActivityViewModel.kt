package com.greenart7c3.nostrsigner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.greenart7c3.nostrsigner.common.sanitizeAppName
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.database.HistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * One history row as the activity screens show it. For encrypt/decrypt requests
 * [content] is the ciphertext; the plaintext is recovered on demand from
 * [encryptionPubKey] (and, for NIP-44 v3, [kind] and [encryptionScope]).
 */
data class ActivityEntry(
    val id: Int,
    val pkKey: String,
    val type: String,
    val kind: Int?,
    val time: Long,
    val accepted: Boolean,
    val translatedPermission: String = "",
    val content: String = "",
    val encryptionPubKey: String = "",
    val encryptionScope: String = "",
)

private fun HistoryEntity.toEntry() = ActivityEntry(id, pkKey, type, kind, time, accepted, translatedPermission, content, encryptionPubKey, encryptionScope)

/** The request history of an account (or, with a `pkKey`, of one of its apps), for the activity screens. */
class ActivityViewModel(
    private val accountRepository: AccountRepository,
) : ViewModel() {
    /** The rows matching [searchQuery] (none: all) and [filter], newest first, 20 per page. */
    fun history(npub: String, pkKey: String?, searchQuery: String, filter: ActivityFilter): Flow<PagingData<ActivityEntry>> = Pager(
        PagingConfig(pageSize = 20, enablePlaceholders = false),
    ) {
        val dao = accountRepository.getHistoryDatabase(npub).pagingDao()
        when {
            pkKey == null && searchQuery.isEmpty() -> dao.getAllHistoryPaging(filter.accepted)
            pkKey == null -> dao.searchAllHistoryPaging(searchQuery.lowercase(), filter.accepted)
            searchQuery.isEmpty() -> dao.getAllHistoryPaging(pkKey, filter.accepted)
            else -> dao.searchAllHistoryPaging(pkKey, searchQuery.lowercase(), filter.accepted)
        }
    }.flow.map { page -> page.map { it.toEntry() } }

    /** The saved name of the app [key] in [npub], or null when it has none. */
    suspend fun appName(npub: String, key: String): String? = withContext(Dispatchers.IO) {
        accountRepository.getDatabase(npub).dao().getAppName(key)?.let(::sanitizeAppName)?.ifBlank { null }
    }

    companion object {
        fun factory(accountRepository: AccountRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ActivityViewModel(accountRepository) }
        }
    }
}
