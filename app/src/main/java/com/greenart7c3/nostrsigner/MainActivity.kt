package com.greenart7c3.nostrsigner

import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.greenart7c3.nostrsigner.service.Biometrics
import com.greenart7c3.nostrsigner.service.BunkerRequestUtils
import com.greenart7c3.nostrsigner.ui.AccountScreen
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.BiometricsTimeType
import com.greenart7c3.nostrsigner.ui.CenterCircularProgressIndicator
import com.greenart7c3.nostrsigner.ui.components.RandomPinInput
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.NostrSignerTheme
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Hex
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun Intent.isLaunchFromHistory(): Boolean =
    this.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY

class MainActivity : AppCompatActivity() {
    private lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(Amber.TAG, "onCreate MainActivity")
        Amber.instance.startService()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Amber.instance.setMainActivity(this)
        mainViewModel = MainViewModel(applicationContext)
        setContent {
            val isStartingApp = Amber.instance.isStartingAppState.collectAsStateWithLifecycle()
            if (isStartingApp.value) {
                NostrSignerTheme {
                    CenterCircularProgressIndicator(Modifier, "Starting application...")
                }
            } else {
                HttpClientManager.setDefaultUserAgent("Amber/${BuildConfig.VERSION_NAME}")

                if (intent.isLaunchFromHistory()) {
                    Log.d(Amber.TAG, "Cleared intent history")
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
                    CompositionLocalProvider(
                        LocalDensity provides Density(
                            LocalDensity.current.density,
                            1f,
                        ),
                    ) {
                        val navController = rememberNavController()
                        mainViewModel.navController = navController
                        var isAuthenticated by remember { mutableStateOf(false) }
                        var showPinDialog by remember { mutableStateOf(false) }
                        val keyguardLauncher =
                            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                                if (result.resultCode == RESULT_OK) {
                                    isAuthenticated = true
                                }
                            }
                        val scope = rememberCoroutineScope()
                        val context = LocalContext.current

                        LaunchedEffect(Unit) {
                            launch(Dispatchers.IO) {
                                val settings = Amber.instance.settings
                                val lastAuthTime = settings.lastBiometricsTime
                                val whenToAsk = settings.biometricsTimeType
                                val isTimeToAsk = when (whenToAsk) {
                                    BiometricsTimeType.EVERY_TIME -> true
                                    BiometricsTimeType.ONE_MINUTE -> minutesBetween(lastAuthTime, System.currentTimeMillis()) >= 1
                                    BiometricsTimeType.FIVE_MINUTES -> minutesBetween(lastAuthTime, System.currentTimeMillis()) >= 5
                                    BiometricsTimeType.TEN_MINUTES -> minutesBetween(lastAuthTime, System.currentTimeMillis()) >= 10
                                }
                                val shouldAuthenticate = (settings.useAuth || settings.usePin) && isTimeToAsk
                                if (!shouldAuthenticate) {
                                    isAuthenticated = true
                                } else {
                                    if (!isAuthenticated) {
                                        launch(Dispatchers.Main) {
                                            if (settings.usePin) {
                                                showPinDialog = true
                                            } else {
                                                Biometrics.authenticate(
                                                    getString(R.string.authenticate),
                                                    this@MainActivity,
                                                    keyguardLauncher,
                                                    {
                                                        Amber.instance.settings = Amber.instance.settings.copy(
                                                            lastBiometricsTime = System.currentTimeMillis(),
                                                        )

                                                        LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
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
                        }

                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            if (showPinDialog) {
                                Dialog(
                                    properties = DialogProperties(usePlatformDefaultWidth = false),
                                    onDismissRequest = {
                                        showPinDialog = false
                                        this@MainActivity.finish()
                                        scope.launch {
                                            Toast.makeText(
                                                context,
                                                getString(R.string.pin_does_not_match),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxSize(),
                                        color = MaterialTheme.colorScheme.background,
                                    ) {
                                        Box(
                                            Modifier.padding(40.dp),
                                        ) {
                                            RandomPinInput(
                                                text = getString(R.string.enter_pin),
                                                onPinEntered = {
                                                    val pin = LocalPreferences.loadPinFromEncryptedStorage()
                                                    if (it == pin) {
                                                        Amber.instance.settings = Amber.instance.settings.copy(
                                                            lastBiometricsTime = System.currentTimeMillis(),
                                                        )

                                                        LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
                                                        isAuthenticated = true
                                                        showPinDialog = false
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            getString(R.string.pin_does_not_match),
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            if (!isAuthenticated) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                val bunkerRequests = BunkerRequestUtils.state.collectAsStateWithLifecycle(emptyList())
                                var npub = remember { mainViewModel.getAccount(intent?.getStringExtra("current_user")) }

                                val accountStateViewModel: AccountStateViewModel =
                                    viewModel {
                                        AccountStateViewModel(npub)
                                    }

                                LaunchedEffect(Unit, bunkerRequests.value) {
                                    launch(Dispatchers.IO) {
                                        val localNpub = mainViewModel.getAccount(intent?.getStringExtra("current_user"))
                                        val currentAccount = LocalPreferences.currentAccount(context)
                                        if (currentAccount != null && localNpub != null && currentAccount != localNpub && localNpub.isNotBlank()) {
                                            if (localNpub.startsWith("npub")) {
                                                Log.d(Amber.TAG, "Switching account to $localNpub")
                                                if (LocalPreferences.containsAccount(context, localNpub)) {
                                                    accountStateViewModel.switchUser(localNpub, Route.IncomingRequest.route)
                                                }
                                            } else {
                                                val localNpub2 = Hex.decode(localNpub).toNpub()
                                                Log.d(Amber.TAG, "Switching account to $localNpub2")
                                                if (LocalPreferences.containsAccount(context, localNpub2)) {
                                                    accountStateViewModel.switchUser(localNpub2, Route.IncomingRequest.route)
                                                }
                                            }
                                        }
                                    }
                                }

                                AccountScreen(
                                    accountStateViewModel = accountStateViewModel,
                                    intent = intent,
                                    packageName = packageName,
                                    appName = appName,
                                    flow = mainViewModel.intents,
                                    bunkerRequests = bunkerRequests.value,
                                    navController = navController,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        Amber.instance.setMainActivity(this)
        mainViewModel.showBunkerRequests(callingPackage)
        @Suppress("KotlinConstantConditions")
        if (BuildConfig.FLAVOR != "offline") {
            val connectivityManager =
                (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.let {
                Amber.instance.updateNetworkCapabilities(it)
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
