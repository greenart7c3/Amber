package com.greenart7c3.nostrsigner.ui

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.BunkerResponse
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.ReturnType
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.models.basicPermissions
import com.greenart7c3.nostrsigner.service.EventNotificationConsumer
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.PushNotificationUtils
import com.greenart7c3.nostrsigner.service.getAppCompatActivity
import com.greenart7c3.nostrsigner.ui.actions.AccountBackupScreen
import com.greenart7c3.nostrsigner.ui.actions.AccountsBottomSheet
import com.greenart7c3.nostrsigner.ui.actions.ActiveRelaysScreen
import com.greenart7c3.nostrsigner.ui.actions.ActivityScreen
import com.greenart7c3.nostrsigner.ui.actions.DefaultRelaysScreen
import com.greenart7c3.nostrsigner.ui.actions.RelayLogScreen
import com.greenart7c3.nostrsigner.ui.components.IconRow
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.navigation.routes
import com.vitorpamplona.quartz.encoders.toHexKey
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
fun sendResult(
    context: Context,
    packageName: String?,
    account: Account,
    key: String,
    rememberChoice: Boolean,
    clipboardManager: ClipboardManager,
    event: String,
    value: String,
    intentData: IntentData,
    kind: Int?,
    database: AppDatabase,
    onLoading: (Boolean) -> Unit,
    permissions: List<Permission>? = null,
    appName: String? = null,
    signPolicy: Int? = null,
) {
    onLoading(true)
    GlobalScope.launch(Dispatchers.IO) {
        val defaultRelays = NostrSigner.getInstance().settings.defaultRelays
        val savedApplication = database.applicationDao().getByKey(key)
        val relays = savedApplication?.application?.relays?.ifEmpty { defaultRelays } ?: (intentData.bunkerRequest?.relays?.ifEmpty { defaultRelays } ?: defaultRelays)
        if (intentData.bunkerRequest != null) {
            NostrSigner.getInstance().checkForNewRelays(
                NostrSigner.getInstance().settings.notificationType != NotificationType.DIRECT,
                relays.toSet(),
            )
        }

        val activity = context.getAppCompatActivity()
        val localAppName =
            if (packageName != null) {
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(info).toString()
            } else {
                appName
            }

        val application =
            savedApplication ?: ApplicationWithPermissions(
                application = ApplicationEntity(
                    key,
                    appName ?: localAppName ?: "",
                    if (packageName != null) emptyList() else relays,
                    "",
                    "",
                    "",
                    account.keyPair.pubKey.toHexKey(),
                    true,
                    intentData.bunkerRequest?.secret ?: "",
                    intentData.bunkerRequest?.secret != null,
                    account.signPolicy,
                ),
                permissions = mutableListOf(),
            )
        application.application.isConnected = true

        if (signPolicy != null) {
            when (signPolicy) {
                0 -> {
                    application.application.signPolicy = 0
                    basicPermissions.forEach {
                        if (application.permissions.any { permission -> permission.type == it.type.toUpperCase(Locale.current) && permission.kind == it.kind }) {
                            return@forEach
                        }
                        application.permissions.add(
                            ApplicationPermissionsEntity(
                                null,
                                key,
                                it.type.toUpperCase(Locale.current),
                                it.kind,
                                true,
                            ),
                        )
                    }
                }
                1 -> {
                    application.application.signPolicy = 1
                    permissions?.filter { it.checked }?.forEach {
                        if (application.permissions.any { permission -> permission.type == it.type.toUpperCase(Locale.current) && permission.kind == it.kind }) {
                            return@forEach
                        }
                        application.permissions.add(
                            ApplicationPermissionsEntity(
                                null,
                                key,
                                it.type.toUpperCase(Locale.current),
                                it.kind,
                                true,
                            ),
                        )
                    }
                }
                2 -> {
                    application.application.signPolicy = 2
                }
            }
        }

        if (rememberChoice) {
            application.permissions.add(
                ApplicationPermissionsEntity(
                    null,
                    key,
                    intentData.type.toString(),
                    kind,
                    true,
                ),
            )
        }

        if (intentData.bunkerRequest == null && intentData.type == SignerType.GET_PUBLIC_KEY) {
            application.permissions.add(
                ApplicationPermissionsEntity(
                    null,
                    key,
                    SignerType.GET_PUBLIC_KEY.toString(),
                    null,
                    true,
                ),
            )
        }

        if (intentData.bunkerRequest != null) {
            IntentUtils.sendBunkerResponse(
                context,
                account,
                intentData.bunkerRequest,
                BunkerResponse(intentData.bunkerRequest.id, event, null),
                relays,
                onLoading,
                onDone = {
                    if (intentData.bunkerRequest.secret.isNotBlank()) {
                        val secretApplication = database.applicationDao().getBySecret(intentData.bunkerRequest.secret)
                        secretApplication?.let {
                            database.applicationDao().delete(it.application)
                        }
                    }
                    if (intentData.type == SignerType.CONNECT) {
                        database.applicationDao().deletePermissions(key)
                    }
                    database.applicationDao().insertApplicationWithPermissions(application)
                    database.applicationDao().addHistory(
                        HistoryEntity(
                            0,
                            key,
                            intentData.type.toString(),
                            kind,
                            TimeUtils.now(),
                            true,
                        ),
                    )
                    PushNotificationUtils.hasInit = false
                    GlobalScope.launch(Dispatchers.IO) {
                        PushNotificationUtils.init(LocalPreferences.allSavedAccounts(context))
                    }

                    EventNotificationConsumer(context).notificationManager().cancelAll()
                    activity?.intent = null
                    activity?.finish()
                },
            )
        } else if (packageName != null) {
            database.applicationDao().insertApplicationWithPermissions(application)
            database.applicationDao().addHistory(
                HistoryEntity(
                    0,
                    key,
                    intentData.type.toString(),
                    kind,
                    TimeUtils.now(),
                    true,
                ),
            )

            val intent = Intent()
            intent.putExtra("signature", value)
            intent.putExtra("result", value)
            intent.putExtra("id", intentData.id)
            intent.putExtra("event", event)
            if (intentData.type == SignerType.GET_PUBLIC_KEY) {
                intent.putExtra("package", BuildConfig.APPLICATION_ID)
            }
            activity?.setResult(RESULT_OK, intent)
            activity?.intent = null
            activity?.finish()
        } else if (!intentData.callBackUrl.isNullOrBlank()) {
            if (intentData.returnType == ReturnType.SIGNATURE) {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(intentData.callBackUrl + Uri.encode(value))
                context.startActivity(intent)
            } else {
                if (intentData.compression == CompressionType.GZIP) {
                    // Compress the string using GZIP
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    val gzipOutputStream = GZIPOutputStream(byteArrayOutputStream)
                    gzipOutputStream.write(event.toByteArray())
                    gzipOutputStream.close()

                    // Convert the compressed data to Base64
                    val compressedData = byteArrayOutputStream.toByteArray()
                    val encodedString = Base64.getEncoder().encodeToString(compressedData)
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = Uri.parse(intentData.callBackUrl + Uri.encode("Signer1$encodedString"))
                    context.startActivity(intent)
                } else {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = Uri.parse(intentData.callBackUrl + Uri.encode(event))
                    context.startActivity(intent)
                }
            }
            activity?.intent = null
            activity?.finish()
        } else {
            val result =
                if (intentData.returnType == ReturnType.SIGNATURE) {
                    value
                } else {
                    event
                }
            val message =
                if (intentData.returnType == ReturnType.SIGNATURE) {
                    context.getString(R.string.signature_copied_to_the_clipboard)
                } else {
                    context.getString(R.string.event_copied_to_the_clipboard)
                }

            clipboardManager.setText(AnnotatedString(result))

            GlobalScope.launch(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    message,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            activity?.intent = null
            activity?.finish()
        }
    }
}

@Composable
fun PermissionsFloatingActionButton(
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
    ) {
        FloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.connect_app),
                tint = Color.White,
            )
        }
    }
}

private suspend fun askNotificationPermission(
    context: Context,
    requestPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    onShouldShowRequestPermissionRationale: () -> Unit,
) {
    if (ContextCompat.checkSelfPermission(context, "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) {
        initNotifications(
            context = context,
        )
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

private suspend fun initNotifications(context: Context) {
    PushNotificationUtils.hasInit = false
    PushNotificationUtils.init(LocalPreferences.allSavedAccounts(context))
}

fun Color.Companion.fromHex(colorString: String) = try {
    Color(android.graphics.Color.parseColor("#$colorString"))
} catch (e: Exception) {
    Log.e("Color", "Failed to parse color: $colorString", e)
    Unspecified
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    account: Account,
    accountStateViewModel: AccountStateViewModel,
    intents: List<IntentData>,
    packageName: String?,
    appName: String?,
    route: MutableState<String?>,
    database: AppDatabase,
    navController: NavHostController,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val destinationRoute = navBackStackEntry?.destination?.route ?: ""
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val requestPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted: Boolean ->
            if (isGranted) {
                scope.launch(Dispatchers.IO) {
                    initNotifications(
                        context = context,
                    )
                }
            } else {
                if (LocalPreferences.shouldShowRationale(context) == null) {
                    LocalPreferences.updateShoulShowRationale(context, true)
                }
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
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
            },
            title = {
                Text(text = "Permission Needed")
            },
            text = {
                Text(text = "Notifications are needed to use Amber as a nsec bunker.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDialog = false
                        requestPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
                    },
                ) {
                    Text(text = "Allow")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showDialog = false
                        LocalPreferences.updateShoulShowRationale(context, false)
                    },
                ) {
                    Text(text = "Deny")
                }
            },
        )
    }

    var localRoute by remember { mutableStateOf(route.value ?: if (intents.isEmpty()) Route.Applications.route else Route.IncomingRequest.route) }

    @Suppress("KotlinConstantConditions")
    if (BuildConfig.FLAVOR != "offline") {
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
    }

    val items = mutableListOf(Route.Applications, Route.IncomingRequest, Route.ActiveRelays, Route.Settings, Route.Accounts)
    @Suppress("KotlinConstantConditions")
    if (BuildConfig.FLAVOR == "offline") {
        items.remove(Route.ActiveRelays)
    }

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
            CenterAlignedTopAppBar(
                title = {
                    var title by remember { mutableStateOf(routes.find { it.route.startsWith(destinationRoute) }?.title ?: "") }
                    LaunchedEffect(destinationRoute) {
                        if (destinationRoute.startsWith("Permission/")) {
                            launch(Dispatchers.IO) {
                                navBackStackEntry?.arguments?.getString("packageName")?.let { packageName ->
                                    title = database.applicationDao().getByKey(packageName)?.application?.name ?: packageName
                                }
                            }
                        } else {
                            title = routes.find { it.route == destinationRoute }?.title ?: ""
                        }
                    }

                    Text(title)
                },
            )
        },
        bottomBar = {
            if (destinationRoute in items.map { it.route }) {
                NavigationBar(
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        Modifier
                            .padding(horizontal = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items.forEach {
                            val selected = destinationRoute == it.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (it.route == Route.Accounts.route) {
                                        scope.launch {
                                            sheetState.show()
                                            shouldShowBottomSheet = true
                                        }
                                    } else {
                                        navController.navigate(it.route) {
                                            popUpTo(0)
                                        }
                                    }
                                },
                                icon = {
                                    if (it.route == Route.Accounts.route) {
                                        Icon(
                                            if (selected) it.selectedIcon else it.icon,
                                            it.route,
                                            modifier = Modifier.border(
                                                2.dp,
                                                Color.fromHex(account.keyPair.pubKey.toHexKey().slice(0..5)),
                                                CircleShape,
                                            ),
                                        )
                                    } else {
                                        Icon(
                                            if (selected) it.selectedIcon else it.icon,
                                            it.route,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            } else {
                val localBackButtonTitle = remember {
                    routes.find { it.route == navController.previousBackStackEntry?.destination?.route }?.title ?: ""
                }
                if (localBackButtonTitle.isNotBlank()) {
                    BottomAppBar {
                        IconRow(
                            title = if (destinationRoute.startsWith("NewNsecBunkerCreated/")) {
                                stringResource(R.string.back_to, localBackButtonTitle)
                            } else {
                                if (destinationRoute == "NewNsecBunker") {
                                    stringResource(R.string.back_to, stringResource(R.string.add_a_new_application))
                                } else {
                                    stringResource(R.string.back_to, localBackButtonTitle)
                                }
                            },
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            onClick = {
                                if (destinationRoute.startsWith("NewNsecBunkerCreated/")) {
                                    navController.navigate(Route.Applications.route) {
                                        popUpTo(0)
                                    }
                                } else {
                                    navController.navigateUp()
                                }
                            },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (navBackStackEntry?.destination?.route == Route.Applications.route) {
                PermissionsFloatingActionButton(
                    onClick = {
                        navController.navigate(Route.NewApplication.route)
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController,
            startDestination = localRoute,
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
        ) {
            composable(
                Route.IncomingRequest.route,
                content = {
                    HomeScreen(
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        intents,
                        packageName,
                        appName,
                        account,
                        database,
                    )
                },
            )

            composable(
                Route.Applications.route,
                content = {
                    PermissionsScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        account = account,
                        accountStateViewModel = accountStateViewModel,
                        navController = navController,
                        database = database,
                    )
                },
            )

            composable(
                Route.Settings.route,
                content = {
                    SettingsScreen(
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        accountStateViewModel,
                        account,
                        navController,
                    )
                },
            )

            composable(
                Route.AccountBackup.route,
                content = {
                    AccountBackupScreen(
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        account,
                    )
                },
            )

            composable(
                Route.Permission.route,
                arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
                content = {
                    it.arguments?.getString("packageName")?.let { packageName ->
                        EditPermission(
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding),
                            account = account,
                            selectedPackage = packageName,
                            navController = navController,
                            database = database,
                        )
                    }
                },
            )

            composable(
                Route.Logs.route,
                content = {
                    LogsScreen(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        account = account,
                    )
                },
            )

            composable(
                Route.ActiveRelays.route,
                content = {
                    ActiveRelaysScreen(
                        navController = navController,
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
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
                            .padding(padding),
                        account = account,
                    )
                },
            )

            composable(
                Route.NotificationType.route,
                content = {
                    NotificationTypeScreen(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        onDone = {
                            navController.navigateUp()
                        },
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
                            .padding(padding),
                        navController = navController,
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
                            .padding(padding),
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
                            .padding(padding),
                        navController = navController,
                    )
                },
            )

            composable(
                Route.NewApplication.route,
                content = {
                    NewApplicationScreen(
                        account = account,
                        accountStateViewModel = accountStateViewModel,
                        navController = navController,
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                },
            )

            composable(
                Route.NewNsecBunker.route,
                content = {
                    NewNsecBunkerScreen(
                        database = database,
                        account = account,
                        accountStateViewModel = accountStateViewModel,
                        navController = navController,
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                },
            )

            composable(
                Route.NSecBunkerCreated.route,
                arguments = listOf(navArgument("key") { type = NavType.StringType }),
                content = {
                    it.arguments?.getString("key")?.let { key ->
                        NewNsecBunkerCreatedScreen(
                            database = database,
                            account = account,
                            key = key,
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding),
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
                            database = database,
                            key = key,
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding),
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
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding),
                        )
                    }
                },
            )

            composable(
                Route.EditConfiguration.route,
                arguments = listOf(navArgument("key") { type = NavType.StringType }),
                content = {
                    it.arguments?.getString("key")?.let { key ->
                        EditConfigurationScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            database = database,
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
                            .padding(padding),
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
                                .padding(padding),
                            accountStateViewModel = accountStateViewModel,
                            pin = pin,
                            navController = navController,
                        )
                    }
                },
            )
        }
    }
}
