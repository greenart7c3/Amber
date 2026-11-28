package com.greenart7c3.nostrsigner

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.greenart7c3.nostrsigner.common.sanitizeAppName
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.LocalAccountRepository
import com.greenart7c3.nostrsigner.ui.LocalAppScope
import com.greenart7c3.nostrsigner.ui.LocalRelayClient
import com.greenart7c3.nostrsigner.ui.LocalSettingsStore
import com.greenart7c3.nostrsigner.ui.components.BiometricAuthScreen
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal fun Context.callerAppName(packageName: String?): String? = packageName?.let {
    try {
        val info = packageManager.getApplicationInfo(it, 0)
        sanitizeAppName(packageManager.getApplicationLabel(info).toString())
    } catch (_: Exception) {
        null
    }
}

@Composable
internal fun AuthGate(content: @Composable () -> Unit) {
    var isAuthenticated by remember { mutableStateOf(false) }
    Box(propagateMinConstraints = true) {
        BiometricAuthScreen(onAuth = { isAuthenticated = it })
        if (isAuthenticated) {
            content()
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
internal fun RouteNavigationEffect(mainViewModel: MainViewModel, navController: NavHostController) {
    LaunchedEffect(mainViewModel, navController) {
        mainViewModel.pendingRoute.filterNotNull().collect { route ->
            navController.currentBackStackEntryFlow.first()
            try {
                routeBackStack(route).forEachIndexed { index, screen ->
                    navController.navigate(screen) {
                        if (index == 0) popUpTo(0)
                    }
                }
            } catch (e: Exception) {
                AmberLog.e(Amber.TAG, "Error navigating to $route", e)
            }
            mainViewModel.pendingRoute.compareAndSet(route, null)
        }
    }
}

internal fun MainViewModel.requestedAccount(intent: Intent?): String? = getAccount(intent?.getStringExtra("current_user"))

@Composable
internal fun SwitchToRequestedAccountEffect(
    mainViewModel: MainViewModel,
    accountStateViewModel: AccountStateViewModel,
    bunkerRequests: ImmutableList<AmberBunkerRequest>,
    currentIntent: () -> Intent?,
) {
    val latestIntent by rememberUpdatedState(currentIntent)
    LaunchedEffect(Unit, bunkerRequests) {
        launch(Dispatchers.IO) {
            mainViewModel.switchToRequestedAccount(mainViewModel.requestedAccount(latestIntent()), accountStateViewModel)
        }
    }
}

@Composable
internal fun accountStateViewModel(npub: () -> String?): AccountStateViewModel = viewModel(
    factory = AccountStateViewModel.factory(
        npub = npub,
        appContext = LocalContext.current.applicationContext,
        settingsStore = LocalSettingsStore.current,
        accountRepository = LocalAccountRepository.current,
        relayClient = LocalRelayClient.current,
        appScope = LocalAppScope.current,
    ),
)
