package com.greenart7c3.nostrsigner.ui

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
import com.greenart7c3.nostrsigner.ui.actions.ActiveRelaysScreen
import com.greenart7c3.nostrsigner.ui.actions.DefaultRelaysScreen
import com.greenart7c3.nostrsigner.ui.components.BackButtonScaffold
import com.greenart7c3.nostrsigner.ui.components.MainScaffold
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsFloatingActionButton(
    accountStateViewModel: AccountStateViewModel,
    account: Account,
    goToTop: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }
    var dialogOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (dialogOpen) {
        SimpleQrCodeScanner {
            dialogOpen = false
            if (!it.isNullOrEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(it)
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.getAppCompatActivity()?.startActivity(intent)
                accountStateViewModel.switchUser(account.keyPair.pubKey.toNpub(), Route.Home.route)
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.End,
    ) {
        AnimatedVisibility(visible = expanded) {
            Column(
                horizontalAlignment = Alignment.End,
            ) {
                Row(
                    Modifier
                        .padding(end = 10.dp)
                        .clickable {
                            clipboardManager.getText()?.let {
                                if (it.text.isBlank()) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.invalid_nostr_connect_uri),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    return@let
                                }
                                if (!it.text.startsWith("nostrconnect://")) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.invalid_nostr_connect_uri),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    return@let
                                }

                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.data = Uri.parse(it.text)
                                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                context.getAppCompatActivity()?.startActivity(intent)
                                accountStateViewModel.switchUser(account.keyPair.pubKey.toNpub(), Route.Home.route)
                            }
                            expanded = false
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tooltipState = rememberTooltipState()
                    val positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider()

                    TooltipBox(
                        content = {
                            Surface(
                                shape = TooltipDefaults.richTooltipContainerShape,
                                color = TooltipDefaults.richTooltipColors().containerColor,
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.paste_from_clipboard),
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                }
                            }
                        },
                        state = tooltipState,
                        positionProvider = positionProvider,
                        tooltip = {},
                    )

                    Spacer(modifier = Modifier.size(4.dp))
                    FloatingActionButton(
                        onClick = {
                            clipboardManager.getText()?.let {
                                if (it.text.isBlank()) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.invalid_nostr_connect_uri),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    return@let
                                }
                                if (!it.text.startsWith("nostrconnect://")) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.invalid_nostr_connect_uri),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    return@let
                                }

                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.data = Uri.parse(it.text)
                                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                context.getAppCompatActivity()?.startActivity(intent)
                                accountStateViewModel.switchUser(account.keyPair.pubKey.toNpub(), Route.Home.route)
                            }
                            expanded = false
                        },
                        modifier = Modifier.size(35.dp),
                        shape = CircleShape,
                    ) {
                        Icon(
                            Icons.Default.ContentPaste,
                            contentDescription = stringResource(R.string.paste_from_clipboard),
                            tint = Color.White,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    Modifier
                        .padding(end = 10.dp)
                        .clickable {
                            dialogOpen = true
                            expanded = false
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tooltipState = rememberTooltipState()
                    val positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider()

                    TooltipBox(
                        content = {
                            Surface(
                                shape = TooltipDefaults.richTooltipContainerShape,
                                color = TooltipDefaults.richTooltipColors().containerColor,
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.connect_app),
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                }
                            }
                        },
                        state = tooltipState,
                        positionProvider = positionProvider,
                        tooltip = { },
                    )

                    Spacer(modifier = Modifier.size(4.dp))
                    FloatingActionButton(
                        onClick = {
                            dialogOpen = true
                            expanded = false
                        },
                        modifier = Modifier.size(35.dp),
                        shape = CircleShape,
                    ) {
                        Icon(
                            Icons.Default.QrCode,
                            contentDescription = stringResource(R.string.connect_app),
                            tint = Color.White,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    Modifier
                        .padding(end = 10.dp)
                        .clickable {
                            goToTop()
                            expanded = false
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tooltipState = rememberTooltipState()
                    val positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider()

                    TooltipBox(
                        content = {
                            Surface(
                                shape = TooltipDefaults.richTooltipContainerShape,
                                color = TooltipDefaults.richTooltipColors().containerColor,
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.new_app),
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                }
                            }
                        },
                        state = tooltipState,
                        positionProvider = positionProvider,
                        tooltip = { },
                    )

                    Spacer(modifier = Modifier.size(4.dp))
                    FloatingActionButton(
                        onClick = {
                            goToTop()
                            expanded = false
                        },
                        modifier = Modifier.size(35.dp),
                        shape = CircleShape,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.new_app),
                            tint = Color.White,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        val rotation by animateFloatAsState(
            targetValue = if (expanded) 0f else 180f,
            animationSpec = tween(
                durationMillis = 150,
                easing = LinearEasing,
            ),
            label = "rotation",
        )

        FloatingActionButton(
            onClick = {
                expanded = !expanded
            },
            shape = CircleShape,
        ) {
            Icon(
                if (expanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = stringResource(R.string.connect_app),
                modifier = Modifier.rotate(rotation),
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

@Composable
fun MainScreen(
    account: Account,
    accountStateViewModel: AccountStateViewModel,
    intents: List<IntentData>,
    packageName: String?,
    appName: String?,
    route: MutableState<String?>,
    database: AppDatabase,
) {
    val navController = rememberNavController()
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

    var localRoute by remember { mutableStateOf(route.value ?: Route.Home.route) }

    @Suppress("KotlinConstantConditions")
    if (BuildConfig.FLAVOR != "offline") {
        LaunchedEffect(Unit, route.value) {
            launch(Dispatchers.Main) {
                if (route.value != null) {
                    localRoute = route.value!!
                    route.value = null
                }
            }
        }
    }

    NavHost(
        navController,
        startDestination = localRoute,
        enterTransition = { fadeIn(animationSpec = tween(200)) },
        exitTransition = { fadeOut(animationSpec = tween(200)) },
    ) {
        composable(
            Route.Home.route,
            content = {
                MainScaffold(
                    accountStateViewModel,
                    account,
                    database,
                    navController,
                    destinationRoute,
                    false,
                ) { padding ->
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
                }
            },
        )

        composable(
            Route.Permissions.route,
            content = {
                MainScaffold(
                    accountStateViewModel,
                    account,
                    database,
                    navController,
                    destinationRoute,
                    true,
                ) { padding ->
                    PermissionsScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        account = account,
                        accountStateViewModel = accountStateViewModel,
                        navController = navController,
                        database = database,
                    )
                }
            },
        )

        composable(
            Route.Settings.route,
            content = {
                MainScaffold(
                    accountStateViewModel,
                    account,
                    database,
                    navController,
                    destinationRoute,
                    false,
                ) { padding ->
                    SettingsScreen(
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        accountStateViewModel,
                        account,
                        navController,
                    )
                }
            },
        )

        composable(
            Route.AccountBackup.route,
            content = {
                BackButtonScaffold(
                    navController = navController,
                ) { padding ->
                    AccountBackupScreen(
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        account,
                    )
                }
            },
        )

        composable(
            Route.Permission.route,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
            content = {
                BackButtonScaffold(
                    navController = navController,
                ) { padding ->
                    EditPermission(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        account = account,
                        accountStateViewModel = accountStateViewModel,
                        selectedPackage = it.arguments?.getString("packageName")!!,
                        navController = navController,
                        database = database,
                    )
                }
            },
        )

        composable(
            Route.Logs.route,
            content = {
                BackButtonScaffold(
                    navController = navController,
                ) { padding ->
                    LogsScreen(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        account = account,
                    )
                }
            },
        )

        composable(
            Route.ActiveRelays.route,
            content = {
                BackButtonScaffold(
                    navController = navController,
                ) { padding ->
                    ActiveRelaysScreen(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
            },
        )

        composable(
            Route.Language.route,
            content = {
                BackButtonScaffold(
                    navController = navController,
                ) { padding ->
                    LanguageScreen(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        account = account,
                    )
                }
            },
        )

        composable(
            Route.NotificationType.route,
            content = {
                BackButtonScaffold(
                    navController = navController,
                ) { padding ->
                    NotificationTypeScreen(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        onDone = {
                            navController.navigateUp()
                        },
                    )
                }
            },
        )

        composable(
            Route.DefaultRelays.route,
            content = {
                BackButtonScaffold(
                    navController = navController,
                ) { padding ->
                    DefaultRelaysScreen(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        navController = navController,
                        accountStateViewModel = accountStateViewModel,
                        account = account,
                    )
                }
            },
        )

        composable(
            Route.SignPolicy.route,
            content = {
                BackButtonScaffold(
                    navController = navController,
                ) { padding ->
                    SignPolicySettingsScreen(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        account = account,
                        navController = navController,
                    )
                }
            },
        )

        composable(
            Route.Security.route,
            content = {
                BackButtonScaffold(
                    navController = navController,
                ) { padding ->
                    SecurityScreen(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        navController = navController,
                    )
                }
            },
        )
    }
}
