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
import kotlinx.coroutines.launch

/** The label of the installed app [packageName], or null when there is none or it cannot be read. */
internal fun Context.callerAppName(packageName: String?): String? = packageName?.let {
    try {
        val info = packageManager.getApplicationInfo(it, 0)
        sanitizeAppName(packageManager.getApplicationLabel(info).toString())
    } catch (_: Exception) {
        null
    }
}

/**
 * The app's screens behind the biometric/PIN lock (UI only, see AGENTS.md): a spinner
 * until [BiometricAuthScreen] lets the user in, then [content].
 */
@Composable
internal fun AuthGate(content: @Composable () -> Unit) {
    var isAuthenticated by remember { mutableStateOf(false) }
    // Like the Surface the activities used to put these in directly: the children
    // get its minimum size.
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

/** The account the activity's intent names in `current_user`, else the first queued request's (see [MainViewModel.getAccount]). */
internal fun MainViewModel.requestedAccount(intent: Intent?): String? = getAccount(intent?.getStringExtra("current_user"))

/**
 * Whenever the queued bunker requests change, switches [accountStateViewModel] to the
 * account the current intent (read through [currentIntent], since onNewIntent replaces
 * it) or the first queued request names.
 */
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

/** The account state for the activities' screens; [npub] is only read when it is first created. */
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
