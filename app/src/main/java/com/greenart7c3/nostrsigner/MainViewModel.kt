package com.greenart7c3.nostrsigner

import android.annotation.SuppressLint
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.NotificationDataSource
import com.greenart7c3.nostrsigner.ui.NotificationType
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.vitorpamplona.ammolite.service.HttpClientManager
import com.vitorpamplona.quartz.encoders.toNpub
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
class MainViewModel : ViewModel() {
    val intents = MutableStateFlow<List<IntentData>>(listOf())
    val isOnMobileDataState = mutableStateOf(false)
    val isOnWifiDataState = mutableStateOf(false)

    val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            var lastNetwork: Network? = null

            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                @Suppress("KotlinConstantConditions")
                if (BuildConfig.FLAVOR == "offline") return

                if (lastNetwork != null && lastNetwork != network && NostrSigner.getInstance().settings.notificationType == NotificationType.DIRECT) {
                    viewModelScope.launch(Dispatchers.IO) {
                        NotificationDataSource.stopSync()
                        delay(1000)
                        NotificationDataSource.start()
                    }
                }

                lastNetwork = network
            }

            // Network capabilities have changed for the network
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)

                @Suppress("KotlinConstantConditions")
                if (BuildConfig.FLAVOR == "offline") return

                viewModelScope.launch(Dispatchers.IO) {
                    Log.d(
                        "ServiceManager NetworkCallback",
                        "onCapabilitiesChanged: ${network.networkHandle} hasMobileData ${isOnMobileDataState.value} hasWifi ${isOnWifiDataState.value}",
                    )
                    if (updateNetworkCapabilities(networkCapabilities) && NostrSigner.getInstance().settings.notificationType == NotificationType.DIRECT) {
                        NotificationDataSource.stopSync()
                        delay(1000)
                        NotificationDataSource.start()
                    }
                }
            }
        }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val account = LocalPreferences.loadFromEncryptedStorage(NostrSigner.getInstance())
            account?.let { acc ->
                IntentUtils.bunkerRequests.collect {
                    it.values.map { bunkerRequest ->
                        val contentIntent =
                            Intent(NostrSigner.getInstance(), MainActivity::class.java).apply {
                                data = Uri.parse("nostrsigner:")
                            }
                        contentIntent.putExtra("bunker", bunkerRequest.toJson())
                        contentIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        IntentUtils.getIntentData(NostrSigner.getInstance(), contentIntent, null, Route.Home.route, acc) { intentData ->
                            if (intentData != null) {
                                if (intents.value.none { item -> item.id == intentData.id }) {
                                    intents.value += listOf(intentData)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

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
            IntentUtils.map().map {
                it.value.copy()
            }

        viewModelScope.launch(Dispatchers.IO) {
            val account = LocalPreferences.loadFromEncryptedStorage(NostrSigner.getInstance())
            account?.let { acc ->
                requests.forEach {
                    val contentIntent =
                        Intent(NostrSigner.getInstance(), MainActivity::class.java).apply {
                            data = Uri.parse("nostrsigner:")
                        }
                    contentIntent.putExtra("bunker", it.toJson())
                    contentIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    IntentUtils.getIntentData(NostrSigner.getInstance(), contentIntent, callingPackage, Route.Home.route, acc) { intentData ->
                        if (intentData != null) {
                            if (intents.value.none { item -> item.id == intentData.id }) {
                                intents.value += listOf(intentData)
                            }
                        }
                    }
                }
            }

            IntentUtils.clearRequests()
        }
    }

    fun onNewIntent(
        intent: Intent,
        callingPackage: String?,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val account = LocalPreferences.loadFromEncryptedStorage(NostrSigner.getInstance())
            account?.let { acc ->
                IntentUtils.getIntentData(NostrSigner.getInstance(), intent, callingPackage, intent.getStringExtra("route"), acc) { intentData ->
                    if (intentData != null) {
                        if (intents.value.none { item -> item.id == intentData.id }) {
                            intents.value += listOf(intentData)
                        }
                        intents.value =
                            intents.value.map {
                                it.copy()
                            }.toMutableList()
                    }
                }
            }
        }
    }

    fun updateNetworkCapabilities(networkCapabilities: NetworkCapabilities): Boolean {
        val isOnMobileData = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val isOnWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

        var changedNetwork = false

        if (isOnMobileDataState.value != isOnMobileData) {
            isOnMobileDataState.value = isOnMobileData

            changedNetwork = true
        }

        if (isOnWifiDataState.value != isOnWifi) {
            isOnWifiDataState.value = isOnWifi

            changedNetwork = true
        }

        if (changedNetwork) {
            if (isOnMobileData) {
                HttpClientManager.setDefaultTimeout(HttpClientManager.DEFAULT_TIMEOUT_ON_MOBILE)
            } else {
                HttpClientManager.setDefaultTimeout(HttpClientManager.DEFAULT_TIMEOUT_ON_WIFI)
            }
        }

        return changedNetwork
    }
}
