package com.greenart7c3.nostrsigner

import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.greenart7c3.nostrsigner.service.BunkerRequestUtils
import com.greenart7c3.nostrsigner.service.IntentRateLimitInspector
import com.greenart7c3.nostrsigner.service.IntentRateLimiter
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.ui.AccountScreen
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.IntentWrapper
import com.greenart7c3.nostrsigner.ui.InvalidIntentScreen
import com.greenart7c3.nostrsigner.ui.NavHostControllerWrapper
import com.greenart7c3.nostrsigner.ui.components.BiometricAuthScreen
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.NostrSignerTheme
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class SignerActivity : AppCompatActivity() {
    lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        AmberLog.d(Amber.TAG, "onCreate SignerActivity")
        Amber.isAppInForeground = true
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Rate-limited per (caller, type, kind). onNewIntent is intentionally exempt —
        // it is the batch merge into an already-open approval screen.
        if (rejectIfRateLimited(intent)) return

        Amber.instance.setMainActivity(this)
        mainViewModel = MainViewModel(applicationContext)

        // If the request will be served by a bunker proxy account, the proxy
        // forwards it transparently and finishes the activity itself. Make the
        // activity completely invisible — no focus, no dim, no animation, no
        // window content — so the user never sees Amber come up at all.
        val isProxyHandled = isProxyHandledIntent(intent)
        if (isProxyHandled) {
            makeWindowInvisible()
        }

        intent?.let { mainViewModel.onNewIntent(it, callingPackage) }

        if (isProxyHandled) {
            setContent { }
            return
        }

        setContent {
            NostrSignerTheme {
                HttpClientManager.setDefaultUserAgent("Amber/${BuildConfig.VERSION_NAME}")

                if (intent?.isLaunchFromHistory() == true) {
                    AmberLog.d(Amber.TAG, "Cleared intent history")
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

                    val invalidIntent by IntentUtils.invalidIntents
                        .collectAsStateWithLifecycle(initialValue = null)

                    ModalBottomSheet(
                        sheetGesturesEnabled = false,
                        onDismissRequest = {
                            finishAndRemoveTask()
                        },
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
                                        finishAndRemoveTask()
                                    },
                                )
                            } else {
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
                                    val bunkerRequests = BunkerRequestUtils.state.collectAsStateWithLifecycle(persistentListOf())
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
                                                    AmberLog.d(Amber.TAG, "Switching account to $localNpub")
                                                    if (LocalPreferences.containsAccount(context, localNpub)) {
                                                        accountStateViewModel.switchUser(localNpub, Route.IncomingRequest.route)
                                                    }
                                                } else {
                                                    val localNpub2 = Hex.decode(localNpub).toNpub()
                                                    AmberLog.d(Amber.TAG, "Switching account to $localNpub2")
                                                    if (LocalPreferences.containsAccount(context, localNpub2)) {
                                                        accountStateViewModel.switchUser(localNpub2, Route.IncomingRequest.route)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    AccountScreen(
                                        accountStateViewModel = accountStateViewModel,
                                        intent = remember(intent) { IntentWrapper(intent) },
                                        packageName = packageName,
                                        appName = appName,
                                        bunkerRequests = bunkerRequests.value,
                                        navController = NavHostControllerWrapper(navController),
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

    override fun onPause() {
        AmberLog.d(Amber.TAG, "onPause SignerActivity")
        Amber.isAppInForeground = false
        super.onPause()
    }

    override fun onResume() {
        AmberLog.d(Amber.TAG, "onResume SignerActivity")
        Amber.isAppInForeground = true
        Amber.instance.setMainActivity(this)
        Amber.instance.startServiceFromUi()
        if (::mainViewModel.isInitialized) {
            mainViewModel.showBunkerRequests()
        }
        if (!BuildFlavorChecker.isOfflineFlavor()) {
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
        setIntent(intent)
        if (::mainViewModel.isInitialized) {
            // Mirror the onCreate gate: if a proxy account will silently
            // forward this intent, we never want to surface the approval UI.
            if (isProxyHandledIntent(intent)) {
                makeWindowInvisible()
            }
            mainViewModel.onNewIntent(intent, callingPackage)
        }
    }

    /**
     * Makes the activity's window completely invisible and non-interactive. Used for
     * bunker proxy accounts so the source app never loses focus and the user never
     * sees Amber come to the foreground while the proxy forwards the request to the
     * remote bunker.
     */
    private fun makeWindowInvisible() {
        overridePendingTransition(0, 0)
        window.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        )
        // Shrink the window to a single off-screen pixel so it cannot draw anything
        // even briefly. The activity is still alive in the background to receive the
        // proxy's setResult / finishAndRemoveTask call.
        window.attributes = window.attributes.apply {
            width = 1
            height = 1
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = 0
            y = 0
            dimAmount = 0f
        }
    }

    override fun onDestroy() {
        val ownedIds = if (::mainViewModel.isInitialized) mainViewModel.addedIntentIds else emptySet()
        if (ownedIds.isNotEmpty()) {
            val toRemove = IntentUtils.intents.value.filter { it.id in ownedIds }
            if (toRemove.isNotEmpty()) {
                IntentUtils.removeAll(toRemove)
            }
        }
        if (isFinishing) {
            IntentUtils.clearInvalidIntents()
        }
        super.onDestroy()
    }

    /**
     * Returns true if the current intent will be handled silently by the bunker
     * proxy. In that case the activity should not render its approval UI — the
     * proxy forwards the request to the remote bunker and calls
     * [finishAndRemoveTask] from [IntentUtils.handleProxyIntent].
     */
    private fun isProxyHandledIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        if (intent.action != Intent.ACTION_VIEW && intent.data == null) return false
        val userFromIntent = intent.getStringExtra("current_user")
        val npub = mainViewModel.getAccount(userFromIntent) ?: return false
        val account = LocalPreferences.loadFromEncryptedStorageSync(applicationContext, npub) ?: return false
        return account.isProxy
    }

    private fun rejectIfRateLimited(intent: Intent?): Boolean {
        if (intent == null) return false
        val key = IntentRateLimitInspector.inspect(intent, callingPackage) ?: return false
        if (IntentRateLimiter.checkAndRecord(key)) return false

        if (IntentRateLimiter.shouldShowToast(key)) {
            Toast.makeText(
                applicationContext,
                getString(R.string.rate_limit_too_many_requests, resolveCallerLabel(callingPackage)),
                Toast.LENGTH_SHORT,
            ).show()
        }
        AmberLog.w(Amber.TAG, "Rate-limited intent from ${key.pkg} type=${key.type} kind=${key.kind}")
        finishAndRemoveTask()
        return true
    }

    private fun resolveCallerLabel(pkg: String?): String {
        if (pkg.isNullOrBlank()) return getString(R.string.rate_limit_unknown_app)
        return try {
            val info = applicationContext.packageManager.getApplicationInfo(pkg, 0)
            applicationContext.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            pkg
        }
    }
}
