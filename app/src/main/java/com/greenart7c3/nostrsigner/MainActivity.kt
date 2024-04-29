package com.greenart7c3.nostrsigner

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.service.HttpClientManager
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.NotificationDataSource
import com.greenart7c3.nostrsigner.ui.AccountScreen
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.NostrSignerTheme
import com.vitorpamplona.quartz.encoders.toNpub
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

fun Intent.isLaunchFromHistory(): Boolean = this.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY

class MainActivity : AppCompatActivity() {
    private val intents = MutableStateFlow<List<IntentData>>(listOf())
    val isOnMobileDataState = mutableStateOf(false)
    private val isOnWifiDataState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        installSplashScreen()

        setContent {
            if (intent.isLaunchFromHistory()) {
                Log.d("isLaunchFromHistory", "Cleared intent history")
                intent = Intent()
            }

            val packageName = callingPackage
            val appName = if (packageName != null) {
                val info = applicationContext.packageManager.getApplicationInfo(packageName, 0)
                applicationContext.packageManager.getApplicationLabel(info).toString()
            } else {
                null
            }

            NostrSignerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val npub = intent.getStringExtra("current_user") ?: getAccount()

                    val accountStateViewModel: AccountStateViewModel = viewModel {
                        AccountStateViewModel(npub)
                    }
                    AccountScreen(accountStateViewModel, intent, packageName, appName, intents)
                }
            }
        }
    }

    fun getAccount(): String? {
        val pubKeys = intents.value.mapNotNull {
            it.event?.pubKey
        }

        if (pubKeys.isEmpty()) return null
        return Hex.decode(pubKeys.first()).toNpub()
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onResume() {
        val requests = IntentUtils.bunkerRequests.map {
            it.value.copy()
        }

        GlobalScope.launch(Dispatchers.IO) {
            val account = LocalPreferences.loadFromEncryptedStorage()
            account?.let { acc ->
                requests.forEach {
                    val contentIntent = Intent(applicationContext, MainActivity::class.java).apply {
                        data = Uri.parse("nostrsigner:")
                    }
                    contentIntent.putExtra("bunker", it.toJson())
                    contentIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    IntentUtils.getIntentData(contentIntent, callingPackage, Route.Home.route, acc) { intentData ->
                        if (intentData != null) {
                            if (intents.value.none { item -> item.id == intentData.id }) {
                                intents.value += listOf(intentData)
                            }
                        }
                    }
                }
            }

            IntentUtils.bunkerRequests.clear()
        }

        if (BuildConfig.FLAVOR != "offline") {
            val connectivityManager =
                (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.let {
                updateNetworkCapabilities(it)
            }
        }

        super.onResume()
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        GlobalScope.launch(Dispatchers.IO) {
            val account = LocalPreferences.loadFromEncryptedStorage()
            account?.let { acc ->
                IntentUtils.getIntentData(intent, callingPackage, intent.getStringExtra("route"), acc) { intentData ->
                    if (intentData != null) {
                        if (intents.value.none { item -> item.id == intentData.id }) {
                            intents.value += listOf(intentData)
                        }
                        intents.value = intents.value.map {
                            it.copy()
                        }.toMutableList()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        intents.value = emptyList()

        super.onDestroy()
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

    @OptIn(DelicateCoroutinesApi::class)
    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            var lastNetwork: Network? = null

            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                if (BuildConfig.FLAVOR == "offline") return

                if (lastNetwork != null && lastNetwork != network) {
                    GlobalScope.launch(Dispatchers.IO) {
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
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)

                if (BuildConfig.FLAVOR == "offline") return

                GlobalScope.launch(Dispatchers.IO) {
                    Log.d(
                        "ServiceManager NetworkCallback",
                        "onCapabilitiesChanged: ${network.networkHandle} hasMobileData ${isOnMobileDataState.value} hasWifi ${isOnWifiDataState.value}"
                    )
                    if (updateNetworkCapabilities(networkCapabilities)) {
                        NotificationDataSource.stopSync()
                        delay(1000)
                        NotificationDataSource.start()
                    }
                }
            }
        }
}
