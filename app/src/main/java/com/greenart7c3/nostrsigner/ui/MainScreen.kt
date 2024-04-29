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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RichTooltip
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
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.ReturnType
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.nostrsigner
import com.greenart7c3.nostrsigner.relays.Relay
import com.greenart7c3.nostrsigner.relays.RelayPool
import com.greenart7c3.nostrsigner.service.EventNotificationConsumer
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.PushNotificationUtils
import com.greenart7c3.nostrsigner.service.RelayDisconnectService
import com.greenart7c3.nostrsigner.service.getAppCompatActivity
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.actions.AccountsBottomSheet
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.ButtonBorder
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPOutputStream

data class BunkerResponse(
    val id: String,
    val result: String,
    val error: String?
)

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
    appName: String? = null
) {
    onLoading(true)
    GlobalScope.launch(Dispatchers.IO) {
        if (intentData.bunkerRequest != null) {
            RelayPool.getAll().forEach { relay ->
                if (!relay.isConnected()) {
                    relay.connectAndRun {
                        val builder = OneTimeWorkRequest.Builder(RelayDisconnectService::class.java)
                        val inputData = Data.Builder()
                        inputData.putString("relay", relay.url)
                        builder.setInputData(inputData.build())
                        WorkManager.getInstance(nostrsigner.instance).enqueue(builder.build())
                    }
                }
            }
            var count = 0
            while (RelayPool.getAll().any { !it.isReady() } && count < 10) {
                count++
                Thread.sleep(1000)
            }
        }

        if (intentData.bunkerRequest != null && intentData.bunkerRequest.secret.isNotBlank()) {
            val application = database.applicationDao().getBySecret(intentData.bunkerRequest.secret)
            application?.let {
                database.applicationDao().delete(it.application)
            }
        }

        val activity = context.getAppCompatActivity()
        if (intentData.type == SignerType.CONNECT || (intentData.bunkerRequest == null && intentData.type == SignerType.GET_PUBLIC_KEY)) {
            database.applicationDao().deletePermissions(key)
        }

        val relays = intentData.bunkerRequest?.relays ?: listOf()
        val savedApplication = database.applicationDao().getByKey(key)

        val application = savedApplication ?: ApplicationWithPermissions(
            application = ApplicationEntity(
                key,
                appName ?: "",
                relays,
                "",
                "",
                "",
                account.keyPair.pubKey.toHexKey(),
                true,
                intentData.bunkerRequest?.secret ?: "",
                intentData.bunkerRequest?.secret != null
            ),
            permissions = mutableListOf()
        )
        application.application.isConnected = true

        permissions?.filter { it.checked }?.forEach {
            application.permissions.add(
                ApplicationPermissionsEntity(
                    null,
                    key,
                    it.type.toUpperCase(Locale.current),
                    it.kind,
                    true
                )
            )
        }
        if (rememberChoice) {
            application.permissions.add(
                ApplicationPermissionsEntity(
                    null,
                    key,
                    intentData.type.toString(),
                    kind,
                    true
                )
            )
        }

        if (intentData.bunkerRequest == null && intentData.type == SignerType.GET_PUBLIC_KEY) {
            application.permissions.add(
                ApplicationPermissionsEntity(
                    null,
                    key,
                    SignerType.GET_PUBLIC_KEY.toString(),
                    null,
                    true
                )
            )
        }

        if (intentData.bunkerRequest != null) {
            IntentUtils.sendBunkerResponse(
                account,
                intentData.bunkerRequest.localKey,
                BunkerResponse(intentData.bunkerRequest.id, event, null),
                application.application.relays.map { url -> Relay(url) },
                onLoading,
                onSign = {
                    database.applicationDao().insertApplicationWithPermissions(application)
                    PushNotificationUtils.hasInit = false
                    GlobalScope.launch(Dispatchers.IO) {
                        PushNotificationUtils.init(LocalPreferences.allSavedAccounts())
                    }
                }
            ) {
                EventNotificationConsumer(context).notificationManager().cancelAll()
                activity?.intent = null
                activity?.finish()
            }
        } else if (packageName != null) {
            database.applicationDao().insertApplicationWithPermissions(application)

            val intent = Intent()
            intent.putExtra("signature", value)
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
            val result = if (intentData.returnType == ReturnType.SIGNATURE) {
                value
            } else {
                event
            }
            val message = if (intentData.returnType == ReturnType.SIGNATURE) {
                context.getString(R.string.signature_copied_to_the_clipboard)
            } else {
                context.getString(R.string.event_copied_to_the_clipboard)
            }

            clipboardManager.setText(AnnotatedString(result))

            GlobalScope.launch(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    message,
                    Toast.LENGTH_SHORT
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
    goToTop: () -> Unit
) {
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
        horizontalAlignment = Alignment.End
    ) {
        AnimatedVisibility(visible = expanded) {
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    Modifier
                        .padding(end = 10.dp)
                        .clickable {
                            dialogOpen = true
                            expanded = false
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RichTooltip {
                        Text(stringResource(R.string.connect_app))
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                    FloatingActionButton(
                        onClick = {
                            dialogOpen = true
                            expanded = false
                        },
                        modifier = Modifier.size(35.dp),
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Default.QrCode,
                            contentDescription = stringResource(R.string.connect_app),
                            tint = Color.White
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RichTooltip {
                        Text(stringResource(R.string.new_app))
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                    FloatingActionButton(
                        onClick = {
                            goToTop()
                            expanded = false
                        },
                        modifier = Modifier.size(35.dp),
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.new_app),
                            tint = Color.White
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
                easing = LinearEasing
            ),
            label = "rotation"
        )

        FloatingActionButton(
            onClick = {
                expanded = !expanded
            },
            shape = CircleShape
        ) {
            Icon(
                if (expanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = stringResource(R.string.connect_app),
                modifier = Modifier.rotate(rotation)
            )
        }
    }
}

private suspend fun askNotificationPermission(
    context: Context,
    requestPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    onShouldShowRequestPermissionRationale: () -> Unit
) {
    if (ContextCompat.checkSelfPermission(context, "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) {
        initNotifications()
        return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shouldShowRationale = LocalPreferences.shouldShowRationale()
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

private suspend fun initNotifications() {
    PushNotificationUtils.hasInit = false
    PushNotificationUtils.init(LocalPreferences.allSavedAccounts())
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
    database: AppDatabase
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val destinationRoute = navBackStackEntry?.destination?.route ?: ""
    val items = listOf(Route.Home, Route.Permissions, Route.Settings)
    var shouldShowBottomSheet by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(
        confirmValueChange = { it != SheetValue.PartiallyExpanded },
        skipPartiallyExpanded = true
    )
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val clipboardManager = LocalClipboardManager.current

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            scope.launch(Dispatchers.IO) {
                initNotifications()
            }
        } else {
            if (LocalPreferences.shouldShowRationale() == null) {
                LocalPreferences.updateShoulShowRationale(true)
            }
        }
    }
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    if (BuildConfig.FLAVOR != "offline") {
        LaunchedEffect(Unit) {
            launch(Dispatchers.IO) {
                askNotificationPermission(
                    context,
                    requestPermissionLauncher
                ) {
                    showDialog = true
                }
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
                    }
                ) {
                    Text(text = "Allow")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showDialog = false
                        LocalPreferences.updateShoulShowRationale(false)
                    }
                ) {
                    Text(text = "Deny")
                }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            if (destinationRoute == "Permissions" && BuildConfig.FLAVOR != "offline") {
                PermissionsFloatingActionButton(
                    accountStateViewModel,
                    account
                ) {
                    val secret = UUID.randomUUID().toString().substring(0, 6)
                    scope.launch(Dispatchers.IO) {
                        val application = ApplicationEntity(
                            secret,
                            "",
                            listOf("wss://relay.nsec.app"),
                            "",
                            "",
                            "",
                            account.keyPair.pubKey.toHexKey(),
                            false,
                            secret,
                            false
                        )

                        database.applicationDao().insertApplication(
                            application
                        )
                        val bunkerUrl = "bunker://${account.keyPair.pubKey.toHexKey()}?relay=wss://relay.nsec.app"
                        clipboardManager.setText(AnnotatedString(bunkerUrl))
                        scope.launch(Dispatchers.Main) {
                            navController.navigate("Permission/$secret")
                        }
                    }
                }
            }
        },
        topBar = {
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
                    }
                )
            }

            CenterAlignedTopAppBar(
                title = {
                    val name = LocalPreferences.getAccountName(account.keyPair.pubKey.toNpub())
                    Row(
                        Modifier
                            .border(
                                border = ButtonDefaults.outlinedButtonBorder,
                                shape = ButtonBorder
                            )
                            .padding(8.dp)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                scope.launch {
                                    sheetState.show()
                                    shouldShowBottomSheet = true
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name.ifBlank { account.keyPair.pubKey.toNpub().toShortenHex() })
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (!destinationRoute.contains("Permission/")) {
                NavigationBar(tonalElevation = 0.dp) {
                    items.forEach {
                        val selected = destinationRoute == it.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(it.route) {
                                    popUpTo(0)
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) it.selectedIcon else it.icon,
                                    it.route
                                )
                            },
                            label = {
                                Text(it.route)
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        var localRoute by remember { mutableStateOf(route.value ?: Route.Home.route) }

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
            exitTransition = { fadeOut(animationSpec = tween(200)) }
        ) {
            composable(
                Route.Home.route,
                content = {
                    HomeScreen(
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        intents,
                        packageName,
                        appName,
                        account,
                        database
                    )
                }
            )

            composable(
                Route.Permissions.route,
                content = {
                    PermissionsScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        account = account,
                        accountStateViewModel = accountStateViewModel,
                        navController = navController,
                        database = database
                    )
                }
            )

            composable(
                Route.Settings.route,
                content = {
                    SettingsScreen(
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        accountStateViewModel,
                        account
                    )
                }
            )

            composable(
                Route.Permission.route,
                arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
                content = {
                    EditPermission(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        account = account,
                        accountStateViewModel = accountStateViewModel,
                        selectedPackage = it.arguments?.getString("packageName")!!,
                        navController = navController,
                        database = database
                    )
                }
            )
        }
    }
}
