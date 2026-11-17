package com.greenart7c3.nostrsigner.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.deleting_old_log_entries_from
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.RelayClient
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.models.ThemeMode
import com.greenart7c3.nostrsigner.models.TorMode
import com.greenart7c3.nostrsigner.service.KillSwitchReceiver
import com.greenart7c3.nostrsigner.service.TorManager
import com.greenart7c3.nostrsigner.service.ZapstoreUpdater
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.TimeUtils.ONE_WEEK
import java.text.DecimalFormat
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SettingsUiState(
    val torMode: TorMode,
    val killSwitch: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    // False on builds without the updater (offline, F-Droid).
    val canUpdate: Boolean = false,
    // The newer release, when there is one.
    val availableVersion: String? = null,
    val isLoading: Boolean = true,
    val status: String = "",
    val databaseSizeMb: String = "",
)

/** The Settings home: database size, clearing old entries, Tor, theme and the kill switch. */
@Suppress("LongParameterList") // Constructor injection: one parameter per dependency.
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    // The Application context: database files, preferences and the kill-switch broadcast.
    private val appContext: Context,
    private val settingsStore: SettingsStore,
    private val accountRepository: AccountRepository,
    private val relayClient: RelayClient,
    // Clearing entries finishes even if the user leaves the screen.
    private val appScope: CoroutineScope,
    private val npub: String,
    themeMode: StateFlow<ThemeMode>,
    updater: ZapstoreUpdater?,
) : ViewModel() {
    private val _state = MutableStateFlow(
        SettingsUiState(
            torMode = settingsStore.settings.torMode,
            killSwitch = settingsStore.settings.killSwitch.value,
            themeMode = themeMode.value,
            canUpdate = updater != null,
            availableVersion = updater?.latestRelease?.value?.version,
        ),
    )

    // Settings reloads (Tor, the service start) replace the settings object and its kill-switch
    // flow, so follow the current one instead of the one seen at construction.
    private val currentSettings = MutableStateFlow(settingsStore.settings)
    val state: StateFlow<SettingsUiState> = combine(
        _state,
        currentSettings.flatMapLatest { it.killSwitch },
        themeMode,
        updater?.latestRelease?.map { it?.version } ?: flowOf(null),
    ) { state, killSwitch, theme, version ->
        state.copy(killSwitch = killSwitch, themeMode = theme, availableVersion = version)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, _state.value)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val size = databaseSizeMb()
            _state.update { it.copy(databaseSizeMb = size, isLoading = false) }
        }
    }

    /** Re-reads what other screens may have changed while this one sat in the back stack. */
    fun refresh() {
        currentSettings.value = settingsStore.settings
        _state.update { it.copy(torMode = settingsStore.settings.torMode) }
        viewModelScope.launch(Dispatchers.IO) {
            val size = databaseSizeMb()
            _state.update { it.copy(databaseSizeMb = size) }
        }
    }

    /** Deletes history and logs older than a week for every account, then reconnects with the current relays. */
    fun clearOldEntries() {
        appScope.launch {
            _state.update { it.copy(isLoading = true) }
            LocalPreferences.allSavedAccounts(appContext).forEach {
                try {
                    _state.update { state -> state.copy(status = getStringBlocking(Res.string.deleting_old_log_entries_from, it.npub)) }
                    val deletedHistory = accountRepository.getHistoryDatabase(it.npub).dao().deleteOldHistory(TimeUtils.oneWeekAgo())
                    if (deletedHistory > 0) AmberLog.d(Amber.TAG, "Deleted $deletedHistory old history entries")
                    val oneWeekAgoMillis = System.currentTimeMillis() - (ONE_WEEK * 1000L)
                    val deletedLogs = accountRepository.getLogDatabase(it.npub).dao().deleteOldLog(oneWeekAgoMillis)
                    if (deletedLogs > 0) AmberLog.d(Amber.TAG, "Deleted $deletedLogs old log entries")
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    AmberLog.e(Amber.TAG, "Error deleting old log entries", e)
                }
            }
            val size = databaseSizeMb()
            _state.update { it.copy(isLoading = false, status = "", databaseSizeMb = size) }
            relayClient.checkForNewRelaysAndUpdateAllFilters()
        }
    }

    fun disableTor() {
        _state.update { it.copy(torMode = TorMode.DISABLED) }
        appScope.launch(Dispatchers.IO) {
            if (settingsStore.settings.torMode == TorMode.BUILTIN) {
                TorManager.stop()
            }
            LocalPreferences.updateTorMode(appContext, TorMode.DISABLED)
            refresh()
            relayClient.checkForNewRelaysAndUpdateAllFilters()
        }
    }

    fun setThemeMode(mode: ThemeMode) = LocalPreferences.updateThemeMode(appContext, mode)

    fun toggleKillSwitch() = appContext.sendBroadcast(Intent(appContext, KillSwitchReceiver::class.java))

    private fun databaseSizeMb(): String {
        val bytes = listOf("amber_db_$npub", "log_db_$npub", "history_db_$npub").sumOf { appContext.getDatabasePath(it).length() }
        return DecimalFormat("#.###").format(bytes / (1024.0 * 1024.0))
    }

    companion object {
        @Suppress("LongParameterList") // Mirrors the constructor.
        fun factory(
            appContext: Context,
            settingsStore: SettingsStore,
            accountRepository: AccountRepository,
            relayClient: RelayClient,
            appScope: CoroutineScope,
            npub: String,
            themeMode: StateFlow<ThemeMode>,
            updater: ZapstoreUpdater?,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(appContext, settingsStore, accountRepository, relayClient, appScope, npub, themeMode, updater) }
        }
    }
}
