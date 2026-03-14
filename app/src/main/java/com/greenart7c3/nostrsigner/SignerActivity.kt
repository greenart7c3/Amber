package com.greenart7c3.nostrsigner

import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.greenart7c3.nostrsigner.service.BunkerRequestUtils
import com.greenart7c3.nostrsigner.ui.AccountScreen
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.CenterCircularProgressIndicator
import com.greenart7c3.nostrsigner.ui.components.BiometricAuthScreen
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.NostrSignerTheme
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class SignerActivity : AppCompatActivity() {
    lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(Amber.TAG, "onCreate SignerActivity")
        Amber.isAppInForeground = true
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Amber.instance.setMainActivity(this)
        mainViewModel = MainViewModel(applicationContext)
        setContent {
            val isStartingApp = Amber.instance.isStartingAppState.collectAsStateWithLifecycle()
            NostrSignerTheme {
                if (isStartingApp.value) {
                    CenterCircularProgressIndicator(Modifier, "Starting application...")
                } else {
                    HttpClientManager.setDefaultUserAgent("Amber/${BuildConfig.VERSION_NAME}")

                    if (intent?.isLaunchFromHistory() == true) {
                        Log.d(Amber.TAG, "Cleared intent history")
                        intent = Intent()
                    }

                    val packageName = callingPackage
                    val appName =
                        if (packageName != null) {
                            try {
                                val info = applicationContext.packageManager.getApplicationInfo(packageName, 0)
                                applicationContext.packageManager.getApplicationLabel(info).toString()
                            } catch (_: Exception) {
                                null
                            }
                        } else {
                            null
                        }

                    CompositionLocalProvider(
                        LocalDensity provides Density(
                            LocalDensity.current.density,
                            1f,
                        ),
                    ) {
                        val navController = rememberNavController()
                        mainViewModel.navController = navController
                        val context = LocalContext.current
                        var isAuthenticated by remember { mutableStateOf(false) }

                        // Use a plain Box+Surface instead of ModalBottomSheet to avoid
                        // BadTokenException on Android 16 with translucent activities.
                        // ModalBottomSheet creates an internal Dialog window whose token
                        // becomes invalid during the first composition frame on API 36.
                        // Since the activity already uses Theme.NostrSigner.Modal (transparent,
                        // translucent) and all modal gestures were disabled anyway, a regular
                        // composable is functionally equivalent.
                        Box(modifier = Modifier.fillMaxSize()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .imePadding(),
                                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                                color = MaterialTheme.colorScheme.background,
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        BottomSheetDefaults.DragHandle()
                                    }
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.background,
                                    ) {
                                        BiometricAuthScreen(
                                            onAuth = {
                                                isAuthenticated = it
                                            },
                                        )

                                        if (!isAuthenticated) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                CircularProgressIndicator()
                                            }
                                        } else {
                                            val bunkerRequests = BunkerRequestUtils.state.collectAsStateWithLifecycle(emptyList())
                                            val npub = remember { mainViewModel.getAccount(intent?.getStringExtra("current_user")) }

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
                                                mainViewModel = mainViewModel,
                                                bunkerRequests = bunkerRequests.value,
                                                navController = navController,
                                                isExternalRequest = true,
                                            )
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

    override fun onPause() {
        Log.d(Amber.TAG, "onPause SignerActivity")
        Amber.isAppInForeground = false
        super.onPause()
    }

    override fun onResume() {
        Log.d(Amber.TAG, "onResume SignerActivity")
        Amber.isAppInForeground = true
        Amber.instance.setMainActivity(this)
        Amber.instance.startServiceFromUi()
        mainViewModel.showBunkerRequests()
        val connectivityManager =
            (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
        connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.let {
            Amber.instance.updateNetworkCapabilities(it)
        }
        super.onResume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mainViewModel.onNewIntent(intent, callingPackage)
    }

    override fun onDestroy() {
        mainViewModel.clear()

        super.onDestroy()
    }
}
