package com.greenart7c3.nostrsigner.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.greenart7c3.nostrsigner.common.generateBunkerPrivKey
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.RelayClient
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.service.BunkerRequestUtils
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the configuration screen edits of a saved app. */
data class AppConfiguration(
    val key: String,
    val name: String,
    val closeApplication: Boolean,
    val relays: List<NormalizedRelayUrl>,
    val showRelays: Boolean,
)

/**
 * Loads one saved app for the configuration screen and applies its three
 * actions: update the name, relays and close-app setting, reset a bunker
 * connection (new secret and key), or delete the app.
 */
class EditConfigurationViewModel(
    // The Application context: drops the app's queued requests.
    private val appContext: Context,
    private val accountRepository: AccountRepository,
    private val relayClient: RelayClient,
) : ViewModel() {
    private var application: ApplicationWithPermissions? = null

    private val _configuration = MutableStateFlow<AppConfiguration?>(null)
    val configuration: StateFlow<AppConfiguration?> = _configuration.asStateFlow()

    suspend fun load(npub: String, key: String): AppConfiguration? {
        val loaded = withContext(Dispatchers.IO) { accountRepository.getDatabase(npub).dao().getByKey(key) }
        application = loaded
        val config = loaded?.application?.let { AppConfiguration(it.key, it.name, it.closeApplication, it.relays, it.shouldShowRelays()) }
        _configuration.value = config
        return config
    }

    fun update(npub: String, name: String, relays: List<NormalizedRelayUrl>, closeApplication: Boolean, onDone: () -> Unit) {
        val it = application ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val localApplicationData = it.application.copy(name = name, relays = relays, closeApplication = closeApplication)
            accountRepository.daos(npub).applicationWriteDao.delete(it.application)
            accountRepository.daos(npub).permissionWriteDao.insertApplicationWithPermissions(ApplicationWithPermissions(localApplicationData, it.permissions))
            relayClient.checkForNewRelaysAndUpdateAllFilters()
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun resetBunker(npub: String, onDone: () -> Unit) {
        val it = application ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val newSecret = UUID.randomUUID().toString()
            val resetApp = it.application.copy(
                key = newSecret,
                isConnected = false,
                secret = newSecret,
                useSecret = true,
                localKey = generateBunkerPrivKey(),
            )
            val daos = accountRepository.daos(npub)
            // The reset retires the old connection key: drop its
            // queued requests so a stale card cannot resurrect it.
            BunkerRequestUtils.removeAllForClient(appContext, it.application.key)
            daos.permissionWriteDao.deletePermissions(it.application.key)
            daos.applicationWriteDao.delete(it.application)
            accountRepository.getHistoryDatabase(npub).dao().deleteHistory(it.application.key)
            daos.permissionWriteDao.insertApplicationWithPermissions(ApplicationWithPermissions(resetApp, it.permissions))
            relayClient.checkForNewRelaysAndUpdateAllFilters()
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun delete(npub: String, onDone: () -> Unit) {
        val it = application ?: return
        viewModelScope.launch(Dispatchers.IO) {
            // Drop queued requests first: answering a stale card
            // would otherwise resurrect the deleted connection.
            BunkerRequestUtils.removeAllForClient(appContext, it.application.key)
            accountRepository.daos(npub).applicationWriteDao.delete(it.application)
            accountRepository.getHistoryDatabase(npub).dao().deleteHistory(it.application.key)
            // Before navigating away, which may clear this ViewModel and cancel its scope.
            relayClient.checkForNewRelaysAndUpdateAllFilters()
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    companion object {
        fun factory(appContext: Context, accountRepository: AccountRepository, relayClient: RelayClient): ViewModelProvider.Factory = viewModelFactory {
            initializer { EditConfigurationViewModel(appContext, accountRepository, relayClient) }
        }
    }
}
