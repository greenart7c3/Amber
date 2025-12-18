package com.greenart7c3.nostrsigner

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.service.BunkerRequestUtils
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
class MainViewModel(val context: Context) : ViewModel() {
    private val _intents = MutableStateFlow<List<IntentData>>(listOf())
    val intents = _intents.asStateFlow()
    var navController: NavHostController? = null

    fun addAll(list: List<IntentData>) {
        val newList = list.filter { !intents.value.contains(it) }
        _intents.value += newList
    }

    fun removeAll(intents: List<IntentData>) {
        _intents.value -= intents.toSet()
    }

    fun clear() {
        _intents.value = emptyList()
    }

    fun getAccount(userFromIntent: String?): String? {
        val currentAccount = LocalPreferences.currentAccount(context)
        try {
            if (!userFromIntent.isNullOrBlank()) {
                if (userFromIntent.startsWith("npub")) {
                    if (LocalPreferences.containsAccount(context, userFromIntent)) {
                        Log.d(Amber.TAG, "getAccount: $userFromIntent")
                        return userFromIntent
                    }
                } else {
                    val localNpub = Hex.decode(userFromIntent).toNpub()
                    if (LocalPreferences.containsAccount(context, localNpub)) {
                        Log.d(Amber.TAG, "getAccount: $localNpub")
                        return localNpub
                    }
                }
            }

            val pubKeys =
                intents.value.mapNotNull {
                    it.event?.pubKey
                }.filter { it.isNotBlank() } + BunkerRequestUtils.getBunkerRequests().mapNotNull {
                    when (val parsed = Nip19Parser.uriToRoute(it.currentAccount)?.entity) {
                        is NPub -> parsed.hex
                        else -> null
                    }
                }

            if (pubKeys.isEmpty()) {
                if (currentAccount != null && LocalPreferences.containsAccount(context, currentAccount)) {
                    Log.d(Amber.TAG, "getAccount: $currentAccount")
                    return currentAccount
                }

                val acc = LocalPreferences.allSavedAccounts(context).firstOrNull()
                if (acc != null) {
                    Log.d(Amber.TAG, "getAccount: ${acc.npub}")
                    return acc.npub
                } else {
                    Log.d(Amber.TAG, "getAccount: null")
                    return null
                }
            }

            val npub = Hex.decode(pubKeys.first()).toNpub()
            Log.d(Amber.TAG, "getAccount: $npub")
            return npub
        } catch (e: Exception) {
            Log.e(Amber.TAG, "Error getting account", e)
            if (currentAccount != null && LocalPreferences.containsAccount(context, currentAccount)) {
                Log.d(Amber.TAG, "getAccount: $currentAccount")
                return currentAccount
            }

            val acc = LocalPreferences.allSavedAccounts(context).firstOrNull()
            if (acc != null) {
                Log.d(Amber.TAG, "getAccount: ${acc.npub}")
                return acc.npub
            } else {
                Log.d(Amber.TAG, "getAccount: null")
                return null
            }
        }
    }

    fun showBunkerRequests(accountStateViewModel: AccountStateViewModel? = null) {
        val requests =
            BunkerRequestUtils.getBunkerRequests().map {
                it.copy()
            }

        viewModelScope.launch(Dispatchers.IO) {
            if (requests.isNotEmpty()) {
                val npub = getAccount(null)
                val currentAccount = LocalPreferences.currentAccount(context)
                if (currentAccount != null && npub != null && currentAccount != npub && npub.isNotBlank()) {
                    if (npub.startsWith("npub")) {
                        Log.d(Amber.TAG, "Switching account to $npub")
                        if (LocalPreferences.containsAccount(context, npub)) {
                            accountStateViewModel?.switchUser(npub, Route.IncomingRequest.route)
                        }
                    } else {
                        val localNpub = Hex.decode(npub).toNpub()
                        Log.d(Amber.TAG, "Switching account to $localNpub")
                        if (LocalPreferences.containsAccount(context, localNpub)) {
                            accountStateViewModel?.switchUser(localNpub, Route.IncomingRequest.route)
                        }
                    }
                }
            }
        }
    }

    fun onNewIntent(
        intent: Intent,
        callingPackage: String?,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val account = LocalPreferences.loadFromEncryptedStorage(context)
            account?.let { acc ->
                val intentData = IntentUtils.getIntentData(context, intent, callingPackage, intent.getStringExtra("route"), acc)
                if (intentData != null) {
                    addAll(listOf(intentData))

                    intent.getStringExtra("route")?.let {
                        viewModelScope.launch(Dispatchers.Main) {
                            var error = true
                            var count = 0
                            while (error && count < 10) {
                                delay(100)
                                count++
                                try {
                                    navController?.navigate(Route.IncomingRequest.route) {
                                        popUpTo(0)
                                    }
                                    error = false
                                } catch (e: Exception) {
                                    Log.e(Amber.TAG, "Error showing bunker requests", e)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
