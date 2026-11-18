package com.greenart7c3.nostrsigner.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.amber
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.allow
import com.greenart7c3.nostrsigner.commonsui.deny
import com.greenart7c3.nostrsigner.commonsui.notifications_are_needed_to_use_amber_as_a_nsec_bunker
import com.greenart7c3.nostrsigner.commonsui.permission_needed
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.service.crashreports.DisplayCrashMessages
import com.greenart7c3.nostrsigner.ui.actions.AccountsBottomSheet
import com.greenart7c3.nostrsigner.ui.components.AmberBottomBar
import com.greenart7c3.nostrsigner.ui.components.AmberFloatingButton
import com.greenart7c3.nostrsigner.ui.components.AmberTopAppBar
import com.greenart7c3.nostrsigner.ui.components.ProfileSubscriptionEffect
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.amoledBorder
import com.greenart7c3.nostrsigner.ui.theme.outlineIfAny
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private fun ManagedActivityResultLauncher<String, Boolean>.tryLaunchPostNotifications() {
    try {
        launch("android.permission.POST_NOTIFICATIONS")
    } catch (_: IllegalStateException) {
        // Launcher unregistered (composable left composition); skip silently.
    }
}

private fun askNotificationPermission(
    context: Context,
    requestPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    onShouldShowRequestPermissionRationale: () -> Unit,
) {
    if (ContextCompat.checkSelfPermission(context, "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) {
        requestIgnoreBatteryOptimizations(context)
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shouldShowRationale = LocalPreferences.shouldShowRationale(context)
        if (shouldShowRationale == null) {
            requestPermissionLauncher.tryLaunchPostNotifications()
            return
        }

        if (!shouldShowRationale) {
            return
        }

        onShouldShowRequestPermissionRationale()
    } else {
        requestPermissionLauncher.tryLaunchPostNotifications()
    }
}

@SuppressLint("BatteryLife")
fun requestIgnoreBatteryOptimizations(context: Context) {
    if (BuildFlavorChecker.isOfflineFlavor()) return
    if (context.amber.client.connectedRelaysFlow().value.isEmpty()) return
    val packageName = context.packageName
    val pm = context.getSystemService(PowerManager::class.java)
    if (!pm.isIgnoringBatteryOptimizations(packageName) && !LocalPreferences.getBatteryOptimization(context)) {
        LocalPreferences.updateBatteryOptimization(context, true)
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = "package:$packageName".toUri()
        context.startActivity(intent)
    }
}

// Asks for the notification permission a bunker needs (online flavors), with a rationale once refused.
@Composable
private fun NotificationPermissionPrompt() {
    val context = LocalContext.current
    val amber = context.amber
    var showRationale by remember { mutableStateOf(false) }
    val requestPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (!isGranted) {
            if (LocalPreferences.shouldShowRationale(context) == null) {
                LocalPreferences.updateShouldShowRationale(context, true)
            }
        } else {
            amber.stats.updateNotification()
            requestIgnoreBatteryOptimizations(context)
        }
    }

    if (!BuildFlavorChecker.isOfflineFlavor()) {
        LaunchedEffect(Unit) {
            askNotificationPermission(context, requestPermissionLauncher, onShouldShowRequestPermissionRationale = { showRationale = true })
        }
    }

    if (showRationale) {
        AlertDialog(
            modifier = Modifier.outlineIfAny(amoledBorder(), AlertDialogDefaults.shape),
            onDismissRequest = { showRationale = false },
            title = { Text(text = stringResource(Res.string.permission_needed)) },
            text = { Text(text = stringResource(Res.string.notifications_are_needed_to_use_amber_as_a_nsec_bunker)) },
            confirmButton = {
                Button(
                    onClick = {
                        showRationale = false
                        requestPermissionLauncher.tryLaunchPostNotifications()
                    },
                ) { Text(text = stringResource(Res.string.allow)) }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showRationale = false
                        LocalPreferences.updateShouldShowRationale(context, false)
                    },
                ) { Text(text = stringResource(Res.string.deny)) }
            },
        )
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    account: Account,
    accountStateViewModel: AccountStateViewModel,
    intents: ImmutableList<IntentData>,
    bunkerRequests: ImmutableList<AmberBunkerRequest>,
    packageName: String?,
    appName: String?,
    // Read through a function so that only this screen recomposes when it is consumed.
    route: () -> String?,
    onConsumeRoute: () -> Unit,
    navController: NavHostControllerWrapper,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    modifier: Modifier = Modifier,
    isExternalRequest: Boolean = false,
    viewModel: MainScreenViewModel = viewModel(
        factory = MainScreenViewModel.factory(LocalContext.current.applicationContext, LocalSettingsStore.current, LocalRelayClient.current, LocalAppScope.current),
    ),
) {
    val appScope = LocalAppScope.current
    val amber = LocalContext.current.amber
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val margin = screenWidthDp - screenWidthDp * 0.93f
    NotificationPermissionPrompt()
    val requests = IncomingRequests(intents, bunkerRequests, packageName, appName, isExternalRequest)
    val startDestination = rememberStartRoute(requests, route, onConsumeRoute)
    // Keep the current account's profile metadata fresh while the main UI is shown.
    ProfileSubscriptionEffect(account)
    var isLoading by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        MainScaffold(
            account,
            navController,
            requests,
            onSwitchAccount = { accountStateViewModel.switchUser(it, null) },
            onLogOff = accountStateViewModel::logOff,
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                val layout = MainLayout(padding, margin)
                NavHost(
                    navController.navController,
                    startDestination = startDestination,
                    enterTransition = { fadeIn(animationSpec = tween(200)) },
                    exitTransition = { fadeOut(animationSpec = tween(200)) },
                ) {
                    loginDestinations(
                        navController,
                        onOpenFirstAccount = accountStateViewModel::openFirstImportedAccount,
                        onCreate = { setup, seedWords, name -> accountStateViewModel.newKey(setup.torMode, setup.proxyPort, setup.signPolicy, seedWords, name) },
                        onLogin = { keyPair, setup -> accountStateViewModel.startUI(keyPair, null, setup.torMode, setup.proxyPort, setup.signPolicy) },
                        onSignedUp = { appScope.launch { amber.notificationSubscription.updateFilter() } },
                    )
                    requestDestination(layout, account, requests, onRemoveIntentData, onLoading = { isLoading = it })
                    accountDestinations(layout, account, navController)
                    relayDestinations(layout, account, navController)
                    applicationDestinations(layout, account, navController)
                    securityDestinations(layout, account, navController)
                    profileDestinations(layout, navController, viewModel::useOrbot, viewModel::useBuiltinTor, onSaveProfile = { accountStateViewModel.switchUser(account.npub, Route.Settings.route) })
                    reportDestinations(layout, account, navController, onLoading = { isLoading = it })
                }

                val restorePrompt by accountStateViewModel.restorePrompt.collectAsState()
                RestoreBackupDialog(restorePrompt, onAccept = accountStateViewModel::acceptRestore, onDismiss = accountStateViewModel::dismissRestore)

                DisplayCrashMessages(amber, account, navController)

                LaunchedEffect(account) {
                    amber.pendingTranslationReport.collect { message ->
                        if (message != null && navController.navController.currentDestination?.route != Route.TranslationReport.route) {
                            navController.navController.navigate(Route.TranslationReport.route)
                        }
                    }
                }
            }
        }
        if (isLoading) LoadingOverlay()
    }
}

// The app bars around the destinations; an external request shows its screen alone.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    account: Account,
    navController: NavHostControllerWrapper,
    requests: IncomingRequests,
    onSwitchAccount: (npub: String) -> Unit,
    onLogOff: (npub: String) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val navBackStackEntry by navController.navController.currentBackStackEntryAsState()
    val destinationRoute = navBackStackEntry?.destination?.route ?: ""
    val scope = rememberCoroutineScope()
    var showAccounts by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(confirmValueChange = { it != SheetValue.PartiallyExpanded }, skipPartiallyExpanded = true)
    if (showAccounts) {
        AccountsBottomSheet(
            sheetState = sheetState,
            account = account,
            onSwitchAccount = onSwitchAccount,
            onLogOff = onLogOff,
            navController = navController,
            onClose = {
                scope.launch {
                    showAccounts = false
                    sheetState.hide()
                }
            },
        )
    }
    Scaffold(
        topBar = {
            if (!requests.isExternalRequest) MainTopBar(account, requests, navBackStackEntry, destinationRoute)
        },
        bottomBar = {
            if (!requests.isExternalRequest) {
                val profileUrl = account.picture.collectAsStateWithLifecycle()
                AmberBottomBar(
                    items = listOf(Route.Applications, Route.IncomingRequest, Route.Settings, Route.Accounts),
                    navController = navController,
                    destinationRoute = destinationRoute,
                    scope = scope,
                    sheetState = sheetState,
                    onShouldShowBottomSheet = { showAccounts = it },
                    profileUrl = profileUrl.value,
                    account = account,
                )
            }
        },
        floatingActionButton = {
            if (!requests.isExternalRequest) {
                AmberFloatingButton(navController = navController, navBackStackEntry = NavBackStackEntryWrapper(navBackStackEntry))
            }
        },
        content = content,
    )
}

// Dims the screen and swallows touches while a request is being answered.
@Composable
private fun LoadingOverlay() {
    CenterCircularProgressIndicator(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
    )
}

/**
 * The start destination: [startRoute] now, and again whenever a route is asked for or
 * the requests change. An asked-for route is consumed once it is used.
 */
@Composable
private fun rememberStartRoute(requests: IncomingRequests, route: () -> String?, onConsumeRoute: () -> Unit): String {
    val currentRoute by rememberUpdatedState(route)
    val currentOnConsumeRoute by rememberUpdatedState(onConsumeRoute)
    val pendingRoute = route()
    var startDestination by remember {
        mutableStateOf(startRoute(requests.isExternalRequest, pendingRoute, requests.intents.isNotEmpty() || requests.bunkerRequests.isNotEmpty()))
    }
    LaunchedEffect(Unit, pendingRoute, requests.intents, requests.bunkerRequests, requests.isExternalRequest) {
        launch(Dispatchers.Main) {
            val asked = currentRoute()
            startDestination = startRoute(requests.isExternalRequest, asked, requests.intents.isNotEmpty() || requests.bunkerRequests.isNotEmpty())
            if (!requests.isExternalRequest && asked != null) currentOnConsumeRoute()
        }
    }
    return startDestination
}

@Composable
private fun MainTopBar(account: Account, requests: IncomingRequests, navBackStackEntry: NavBackStackEntry?, destinationRoute: String) {
    AmberTopAppBar(
        destinationRoute = destinationRoute,
        lifecycleOwner = LocalLifecycleOwner.current,
        scope = rememberCoroutineScope(),
        context = LocalContext.current,
        navBackStackEntry = NavBackStackEntryWrapper(navBackStackEntry),
        account = account,
        intents = requests.intents,
        bunkerRequests = requests.bunkerRequests,
        packageName = requests.packageName,
    )
}
