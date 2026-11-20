package com.greenart7c3.nostrsigner.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.greenart7c3.nostrsigner.common.sanitizeAppName
import com.greenart7c3.nostrsigner.data.AccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A saved app as the top bar titles it: its sanitized [name] (may be blank) and its [key]. */
data class SavedAppName(val name: String, val key: String)

/** Looks up the saved apps the top bar names in its title. */
class TopAppBarViewModel(
    private val accountRepository: AccountRepository,
) : ViewModel() {
    /** The app saved under [key] in [npub]'s apps, or null. */
    suspend fun savedApp(npub: String, key: String): SavedAppName? = withContext(Dispatchers.IO) {
        accountRepository.getDatabase(npub).dao().getByKey(key)?.application?.let { SavedAppName(sanitizeAppName(it.name), it.key) }
    }

    companion object {
        fun factory(accountRepository: AccountRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { TopAppBarViewModel(accountRepository) }
        }
    }
}
