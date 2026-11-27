package com.greenart7c3.nostrsigner

import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.greenart7c3.nostrsigner.service.BunkerRequestUtils
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.safeReferrer
import com.greenart7c3.nostrsigner.ui.AccountScreen
import com.greenart7c3.nostrsigner.ui.IntentWrapper
import com.greenart7c3.nostrsigner.ui.NavHostControllerWrapper
import com.greenart7c3.nostrsigner.ui.appDependencies
import com.greenart7c3.nostrsigner.ui.theme.NostrSignerTheme
import java.time.Duration
import java.time.Instant
import kotlinx.collections.immutable.persistentListOf

fun Intent.isLaunchFromHistory(): Boolean = this.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY

class MainActivity : AppCompatActivity() {
    lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        AmberLog.d(Amber.TAG, "onCreate MainActivity")
        Amber.isAppInForeground = true
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        amber.setMainActivity(this)
        mainViewModel = MainViewModel(applicationContext)
        val launchReferrer = safeReferrer()
        intent?.let { mainViewModel.onNewIntent(it, callingPackage, launchReferrer) }
        setContent {
            HttpClientManager.setDefaultUserAgent("Amber/${BuildConfig.VERSION_NAME}")

            if (intent?.isLaunchFromHistory() == true) {
                AmberLog.d(Amber.TAG, "Cleared intent history")
                intent = Intent()
            }

            val packageName = callingPackage
            val appName = applicationContext.callerAppName(packageName)

            val themeMode by amber.themeMode.collectAsStateWithLifecycle()
            NostrSignerTheme(themeMode = themeMode) {
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        LocalDensity.current.density,
                        1f,
                    ),
                    *appDependencies(amber),
                ) {
                    val navController = rememberNavController()
                    mainViewModel.navController = navController

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        AuthGate {
                            val bunkerRequests = BunkerRequestUtils.state.collectAsStateWithLifecycle(persistentListOf())
                            val accountStateViewModel = accountStateViewModel { mainViewModel.requestedAccount(intent) }
                            SwitchToRequestedAccountEffect(mainViewModel, accountStateViewModel, bunkerRequests.value) { intent }

                            AccountScreen(
                                accountStateViewModel = accountStateViewModel,
                                intent = remember(intent) { IntentWrapper(intent) },
                                packageName = packageName,
                                appName = appName,
                                referrer = launchReferrer,
                                bunkerRequests = bunkerRequests.value,
                                navController = NavHostControllerWrapper(navController),
                                intentOwner = mainViewModel.ownerId,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        AmberLog.d(Amber.TAG, "onPause MainActivity")
        Amber.isAppInForeground = false
        super.onPause()
    }

    override fun onResume() {
        AmberLog.d(Amber.TAG, "onResume MainActivity")
        Amber.isAppInForeground = true
        amber.setMainActivity(this)
        amber.startService()
        mainViewModel.showBunkerRequests()
        if (!BuildFlavorChecker.isOfflineFlavor()) {
            val connectivityManager =
                (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.let {
                amber.networkState.update(it)
            }
        }
        super.onResume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mainViewModel.onNewIntent(intent, callingPackage, safeReferrer())
    }

    override fun onDestroy() {
        val ownedIds = mainViewModel.addedIntentIds
        val toRemove = mainViewModel.ownIntents().filter { it.id in ownedIds }
        if (toRemove.isNotEmpty()) {
            IntentUtils.removeAll(toRemove)
        }
        super.onDestroy()
    }
}

fun minutesBetween(startMillis: Long, endMillis: Long): Long {
    val start = Instant.ofEpochMilli(startMillis)
    val end = Instant.ofEpochMilli(endMillis)
    val duration = Duration.between(start, end)
    return duration.toMinutes()
}
