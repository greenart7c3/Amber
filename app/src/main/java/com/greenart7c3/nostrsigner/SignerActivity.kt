package com.greenart7c3.nostrsigner

import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.rate_limit_too_many_requests
import com.greenart7c3.nostrsigner.commonsui.rate_limit_unknown_app
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.greenart7c3.nostrsigner.service.BunkerRequestUtils
import com.greenart7c3.nostrsigner.service.IntentRateLimitInspector
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.safeReferrer
import com.greenart7c3.nostrsigner.ui.AccountScreen
import com.greenart7c3.nostrsigner.ui.IntentWrapper
import com.greenart7c3.nostrsigner.ui.InvalidIntentScreen
import com.greenart7c3.nostrsigner.ui.NavHostControllerWrapper
import com.greenart7c3.nostrsigner.ui.appDependencies
import com.greenart7c3.nostrsigner.ui.theme.NostrSignerTheme
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalMaterial3Api::class)
class SignerActivity : AppCompatActivity() {
    lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        AmberLog.d(Amber.TAG, "onCreate SignerActivity")
        Amber.isAppInForeground = true
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (rejectIfRateLimited(intent)) return

        amber.setMainActivity(this)
        mainViewModel = MainViewModel(applicationContext)
        val launchReferrer = safeReferrer()
        intent?.let { mainViewModel.onNewIntent(it, callingPackage, launchReferrer) }
        setContent {
            val themeMode by amber.themeMode.collectAsStateWithLifecycle()
            NostrSignerTheme(themeMode = themeMode) {
                HttpClientManager.setDefaultUserAgent("Amber/${BuildConfig.VERSION_NAME}")

                if (intent?.isLaunchFromHistory() == true) {
                    AmberLog.d(Amber.TAG, "Cleared intent history")
                    intent = Intent()
                }

                val packageName = callingPackage
                val appName = applicationContext.callerAppName(packageName)

                CompositionLocalProvider(
                    LocalDensity provides Density(
                        LocalDensity.current.density,
                        1f,
                    ),
                    *appDependencies(amber),
                ) {
                    val navController = rememberNavController()
                    mainViewModel.navController = navController

                    RequestSheet(onFinish = ::finishAndRemoveTask) {
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
                                isExternalRequest = true,
                                intentOwner = mainViewModel.ownerId,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        AmberLog.d(Amber.TAG, "onPause SignerActivity")
        Amber.isAppInForeground = false
        super.onPause()
    }

    override fun onResume() {
        AmberLog.d(Amber.TAG, "onResume SignerActivity")
        Amber.isAppInForeground = true
        amber.setMainActivity(this)
        amber.startService()
        if (::mainViewModel.isInitialized) {
            mainViewModel.showBunkerRequests()
        }
        if (!BuildFlavorChecker.isOfflineFlavor()) {
            val connectivityManager =
                (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.let {
                amber.networkState.update(it)
            }
        }
        super.onResume()
    }

    /**
     * Not rate-limited, unlike [onCreate] (per caller, type and kind): it is the batch
     * merge into an already-open approval screen.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::mainViewModel.isInitialized) {
            mainViewModel.onNewIntent(intent, callingPackage, referrer)
        }
    }

    override fun onDestroy() {
        val ownedIds = if (::mainViewModel.isInitialized) mainViewModel.addedIntentIds else emptySet()
        if (ownedIds.isNotEmpty()) {
            val toRemove = mainViewModel.ownIntents().filter { it.id in ownedIds }
            if (toRemove.isNotEmpty()) {
                IntentUtils.removeAll(toRemove)
            }
        }
        if (isFinishing) {
            IntentUtils.clearInvalidIntents()
        }
        super.onDestroy()
    }

    private fun rejectIfRateLimited(intent: Intent?): Boolean {
        if (intent == null) return false
        val key = IntentRateLimitInspector.inspect(intent, callingPackage) ?: return false
        val limiter = amber.intentRateLimiter
        if (limiter.checkAndRecord(key)) return false

        if (limiter.shouldShowToast(key)) {
            Toast.makeText(
                applicationContext,
                getStringBlocking(Res.string.rate_limit_too_many_requests, resolveCallerLabel(callingPackage)),
                Toast.LENGTH_SHORT,
            ).show()
        }
        AmberLog.w(Amber.TAG, "Rate-limited intent from ${key.pkg} type=${key.type} kind=${key.kind}")
        finishAndRemoveTask()
        return true
    }

    private fun resolveCallerLabel(pkg: String?): String {
        if (pkg.isNullOrBlank()) return getStringBlocking(Res.string.rate_limit_unknown_app)
        return applicationContext.callerAppName(pkg) ?: pkg
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestSheet(
    onFinish: () -> Unit,
    content: @Composable () -> Unit,
) {
    val invalidIntent by IntentUtils.invalidIntents.collectAsStateWithLifecycle(initialValue = null)
    ModalBottomSheet(
        sheetGesturesEnabled = false,
        onDismissRequest = onFinish,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        scrimColor = Color.Transparent,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = false,
            shouldDismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (invalidIntent != null) {
                InvalidIntentScreen(
                    modifier = Modifier.fillMaxSize(),
                    onClose = {
                        IntentUtils.clearInvalidIntents()
                        onFinish()
                    },
                )
            } else {
                content()
            }
        }
    }
}
