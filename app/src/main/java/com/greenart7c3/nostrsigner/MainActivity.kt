package com.greenart7c3.nostrsigner

import android.app.Activity
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greenart7c3.nostrsigner.service.Biometrics
import com.greenart7c3.nostrsigner.ui.AccountScreen
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.BiometricsTimeType
import com.greenart7c3.nostrsigner.ui.theme.NostrSignerTheme
import com.vitorpamplona.ammolite.service.HttpClientManager
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun Intent.isLaunchFromHistory(): Boolean =
    this.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY

class MainActivity : AppCompatActivity() {
    private lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel = MainViewModel(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        installSplashScreen()

        HttpClientManager.setDefaultUserAgent("Amber/${BuildConfig.VERSION_NAME}")

        setContent {
            if (intent.isLaunchFromHistory()) {
                Log.d("isLaunchFromHistory", "Cleared intent history")
                intent = Intent()
            }

            val packageName = callingPackage
            val appName =
                if (packageName != null) {
                    val info = applicationContext.packageManager.getApplicationInfo(packageName, 0)
                    applicationContext.packageManager.getApplicationLabel(info).toString()
                } else {
                    null
                }

            NostrSignerTheme {
                var isAuthenticated by remember { mutableStateOf(false) }
                val keyguardLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                        if (result.resultCode == Activity.RESULT_OK) {
                            isAuthenticated = true
                        }
                    }
                val scope = rememberCoroutineScope()
                val context = LocalContext.current

                LaunchedEffect(Unit) {
                    launch(Dispatchers.IO) {
                        val lastAuthTime = NostrSigner.getInstance().settings.lastBiometricsTime
                        val whenToAsk = NostrSigner.getInstance().settings.biometricsTimeType
                        val isTimeToAsk = when (whenToAsk) {
                            BiometricsTimeType.EVERY_TIME -> true
                            BiometricsTimeType.ONE_MINUTE -> minutesBetween(lastAuthTime, System.currentTimeMillis()) >= 1
                            BiometricsTimeType.FIVE_MINUTES -> minutesBetween(lastAuthTime, System.currentTimeMillis()) >= 5
                            BiometricsTimeType.TEN_MINUTES -> minutesBetween(lastAuthTime, System.currentTimeMillis()) >= 10
                        }
                        val shouldAuthenticate = NostrSigner.getInstance().settings.useAuth && isTimeToAsk
                        if (!shouldAuthenticate) {
                            isAuthenticated = true
                        } else {
                            if (!isAuthenticated) {
                                launch(Dispatchers.Main) {
                                    Biometrics.authenticate(
                                        getString(R.string.authenticate),
                                        this@MainActivity,
                                        keyguardLauncher,
                                        {
                                            NostrSigner.getInstance().settings = NostrSigner.getInstance().settings.copy(
                                                lastBiometricsTime = System.currentTimeMillis(),
                                            )

                                            LocalPreferences.saveSettingsToEncryptedStorage(NostrSigner.getInstance().settings)
                                            isAuthenticated = true
                                        },
                                        { _, message ->
                                            this@MainActivity.finish()
                                            scope.launch {
                                                Toast.makeText(
                                                    context,
                                                    message,
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (!isAuthenticated) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val npub = intent.getStringExtra("current_user") ?: mainViewModel.getAccount()

                        val accountStateViewModel: AccountStateViewModel =
                            viewModel {
                                AccountStateViewModel(npub)
                            }
                        AccountScreen(accountStateViewModel, intent, packageName, appName, mainViewModel.intents)
                    }
                }
            }
        }
    }

    override fun onResume() {
        mainViewModel.showBunkerRequests(callingPackage)

        @Suppress("KotlinConstantConditions")
        if (BuildConfig.FLAVOR != "offline") {
            val connectivityManager =
                (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
            connectivityManager.registerDefaultNetworkCallback(mainViewModel.networkCallback)
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.let {
                mainViewModel.updateNetworkCapabilities(it)
            }
        }

        super.onResume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        mainViewModel.onNewIntent(intent, callingPackage)
    }

    override fun onDestroy() {
        mainViewModel.intents.value = emptyList()

        super.onDestroy()
    }
}

fun minutesBetween(startMillis: Long, endMillis: Long): Long {
    val start = Instant.ofEpochMilli(startMillis)
    val end = Instant.ofEpochMilli(endMillis)
    val duration = Duration.between(start, end)
    return duration.toMinutes()
}
