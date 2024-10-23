package com.greenart7c3.nostrsigner

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.vitorpamplona.quartz.encoders.toNpub
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
class MainViewModel(val context: Context) : ViewModel() {
    val intents = MutableStateFlow<List<IntentData>>(listOf())
    var navController: NavHostController? = null

    fun getAccount(): String? {
        val pubKeys =
            intents.value.mapNotNull {
                it.event?.pubKey
            }

        if (pubKeys.isEmpty()) return null
        return Hex.decode(pubKeys.first()).toNpub()
    }

    fun showBunkerRequests(callingPackage: String?) {
        val requests =
            IntentUtils.getBunkerRequests().map {
                it.value.copy()
            }

        viewModelScope.launch(Dispatchers.IO) {
            val account = LocalPreferences.loadFromEncryptedStorage(context)
            account?.let { acc ->
                requests.forEach {
                    val contentIntent =
                        Intent(NostrSigner.getInstance(), MainActivity::class.java).apply {
                            data = Uri.parse("nostrsigner:")
                        }
                    contentIntent.putExtra("bunker", it.toJson())
                    contentIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    IntentUtils.getIntentData(context, contentIntent, callingPackage, Route.IncomingRequest.route, acc) { intentData ->
                        if (intentData != null) {
                            if (intents.value.none { item -> item.id == intentData.id }) {
                                intents.value += listOf(intentData)
                            }
                        }
                    }
                }
            }

            if (requests.isNotEmpty()) {
                viewModelScope.launch(Dispatchers.Main) {
                    val hasNavGraph = navController?.currentDestination?.id
                    while (hasNavGraph == null || hasNavGraph == 0) {
                        Log.d("MainViewModel", "Waiting for nav graph")
                        delay(100)
                    }
                    if (hasNavGraph != 0) {
                        navController?.navigate(Route.IncomingRequest.route) {
                            popUpTo(0)
                        }
                    }
                }
            }

            viewModelScope.launch(Dispatchers.IO) {
                delay(10000)
                IntentUtils.clearRequests()
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
                                val hasNavGraph = navController?.currentDestination?.id
                                while (hasNavGraph == null || hasNavGraph == 0) {
                                    Log.d("MainViewModel", "Waiting for nav graph")
                                    delay(100)
                                }
                                if (hasNavGraph != 0) {
                                    navController?.navigate(Route.IncomingRequest.route) {
                                        popUpTo(0)
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
