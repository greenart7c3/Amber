package com.greenart7c3.nostrsigner.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.service.intentPermission
import com.greenart7c3.nostrsigner.service.isRemembered
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The saved app of [intentData]'s caller (null when it has none), as loaded for that
 * request. The screen gets only its name and remembered decisions, never the entity.
 */
data class LoadedIntentApplication(val intentData: IntentData, private val application: ApplicationWithPermissions?) {
    val name: String? get() = application?.application?.name

    /** The remembered decision for this request under [key] (relay auth: for [relayUrl]): true, false, or null to ask. */
    fun remembered(key: String, relayUrl: String? = null): Boolean? = isRemembered(application?.application?.signPolicy, intentPermission(application, key, intentData, relayUrl))
}

/**
 * Loads the saved application (and its grants) that a single NIP-55 request comes from.
 * It is loaded for each request and kept with it: the calling app chooses the request id,
 * so a later request can reuse this ViewModel's key, and must be answered from its own
 * load (a grant revoked in between must not still apply).
 */
class IntentSingleEventViewModel(
    private val accountRepository: AccountRepository,
) : ViewModel() {
    private val _loaded = MutableStateFlow<LoadedIntentApplication?>(null)
    val loaded: StateFlow<LoadedIntentApplication?> = _loaded.asStateFlow()

    fun load(intentData: IntentData, npub: String, packageName: String?) {
        // Browser deep-links (nostrsigner://) arrive with no calling package, so
        // every web caller would otherwise share the single "null" key. Honoring a
        // remembered grant for that shared identity would let a grant given to one
        // website auto-approve any other website. Never load (and thus never honor)
        // the shared "null" application: force always-ask for null-package callers.
        if (packageName == null) return
        viewModelScope.launch(Dispatchers.IO) {
            _loaded.value = LoadedIntentApplication(intentData, accountRepository.getDatabase(npub).dao().getByKey(packageName))
        }
    }

    companion object {
        fun factory(accountRepository: AccountRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { IntentSingleEventViewModel(accountRepository) }
        }
    }
}
