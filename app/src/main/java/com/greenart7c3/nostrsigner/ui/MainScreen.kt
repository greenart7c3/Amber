package com.greenart7c3.nostrsigner.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.relays.AmberRelayStats
import com.greenart7c3.nostrsigner.service.NotificationDataSource
import com.greenart7c3.nostrsigner.ui.actions.AccountBackupScreen
import com.greenart7c3.nostrsigner.ui.actions.AccountsBottomSheet
import com.greenart7c3.nostrsigner.ui.actions.ActiveRelaysScreen
import com.greenart7c3.nostrsigner.ui.actions.ActivityScreen
import com.greenart7c3.nostrsigner.ui.actions.ConnectOrbotScreen
import com.greenart7c3.nostrsigner.ui.actions.DefaultRelaysScreen
import com.greenart7c3.nostrsigner.ui.actions.QrCodeScreen
import com.greenart7c3.nostrsigner.ui.actions.RelayLogScreen
import com.greenart7c3.nostrsigner.ui.components.AmberBottomBar
import com.greenart7c3.nostrsigner.ui.components.AmberFloatingButton
import com.greenart7c3.nostrsigner.ui.components.AmberTopAppBar
import com.greenart7c3.nostrsigner.ui.navigation.Route
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
            requestPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
            return
        }

        if (!shouldShowRationale) {
            return
        }

        onShouldShowRequestPermissionRationale()
    } else {
        requestPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
    }
}

@SuppressLint("BatteryLife")
fun requestIgnoreBatteryOptimizations(context: Context) {
    @Suppress("KotlinConstantConditions")
    if (BuildConfig.FLAVOR == "offline") return
    if (Amber.instance.client.getAll().isEmpty()) return
    val packageName = context.packageName
    val pm = context.getSystemService(PowerManager::class.java)
    if (!pm.isIgnoringBatteryOptimizations(packageName) && !LocalPreferences.getBatteryOptimization(context)) {
        LocalPreferences.updateBatteryOptimization(context, true)
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = "package:$packageName".toUri()
        context.startActivity(intent)
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    account: Account,
    accountStateViewModel: AccountStateViewModel,
    intents: List<IntentData>,
    packageName: String?,
    appName: String?,
    route: MutableState<String?>,
    navController: NavHostController,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val destinationRoute = navBackStackEntry?.destination?.route ?: ""
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var profileUrl by remember { mutableStateOf<String?>(null) }
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val percentage = (screenWidthDp * 0.93f)
    val verticalPadding = (screenWidthDp - percentage)
    val requestPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted: Boolean ->
            if (!isGranted) {
                if (LocalPreferences.shouldShowRationale(context) == null) {
                    LocalPreferences.updateShouldShowRationale(context, true)
                }
            } else {
                AmberRelayStats.updateNotification()
                requestIgnoreBatteryOptimizations(context)
            }
        }

    var showDialog by remember { mutableStateOf(false) }

    @Suppress("KotlinConstantConditions")
    if (BuildConfig.FLAVOR != "offline") {
        LaunchedEffect(Unit) {
            launch(Dispatchers.IO) {
                askNotificationPermission(
                    context = context,
                    requestPermissionLauncher = requestPermissionLauncher,
                    onShouldShowRequestPermissionRationale = {
                        showDialog = true
                    },
                )

                Amber.instance.fetchProfileData(
                    account = account,
                    onPictureFound = {
                        profileUrl = it
                    },
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
            },
            title = {
                Text(text = stringResource(R.string.permission_needed))
            },
            text = {
                Text(text = stringResource(R.string.notifications_are_needed_to_use_amber_as_a_nsec_bunker))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDialog = false
                        requestPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
                    },
                ) {
                    Text(text = stringResource(R.string.allow))
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showDialog = false
                        LocalPreferences.updateShouldShowRationale(context, false)
                    },
                ) {
                    Text(text = stringResource(R.string.deny))
                }
            },
        )
    }

    var localRoute by remember { mutableStateOf(route.value ?: if (intents.isEmpty()) Route.Applications.route else Route.IncomingRequest.route) }
    LaunchedEffect(Unit, route.value, intents) {
        launch(Dispatchers.Main) {
            if (route.value != null) {
                localRoute = route.value!!
                route.value = null
            } else {
                localRoute = if (intents.isEmpty()) Route.Applications.route else Route.IncomingRequest.route
            }
        }
    }

    val items = listOf(Route.Applications, Route.IncomingRequest, Route.Settings, Route.Accounts)

    var shouldShowBottomSheet by remember { mutableStateOf(false) }
    val sheetState =
        rememberModalBottomSheetState(
            confirmValueChange = { it != SheetValue.PartiallyExpanded },
            skipPartiallyExpanded = true,
        )

    if (shouldShowBottomSheet) {
        AccountsBottomSheet(
            sheetState = sheetState,
            account = account,
            accountStateViewModel = accountStateViewModel,
            navController = navController,
            onClose = {
                scope.launch {
                    shouldShowBottomSheet = false
                    sheetState.hide()
                }
            },
        )
    }

    Scaffold(
        topBar = {
            AmberTopAppBar(
                destinationRoute = destinationRoute,
                lifecycleOwner = lifecycleOwner,
                scope = scope,
                context = context,
                navBackStackEntry = navBackStackEntry,
                account = account,
                intents = intents,
                packageName = packageName,
            )
        },
        bottomBar = {
            AmberBottomBar(
                items = items,
                navController = navController,
                destinationRoute = destinationRoute,
                scope = scope,
                sheetState = sheetState,
                onShouldShowBottomSheet = {
                    shouldShowBottomSheet = it
                },
                profileUrl = profileUrl,
                account = account,
            )
        },
        floatingActionButton = {
            AmberFloatingButton(
                navController = navController,
                navBackStackEntry = navBackStackEntry,
            )
        },
    ) { padding ->
        var isLoading by remember { mutableStateOf(false) }
        if (isLoading) {
            CenterCircularProgressIndicator(Modifier.padding(padding))
        } else {
            NavHost(
                navController,
                startDestination = localRoute,
                enterTransition = { fadeIn(animationSpec = tween(200)) },
                exitTransition = { fadeOut(animationSpec = tween(200)) },
            ) {
                composable(
                    "login",
                    content = {
                        MainPage(
                            scope = scope,
                            navController = navController,
                        )
                    },
                )

                composable(
                    "create",
                    content = {
                        SignUpPage(
                            accountViewModel = accountStateViewModel,
                            scope = scope,
                            navController = navController,
                            onFinish = {
                                navController.navigate(Route.Applications.route) {
                                    popUpTo(0)
                                }
                            },
                        )
                    },
                )

                composable(
                    "loginPage",
                    content = {
                        LoginPage(
                            accountViewModel = accountStateViewModel,
                            navController = navController,
                            onFinish = {
                                navController.navigate(Route.Applications.route) {
                                    popUpTo(0)
                                }
                            },
                        )
                    },
                )

                composable(
                    Route.IncomingRequest.route,
                    content = {
                        IncomingRequestScreen(
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
                            paddingValues = PaddingValues(
                                top = padding.calculateTopPadding() + (verticalPadding * 1.5f),
                                bottom = padding.calculateBottomPadding(),
                                start = padding.calculateStartPadding(LayoutDirection.Ltr) + verticalPadding,
                                end = padding.calculateEndPadding(LayoutDirection.Ltr) + verticalPadding,
                            ),
                            intents,
                            packageName,
                            appName,
                            account,
                            navController,
                            onRemoveIntentData,
                            onLoading = {
                                isLoading = it
                            },
                        )
                    },
                )

                composable(
                    Route.QrCode.route,
                    arguments = listOf(navArgument("content") { type = NavType.StringType }),
                    content = {
                        it.arguments?.getString("content")?.let {
                            QrCodeScreen(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .padding(horizontal = verticalPadding)
                                    .padding(top = verticalPadding * 1.5f),
                                content = it,
                            )
                        }
                    },
                )

                composable(
                    Route.Applications.route,
                    content = {
                        val scrollState = rememberScrollState()

                        PermissionsScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScrollbar(scrollState)
                                .verticalScroll(scrollState)
                                .padding(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
                            account = account,
                            navController = navController,
                        )
                    },
                )

                composable(
                    Route.Settings.route,
                    content = {
                        val scrollState = rememberScrollState()
                        SettingsScreen(
                            Modifier
                                .fillMaxSize()
                                .verticalScrollbar(scrollState)
                                .verticalScroll(scrollState)
                                .padding(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
                            accountStateViewModel,
                            account,
                            navController,
                        )
                    },
                )

                composable(
                    Route.AccountBackup.route,
                    content = {
                        val scrollState = rememberScrollState()
                        AccountBackupScreen(
                            Modifier
                                .fillMaxSize()
                                .verticalScrollbar(scrollState)
                                .verticalScroll(scrollState)
                                .padding(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f)
                                .imePadding(),
                            account,
                            navController,
                        )
                    },
                )

                composable(
                    Route.Permission.route,
                    arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
                    content = {
                        it.arguments?.getString("packageName")?.let { packageName ->
                            val scrollState = rememberScrollState()
                            EditPermission(
                                modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScrollbar(scrollState)
                                    .verticalScroll(scrollState)
                                    .padding(padding)
                                    .padding(horizontal = verticalPadding)
                                    .padding(top = verticalPadding * 1.5f)
                                    .imePadding(),
                                account = account,
                                selectedPackage = packageName,
                                navController = navController,
                            )
                        }
                    },
                )

                composable(
                    Route.Logs.route,
                    content = {
                        LogsScreen(
                            PaddingValues(
                                top = padding.calculateTopPadding() + (verticalPadding * 1.5f),
                                bottom = padding.calculateBottomPadding(),
                                start = padding.calculateStartPadding(LayoutDirection.Ltr) + verticalPadding,
                                end = padding.calculateEndPadding(LayoutDirection.Ltr) + verticalPadding,
                            ),
                            account = account,
                        )
                    },
                )

                composable(
                    Route.ActiveRelays.route,
                    content = {
                        val scrollState = rememberScrollState()
                        ActiveRelaysScreen(
                            navController = navController,
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScrollbar(scrollState)
                                .verticalScroll(scrollState)
                                .padding(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
                        )
                    },
                )

                composable(
                    Route.Language.route,
                    content = {
                        LanguageScreen(
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
                            account = account,
                        )
                    },
                )

                composable(
                    Route.DefaultRelays.route,
                    content = {
                        DefaultRelaysScreen(
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
                            accountStateViewModel = accountStateViewModel,
                            account = account,
                        )
                    },
                )

                composable(
                    Route.SignPolicy.route,
                    content = {
                        SignPolicySettingsScreen(
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
                            account = account,
                            navController = navController,
                        )
                    },
                )

                composable(
                    Route.Security.route,
                    content = {
                        SecurityScreen(
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
                            navController = navController,
                        )
                    },
                )

                composable(
                    Route.NewApplication.route,
                    content = {
                        val scrollState = rememberScrollState()
                        NewApplicationScreen(
                            account = account,
                            accountStateViewModel = accountStateViewModel,
                            navController = navController,
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScrollbar(scrollState)
                                .verticalScroll(scrollState)
                                .padding(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
                        )
                    },
                )

                composable(
                    Route.NewNsecBunker.route,
                    content = {
                        val scrollState = rememberScrollState()
                        NewNsecBunkerScreen(
                            account = account,
                            accountStateViewModel = accountStateViewModel,
                            navController = navController,
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .imePadding()
                                .verticalScrollbar(scrollState)
                                .verticalScroll(scrollState)
                                .padding(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
                        )
                    },
                )

                composable(
                    Route.NSecBunkerCreated.route,
                    arguments = listOf(navArgument("key") { type = NavType.StringType }),
                    content = {
                        it.arguments?.getString("key")?.let { key ->
                            val scrollState = rememberScrollState()
                            NewNsecBunkerCreatedScreen(
                                account = account,
                                key = key,
                                modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScrollbar(scrollState)
                                    .verticalScroll(scrollState)
                                    .padding(padding)
                                    .padding(horizontal = verticalPadding)
                                    .padding(top = verticalPadding * 1.5f),
                            )
                        }
                    },
                )

                composable(
                    Route.Activity.route,
                    arguments = listOf(navArgument("key") { type = NavType.StringType }),
                    content = {
                        it.arguments?.getString("key")?.let { key ->
                            ActivityScreen(
                                account = account,
                                key = key,
                                paddingValues = PaddingValues(
                                    top = padding.calculateTopPadding() + (verticalPadding * 1.5f),
                                    bottom = padding.calculateBottomPadding(),
                                    start = padding.calculateStartPadding(LayoutDirection.Ltr) + verticalPadding,
                                    end = padding.calculateEndPadding(LayoutDirection.Ltr) + verticalPadding,
                                ),
                                topPadding = padding.calculateTopPadding() + (verticalPadding * 1.5f),
                                modifier =
                                Modifier
                                    .fillMaxSize()
                                    .imePadding(),
                            )
                        }
                    },
                )

                composable(
                    Route.RelayLogScreen.route,
                    arguments = listOf(navArgument("url") { type = NavType.StringType }),
                    content = {
                        it.arguments?.getString("url")?.let { url ->
                            val localUrl = Base64.getDecoder().decode(url).toString(Charsets.UTF_8)
                            RelayLogScreen(
                                url = localUrl,
                                paddingValues = PaddingValues(
                                    top = padding.calculateTopPadding() + (verticalPadding * 1.5f),
                                    bottom = padding.calculateBottomPadding(),
                                    start = padding.calculateStartPadding(LayoutDirection.Ltr) + verticalPadding,
                                    end = padding.calculateEndPadding(LayoutDirection.Ltr) + verticalPadding,
                                ),
                            )
                        }
                    },
                )

                composable(
                    Route.EditConfiguration.route,
                    arguments = listOf(navArgument("key") { type = NavType.StringType }),
                    content = {
                        it.arguments?.getString("key")?.let { key ->
                            val scrollState = rememberScrollState()
                            EditConfigurationScreen(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScrollbar(scrollState)
                                    .verticalScroll(scrollState)
                                    .padding(padding)
                                    .padding(horizontal = verticalPadding)
                                    .padding(top = verticalPadding * 1.5f)
                                    .imePadding(),
                                key = key,
                                accountStateViewModel = accountStateViewModel,
                                account = account,
                                navController = navController,
                            )
                        }
                    },
                )

                composable(
                    Route.SetupPin.route,
                    content = {
                        SetupPinScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
                            navController = navController,
                        )
                    },
                )

                composable(
                    Route.ConfirmPin.route,
                    arguments = listOf(navArgument("pin") { type = NavType.StringType }),
                    content = {
                        it.arguments?.getString("pin")?.let { pin ->
                            ConfirmPinScreen(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .padding(horizontal = verticalPadding)
                                    .padding(top = verticalPadding * 1.5f),
                                accountStateViewModel = accountStateViewModel,
                                pin = pin,
                                navController = navController,
                            )
                        }
                    },
                )

                composable(
                    Route.SeeDetails.route,
                    content = {
                        val scrollState = rememberScrollState()
                        SeeDetailsScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScrollbar(scrollState)
                                .verticalScroll(scrollState)
                                .padding(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
                        )
                    },
                )

                composable(
                    Route.RelaysScreen.route,
                    content = {
                        val scrollState = rememberScrollState()
                        RelaysScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScrollbar(scrollState)
                                .verticalScroll(scrollState)
                                .padding(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
                            navController = navController,
                        )
                    },
                )

                composable(
                    Route.DefaultProfileRelaysScreen.route,
                    content = {
                        val scrollState = rememberScrollState()
                        DefaultProfileRelaysScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScrollbar(scrollState)
                                .verticalScroll(scrollState)
                                .padding(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
                            account = account,
                            accountStateViewModel = accountStateViewModel,
                        )
                    },
                )

                composable(
                    Route.TorSettings.route,
                    content = {
                        val scrollState = rememberScrollState()
                        ConnectOrbotScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScrollbar(scrollState)
                                .verticalScroll(scrollState)
                                .padding(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
                            onPost = {
                                scope.launch(Dispatchers.IO) {
                                    LocalPreferences.updateProxy(context, true, it)
                                    NotificationDataSource.stop()
                                    Amber.instance.checkForNewRelays()
                                    NotificationDataSource.start()
                                    scope.launch {
                                        navController.navigate(Route.Settings.route) {
                                            popUpTo(0)
                                        }
                                    }
                                }
                            },
                            onError = {
                                scope.launch {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.could_not_connect_to_tor),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    },
                )

                composable(
                    Route.EditProfile.route,
                    arguments = listOf(navArgument("key") { type = NavType.StringType }),
                    content = {
                        it.arguments?.getString("key")?.let { key ->
                            val scrollState = rememberScrollState()
                            EditProfileScreen(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScrollbar(scrollState)
                                    .verticalScroll(scrollState)
                                    .padding(padding)
                                    .padding(horizontal = verticalPadding)
                                    .padding(top = verticalPadding * 1.5f),
                                account = account,
                                accountStateViewModel = accountStateViewModel,
                                npub = key,
                            )
                        }
                    },
                )

                composable(
                    Route.Feedback.route,
                    content = {
                        FeedbackScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .consumeWindowInsets(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
                            account = account,
                            accountStateViewModel = accountStateViewModel,
                            onDismiss = {
                                Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                                    navController.navigateUp()
                                }
                            },
                            onLoading = {
                                isLoading = it
                            },
                        )
                    },
                )
            }
        }
    }
}
