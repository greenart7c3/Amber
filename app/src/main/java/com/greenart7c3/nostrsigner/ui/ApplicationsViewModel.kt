package com.greenart7c3.nostrsigner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.database.ApplicationListItem
import kotlinx.coroutines.flow.Flow

/** An account's connected applications, for the applications list. */
class ApplicationsViewModel(
    private val accountRepository: AccountRepository,
) : ViewModel() {
    /** The account's apps, 20 per page, by name or by last use. */
    fun applications(npub: String, hexKey: String, sortAlphabetically: Boolean): Flow<PagingData<ApplicationListItem>> = Pager(
        PagingConfig(pageSize = 20, enablePlaceholders = false),
    ) {
        // Projection excludes the envelope-encrypted secret/localKey
        // columns: the list renders neither, and each Keystore decrypt
        // is a binder + TEE round trip (2 x rows per page).
        val dao = accountRepository.getDatabase(npub).dao()
        if (sortAlphabetically) dao.getApplicationListItemsPagingByName(hexKey) else dao.getApplicationListItemsPaging(hexKey)
    }.flow

    companion object {
        fun factory(accountRepository: AccountRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ApplicationsViewModel(accountRepository) }
        }
    }
}
