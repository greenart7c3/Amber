package com.greenart7c3.nostrsigner.ui

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.greenart7c3.nostrsigner.AccountStorage
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.RelayClient
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.TorMode
import com.greenart7c3.nostrsigner.service.ApplicationBackup
import com.greenart7c3.nostrsigner.service.BackupApplication
import com.greenart7c3.nostrsigner.service.BackupPayload
import com.greenart7c3.nostrsigner.service.RestoreResult
import com.greenart7c3.nostrsigner.service.TorManager
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip06KeyDerivation.Nip06
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RestorePromptState {
    data object Loading : RestorePromptState
    data class Found(val account: Account, val payload: BackupPayload, val createdAt: Long) : RestorePromptState
}

@Stable
class AccountStateViewModel(
    npub: String?,
    // The Application context: the view model outlives the activity.
    private val appContext: Context,
    private val settingsStore: SettingsStore,
    private val accountRepository: AccountRepository,
    private val relayClient: RelayClient,
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val _accountContent = MutableStateFlow<AccountState>(AccountState.Loading)
    val accountContent = _accountContent.asStateFlow()
    private var observerJob: Job? = null

    val restorePrompt: MutableStateFlow<RestorePromptState?> = MutableStateFlow(null)

    init {
        tryLoginExistingAccount(null, npub)
    }

    private fun tryLoginExistingAccount(
        route: String?,
        npub: String?,
        forceLogout: Boolean = false,
    ) {
        // Loading the account decrypts keys via the Android KeyStore, which is a slow
        // blocking call. Run it off the main thread to avoid StrictMode violations and jank.
        appScope.launch {
            var currentUser = npub ?: SavedAccounts.current(appContext)
            val allAccounts = SavedAccounts.all(appContext)
            if (currentUser != null && !SavedAccounts.contains(appContext, currentUser) && allAccounts.any { it.npub == currentUser }) {
                currentUser = SavedAccounts.current(appContext)
            }
            if (forceLogout) {
                currentUser = null
            }
            // Warm the full account cache BEFORE any UI state is emitted: the
            // account switcher in approval screens reads AccountStorage.cached()
            // synchronously in remember {}, and it used to be populated by the
            // eager load in AccountStorage.loadSync. ConnectivityService's
            // reloadApp() cannot be relied on — it starts via an AlarmManager
            // PendingIntent (a race with composition) and doesn't exist in the
            // offline flavor — so warm here, at the deterministic app start.
            AccountStorage.all(appContext)
            AccountStorage.load(appContext, currentUser)?.let {
                startUI(it, route)
            }
            if (currentUser == null) {
                _accountContent.update { AccountState.LoggedOff }
            }
        }
    }

    private fun prepareLogoutOrSwitch() {
        observerJob?.cancel()
        // Show the loading state while the next account decrypts off the main thread,
        // so the login page does not flash during an account switch.
        _accountContent.update { AccountState.Loading }
    }

    fun logOff(npub: String) {
        prepareLogoutOrSwitch()
        appScope.launch {
            val shouldLogout = AccountStorage.logout(npub, appContext)
            // The logged-out account's NIP-46 subscriptions, connection keys and
            // relay sockets must go with it: rebuild the filters from the
            // remaining accounts, or Amber keeps listening for a deleted key.
            relayClient.checkForNewRelaysAndUpdateAllFilters()
            tryLoginExistingAccount(null, null, forceLogout = shouldLogout)
        }
    }

    fun switchUser(
        npub: String,
        route: String?,
    ) {
        prepareLogoutOrSwitch()
        SavedAccounts.switchTo(appContext, npub)
        tryLoginExistingAccount(route, npub)
    }

    /** After a backup was restored into an Amber with no accounts: logs in to the first restored one. */
    suspend fun openFirstImportedAccount() {
        val first = AccountStorage.all(appContext).firstOrNull() ?: return
        val account = AccountStorage.load(appContext, first.npub) ?: return
        startUI(account, null)
        maybeOfferRestore(account)
    }

    suspend fun startUI(
        keyPair: KeyPair,
        route: String?,
        torMode: TorMode,
        proxyPort: Int,
        signPolicy: Int,
    ) {
        val account = Account(
            name = MutableStateFlow(""),
            picture = MutableStateFlow(""),
            signPolicy = signPolicy,
            didBackup = true,
            signer = NostrSignerInternal(keyPair),
            scope = appScope,
            appContext = appContext,
        )

        if (SavedAccounts.all(appContext).isEmpty()) {
            settingsStore.settings = settingsStore.settings.copy(
                torMode = torMode,
                proxyPort = proxyPort,
            )
            LocalPreferences.saveSettingsToEncryptedStorage(
                appContext,
                settingsStore.settings,
            )
            if (torMode == TorMode.BUILTIN) {
                TorManager.start(appContext, appScope)
            }
        }
        AccountStorage.login(appContext, account, keyPair.pubKey.toHexKey(), keyPair.privKey!!.toHexKey(), null)
        startUI(account, route)
        maybeOfferRestore(account)
    }

    suspend fun newKey(
        torMode: TorMode,
        proxyPort: Int,
        signPolicy: Int,
        seedWords: Set<String>,
        name: String,
    ) {
        val key = seedWords.joinToString(separator = " ") { it }
        val keyPair = KeyPair(privKey = Nip06().privateKeyFromMnemonic(key))
        val hexKey = keyPair.pubKey.toHexKey()
        val privKey = keyPair.privKey!!.toHexKey()
        val account = Account(
            name = MutableStateFlow(name),
            picture = MutableStateFlow(""),
            signPolicy = signPolicy,
            didBackup = false,
            signer = NostrSignerInternal(keyPair),
            scope = appScope,
            appContext = appContext,
        )
        if (SavedAccounts.all(appContext).isEmpty()) {
            settingsStore.settings = settingsStore.settings.copy(
                torMode = torMode,
                proxyPort = proxyPort,
            )
            LocalPreferences.saveSettingsToEncryptedStorage(
                appContext,
                settingsStore.settings,
            )
            if (torMode == TorMode.BUILTIN) {
                TorManager.start(appContext, appScope)
            }
            appScope.launch {
                relayClient.reconnect()
            }
        }
        AccountStorage.login(appContext, account, hexKey, privKey, key)
        startUI(account, null)
    }

    fun startUI(
        account: Account,
        route: String?,
    ) {
        _accountContent.update { AccountState.LoggedIn(account, route) }

        observerJob?.cancel()
        observerJob = appScope.launch(Dispatchers.IO) {
            account.saveable.collect {
                AmberLog.d(Amber.TAG, "Account saved")
                AccountStorage.save(appContext, it.account, null, null, null)
            }
        }
    }

    fun maybeOfferRestore(account: Account) {
        if (BuildFlavorChecker.isOfflineFlavor()) return

        // No BACKUP_APPLICATIONS gate here: that flag is publish consent for the
        // daily worker and is deliberately wiped on logout, so it can never be
        // true on a logout→login round trip — the main case restore exists for.
        // Fetching is a read of a public, self-authored event; the dao check
        // below skips accounts whose local apps are still present.

        appScope.launch {
            try {
                val dao = accountRepository.daos(account.npub).applicationDao
                if (dao.getAll(account.hexKey).isNotEmpty()) return@launch

                restorePrompt.value = RestorePromptState.Loading

                val relays = ApplicationBackup.resolveReadRelays(account, relayClient.client, settingsStore)
                val event = ApplicationBackup.fetchLatestBackupEvent(account, relays, relayClient.client)
                if (event == null) {
                    restorePrompt.value = null
                    return@launch
                }
                val payload = ApplicationBackup.decryptPayload(event, account)
                if (payload == null || payload.applications.isEmpty()) {
                    restorePrompt.value = null
                    return@launch
                }
                restorePrompt.value = RestorePromptState.Found(account, payload, event.createdAt)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AmberLog.e(Amber.TAG, "maybeOfferRestore: failed", e)
                restorePrompt.value = null
            }
        }
    }

    /** Restores only [applications], the entries the user kept selected in the prompt. */
    fun acceptRestore(applications: List<BackupApplication>, onResult: (RestoreResult) -> Unit) {
        val current = restorePrompt.value as? RestorePromptState.Found ?: return
        appScope.launch {
            val result = ApplicationBackup.restoreFromPayload(current.account.npub, current.payload.copy(applications = applications), accountRepository, relayClient)
            restorePrompt.value = null
            launch(Dispatchers.Main) { onResult(result) }
        }
    }

    fun dismissRestore() {
        restorePrompt.value = null
    }

    companion object {
        /** [npub] is only read when the view model is first created. */
        fun factory(
            npub: () -> String?,
            appContext: Context,
            settingsStore: SettingsStore,
            accountRepository: AccountRepository,
            relayClient: RelayClient,
            appScope: CoroutineScope,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AccountStateViewModel(npub(), appContext, settingsStore, accountRepository, relayClient, appScope) }
        }
    }
}
