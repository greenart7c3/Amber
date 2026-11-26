package com.greenart7c3.nostrsigner.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.data.RelayClient
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.models.TorMode
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.greenart7c3.nostrsigner.service.TorManager
import com.greenart7c3.nostrsigner.ui.navigation.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun startRoute(isExternalRequest: Boolean, pendingRoute: String?, hasRequests: Boolean): String = when {
    isExternalRequest -> Route.IncomingRequest.route
    pendingRoute != null -> pendingRoute
    hasRequests -> Route.IncomingRequest.route
    else -> Route.Applications.route
}

/** The main screen's actions that outlive a click: switching how Amber reaches relays through Tor. */
class MainScreenViewModel(
    private val appContext: Context,
    private val settingsStore: SettingsStore,
    private val relayClient: RelayClient,
    private val appScope: CoroutineScope,
) : ViewModel() {
    /** Routes relays through Orbot's SOCKS [port], stopping built-in Tor; [onDone] runs on the main thread. */
    fun useOrbot(port: Int, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (settingsStore.settings.torMode == TorMode.BUILTIN) {
                TorManager.stop()
            }
            LocalPreferences.updateProxy(appContext, true, port)
            relayClient.checkForNewRelaysAndUpdateAllFilters()
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    /** Routes relays through built-in Tor, dropping an Orbot proxy; [onDone] runs on the main thread. */
    fun useBuiltinTor(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (settingsStore.settings.torMode == TorMode.ORBOT) {
                HttpClientManager.clearProxy()
            }
            LocalPreferences.updateTorMode(appContext, TorMode.BUILTIN)
            TorManager.start(appContext, appScope)
            relayClient.checkForNewRelaysAndUpdateAllFilters()
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    companion object {
        fun factory(appContext: Context, settingsStore: SettingsStore, relayClient: RelayClient, appScope: CoroutineScope): ViewModelProvider.Factory = viewModelFactory {
            initializer { MainScreenViewModel(appContext, settingsStore, relayClient, appScope) }
        }
    }
}
