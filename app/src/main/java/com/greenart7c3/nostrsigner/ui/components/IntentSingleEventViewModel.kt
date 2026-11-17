package com.greenart7c3.nostrsigner.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loads the saved application (and its grants) that a single NIP-55 request comes from. */
class IntentSingleEventViewModel(
    accountRepository: AccountRepository,
    npub: String,
    packageName: String?,
) : ViewModel() {
    private val _application = MutableStateFlow<ApplicationWithPermissions?>(null)
    val application: StateFlow<ApplicationWithPermissions?> = _application.asStateFlow()

    init {
        // Browser deep-links (nostrsigner://) arrive with no calling package, so
        // every web caller would otherwise share the single "null" key. Honoring a
        // remembered grant for that shared identity would let a grant given to one
        // website auto-approve any other website. Never load (and thus never honor)
        // the shared "null" application: force always-ask for null-package callers.
        if (packageName != null) {
            viewModelScope.launch(Dispatchers.IO) {
                _application.value = accountRepository.getDatabase(npub).dao().getByKey(packageName)
            }
        }
    }

    companion object {
        fun factory(accountRepository: AccountRepository, npub: String, packageName: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer { IntentSingleEventViewModel(accountRepository, npub, packageName) }
        }
    }
}
