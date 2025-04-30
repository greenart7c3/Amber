package com.greenart7c3.nostrsigner

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.service.BunkerRequestUtils
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Hex
import kotlin.text.isNotBlank
import kotlin.text.startsWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
class MainViewModel(val context: Context) : ViewModel() {
    val intents = MutableStateFlow<List<IntentData>>(listOf())
    var navController: NavHostController? = null

    fun getAccount(userFromIntent: String?): String? {
        val currentAccount = LocalPreferences.currentAccount(context)
        try {
            if (!userFromIntent.isNullOrBlank()) {
                if (userFromIntent.startsWith("npub")) {
                    if (LocalPreferences.containsAccount(context, userFromIntent)) {
                        Log.d("MainViewModel", "getAccount: $userFromIntent")
                        return userFromIntent
                    }
                } else {
                    val localNpub = Hex.decode(userFromIntent).toNpub()
                    if (LocalPreferences.containsAccount(context, localNpub)) {
                        Log.d("MainViewModel", "getAccount: $localNpub")
                        return localNpub
                    }
                }
            }

            val pubKeys =
                intents.value.mapNotNull {
                    it.event?.pubKey ?: it.bunkerRequest?.currentAccount
                }.filter { it.isNotBlank() }

            if (pubKeys.isEmpty()) {
                if (currentAccount != null && LocalPreferences.containsAccount(context, currentAccount)) {
                    Log.d("MainViewModel", "getAccount: $currentAccount")
                    return currentAccount
                }

                val acc = LocalPreferences.allSavedAccounts(context).firstOrNull()
                if (acc != null) {
                    Log.d("MainViewModel", "getAccount: ${acc.npub}")
                    return acc.npub
                } else {
                    Log.d("MainViewModel", "getAccount: null")
                    return null
                }
            }

            val npub = Hex.decode(pubKeys.first()).toNpub()
            Log.d("MainViewModel", "getAccount: $npub")
            return npub
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error getting account", e)
            if (currentAccount != null && LocalPreferences.containsAccount(context, currentAccount)) {
                Log.d("MainViewModel", "getAccount: $currentAccount")
                return currentAccount
            }

            val acc = LocalPreferences.allSavedAccounts(context).firstOrNull()
            if (acc != null) {
                Log.d("MainViewModel", "getAccount: ${acc.npub}")
                return acc.npub
            } else {
                Log.d("MainViewModel", "getAccount: null")
                return null
            }
        }
    }

    fun showBunkerRequests(callingPackage: String?, accountStateViewModel: AccountStateViewModel? = null) {
        val requests =
            BunkerRequestUtils.getBunkerRequests().map {
                it.value.copy()
            }

        viewModelScope.launch(Dispatchers.IO) {
            val account = LocalPreferences.loadFromEncryptedStorage(context)
            account?.let { acc ->
                requests.forEach {
                    val contentIntent =
                        Intent(Amber.instance, MainActivity::class.java).apply {
                            data = "nostrsigner:".toUri()
                        }
                    contentIntent.putExtra("bunker", it.toJson())
                    contentIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    IntentUtils.getIntentData(context, contentIntent, callingPackage, Route.IncomingRequest.route, acc) { intentData ->
                        contentIntent.putExtra("current_account", acc.npub)
                        if (intentData != null) {
                            if (intents.value.none { item -> item.id == intentData.id }) {
                                intents.value += listOf(intentData)
                            }
                        }
                    }
                }
            }

            if (requests.isNotEmpty()) {
                val npub = getAccount(null)
                val currentAccount = LocalPreferences.currentAccount(context)
                if (currentAccount != null && npub != null && currentAccount != npub && npub.isNotBlank()) {
                    if (npub.startsWith("npub")) {
                        Log.d("Account", "Switching account to $npub")
                        if (LocalPreferences.containsAccount(context, npub)) {
                            accountStateViewModel?.switchUser(npub, Route.IncomingRequest.route)
                        }
                    } else {
                        val localNpub = Hex.decode(npub).toNpub()
                        Log.d("Account", "Switching account to $localNpub")
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
                IntentUtils.getIntentData(context, intent, callingPackage, intent.getStringExtra("route"), acc) { intentData ->
                    if (intentData != null) {
                        if (intents.value.none { item -> item.id == intentData.id }) {
                            intents.value += listOf(intentData)
                        }
                        intents.value =
                            intents.value.map {
                                it.copy()
                            }.toMutableList()

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
                                        Log.e("MainViewModel", "Error showing bunker requests", e)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
