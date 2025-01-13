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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import coil3.compose.AsyncImage
import com.anggrayudi.storage.SimpleStorageHelper
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
import com.greenart7c3.nostrsigner.models.basicPermissions
import com.greenart7c3.nostrsigner.service.BunkerRequestUtils
import com.greenart7c3.nostrsigner.service.EventNotificationConsumer
import com.greenart7c3.nostrsigner.service.NotificationDataSource
import com.greenart7c3.nostrsigner.service.getAppCompatActivity
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.actions.AccountBackupScreen
import com.greenart7c3.nostrsigner.ui.actions.AccountsBottomSheet
import com.greenart7c3.nostrsigner.ui.actions.ActiveRelaysScreen
import com.greenart7c3.nostrsigner.ui.actions.ActivityScreen
import com.greenart7c3.nostrsigner.ui.actions.ConnectOrbotScreen
import com.greenart7c3.nostrsigner.ui.actions.DefaultRelaysScreen
import com.greenart7c3.nostrsigner.ui.actions.RelayLogScreen
import com.greenart7c3.nostrsigner.ui.components.IconRow
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.navigation.routes
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
) {
    onLoading(true)
    NostrSigner.getInstance().applicationIOScope.launch {
        val defaultRelays = NostrSigner.getInstance().settings.defaultRelays
        var savedApplication = database.applicationDao().getByKey(key)
        if (savedApplication == null && intentData.bunkerRequest?.secret != null) {
            savedApplication = database.applicationDao().getByKey(intentData.bunkerRequest.secret)
            if (savedApplication != null) {
                val tempApplication = savedApplication.application.copy(
                    key = key,
                )
                val tempApp2 = ApplicationWithPermissions(
                    application = tempApplication,
                    permissions = savedApplication.permissions.map {
                        it.copy()
                    }.toMutableList(),
                )

                database.applicationDao().delete(intentData.bunkerRequest.secret)
                database.applicationDao().insertApplicationWithPermissions(tempApp2)
            }
        }
        savedApplication = database.applicationDao().getByKey(key)
        val relays = savedApplication?.application?.relays?.ifEmpty { defaultRelays } ?: (intentData.bunkerRequest?.relays?.ifEmpty { defaultRelays } ?: defaultRelays)
        if (intentData.bunkerRequest != null) {
            NostrSigner.getInstance().checkForNewRelays(
                newRelays = relays.toSet(),
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
                    account.signer.keyPair.pubKey.toHexKey(),
                    true,
                    intentData.bunkerRequest?.secret ?: "",
                    intentData.bunkerRequest?.secret != null,
                    account.signPolicy,
                    intentData.bunkerRequest?.closeApplication ?: true,
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

        if (intentData.type == SignerType.CONNECT) {
            database.applicationDao().deletePermissions(key)
        }
        if ((intentData.bunkerRequest == null && intentData.type == SignerType.GET_PUBLIC_KEY) || (intentData.bunkerRequest != null && intentData.type == SignerType.CONNECT)) {
            application.application.isConnected = true
            if (!application.permissions.any { it.type == SignerType.GET_PUBLIC_KEY.toString() }) {
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
        }

        if (intentData.bunkerRequest != null) {
            val localIntentData = intentData.copy()

            BunkerRequestUtils.clearRequests()

            // assume that everything worked and try to revert it if it fails
            EventNotificationConsumer(context).notificationManager().cancelAll()
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

            EventNotificationConsumer(context).notificationManager().cancelAll()
            onRemoveIntentData(listOf(intentData), IntentResultType.REMOVE)

            BunkerRequestUtils.sendBunkerResponse(
                context,
                account,
                intentData.bunkerRequest,
                BunkerResponse(intentData.bunkerRequest.id, event, null),
                application.application.relays.ifEmpty { relays },
                onLoading,
                onDone = {
                    NostrSigner.getInstance().applicationIOScope.launch {
                        if (!it) {
                            if (rememberChoice) {
                                if (intentData.type == SignerType.SIGN_EVENT) {
                                    kind?.let {
                                        database.applicationDao().deletePermissions(key, intentData.type.toString(), kind)
                                    }
                                } else {
                                    database.applicationDao().deletePermissions(key, intentData.type.toString())
                                }
                            }

                            onLoading(false)
                            BunkerRequestUtils.addRequest(localIntentData.bunkerRequest!!)
                        } else {
                            activity?.intent = null
                            if (application.application.closeApplication) {
                                activity?.finish()
                            }
                        }
                    }
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
            onRemoveIntentData(listOf(intentData), IntentResultType.REMOVE)
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
            onRemoveIntentData(listOf(intentData), IntentResultType.REMOVE)
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

            NostrSigner.getInstance().applicationIOScope.launch(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    message,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            onRemoveIntentData(listOf(intentData), IntentResultType.REMOVE)
            activity?.intent = null
            activity?.finish()
        }
    }
}

@Composable
fun NewBunkerFloatingButton(
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
    ) {
        FloatingActionButton(
            modifier = Modifier
                .padding(end = 8.dp),
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                pressedElevation = 0.dp,
            ),
            onClick = onClick,
            shape = RoundedCornerShape(24),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.connect_app),
                tint = Color.Black,
            )
        }
    }
}

private fun askNotificationPermission(
    context: Context,
    requestPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
    onShouldShowRequestPermissionRationale: () -> Unit,
) {
    if (ContextCompat.checkSelfPermission(context, "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) {
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

fun Color.Companion.fromHex(colorString: String) = try {
    Color(android.graphics.Color.parseColor("#$colorString"))
} catch (e: Exception) {
    Log.e("Color", "Failed to parse color: $colorString", e)
    Unspecified
}

enum class IntentResultType {
    REMOVE,
    ADD,
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
    storageHelper: SimpleStorageHelper,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
) {
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

                NostrSigner.getInstance().fetchProfileData(
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

    val items = mutableListOf(Route.Applications, Route.IncomingRequest, Route.Settings, Route.Accounts)
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
            if (destinationRoute != "login" && destinationRoute != "create" && destinationRoute != "loginPage") {
                CenterAlignedTopAppBar(
                    title = {
                        var title by remember { mutableStateOf(routes.find { it.route.startsWith(destinationRoute) }?.title ?: "") }
                        LaunchedEffect(destinationRoute) {
                            if (destinationRoute.startsWith("Permission/") || destinationRoute.startsWith("Activity/") || destinationRoute.startsWith("RelayLogScreen/")) {
                                launch(Dispatchers.IO) {
                                    navBackStackEntry?.arguments?.getString("packageName")?.let { packageName ->
                                        val application = database.applicationDao().getByKey(packageName)?.application
                                        title = if (destinationRoute.startsWith("Activity/")) {
                                            "${application?.name?.ifBlank { application.key.toShortenHex() } ?: packageName} - ${routes.find { it.route.startsWith(destinationRoute) }?.title}"
                                        } else {
                                            application?.name?.ifBlank { application.key.toShortenHex() } ?: packageName
                                        }
                                    }
                                    navBackStackEntry?.arguments?.getString("key")?.let { packageName ->
                                        val application = database.applicationDao().getByKey(packageName)?.application
                                        title = if (destinationRoute.startsWith("Activity/")) {
                                            "${application?.name?.ifBlank { application.key.toShortenHex() } ?: packageName} - ${routes.find { it.route.startsWith(destinationRoute) }?.title}"
                                        } else {
                                            application?.name?.ifBlank { application.key.toShortenHex() } ?: packageName
                                        }
                                    }
                                    navBackStackEntry?.arguments?.getString("url")?.let { url ->
                                        val localUrl = Base64.getDecoder().decode(url).toString(Charsets.UTF_8)
                                        title = localUrl
                                    }
                                }
                            } else {
                                launch(Dispatchers.IO) {
                                    if (destinationRoute == Route.IncomingRequest.route && intents.isNotEmpty()) {
                                        val application = database.applicationDao().getByKey(intents.first().bunkerRequest?.localKey ?: packageName ?: "")?.application
                                        val titleTemp = application?.name?.ifBlank { application.key.toShortenHex() } ?: packageName ?: ""
                                        title = if (titleTemp.isBlank()) {
                                            routes.find { it.route == destinationRoute }?.title ?: ""
                                        } else {
                                            "$titleTemp - Request"
                                        }
                                    } else {
                                        title = routes.find { it.route == destinationRoute }?.title ?: ""
                                    }
                                }
                            }
                        }

                        Text(title)
                    },
                )
            }
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
                                        @Suppress("KotlinConstantConditions")
                                        if (!profileUrl.isNullOrBlank() && BuildConfig.FLAVOR != "offline") {
                                            AsyncImage(
                                                profileUrl,
                                                it.route,
                                                Modifier
                                                    .clip(
                                                        RoundedCornerShape(50),
                                                    )
                                                    .height(28.dp)
                                                    .width(28.dp),
                                            )
                                        } else {
                                            Icon(
                                                Icons.Outlined.Person,
                                                it.route,
                                                modifier = Modifier.border(
                                                    2.dp,
                                                    Color.fromHex(account.signer.keyPair.pubKey.toHexKey().slice(0..5)),
                                                    CircleShape,
                                                ),
                                            )
                                        }
                                    } else {
                                        Icon(
                                            painterResource(it.icon),
                                            it.route,
                                            tint = if (selected) Color.Black else if (isSystemInDarkTheme()) Color.White else Color.Black,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            } else if (destinationRoute != "create" && destinationRoute != "loginPage") {
                val localBackButtonTitle = routes.find { it.route == navController.previousBackStackEntry?.destination?.route }?.title ?: ""
                if (localBackButtonTitle.isNotBlank()) {
                    BottomAppBar {
                        IconRow(
                            center = true,
                            title = if (destinationRoute.startsWith("login")) {
                                stringResource(R.string.go_back)
                            } else if (destinationRoute.startsWith("NewNsecBunkerCreated/")) {
                                stringResource(R.string.back_to, localBackButtonTitle)
                            } else {
                                if (destinationRoute == "NewNsecBunker") {
                                    stringResource(R.string.back_to, stringResource(R.string.add_a_new_application))
                                } else {
                                    stringResource(R.string.back_to, localBackButtonTitle)
                                }
                            },
                            icon = ImageVector.vectorResource(R.drawable.back),
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
            @Suppress("KotlinConstantConditions")
            if (navBackStackEntry?.destination?.route == Route.Applications.route && BuildConfig.FLAVOR != "offline") {
                NewBunkerFloatingButton(
                    onClick = {
                        navController.navigate(Route.NewApplication.route)
                    },
                )
            } else if (navBackStackEntry?.destination?.route == Route.ActiveRelays.route) {
                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    FloatingActionButton(
                        modifier = Modifier
                            .padding(end = 8.dp),
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                            pressedElevation = 0.dp,
                        ),
                        onClick = {
                            navController.navigate(Route.DefaultRelays.route)
                        },
                        shape = RoundedCornerShape(24),
                    ) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.settings),
                            contentDescription = stringResource(R.string.connect_app),
                            tint = Color.Black,
                        )
                    }
                }
            }
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
                            storageHelper = storageHelper,
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
                            database,
                            navController,
                            onRemoveIntentData,
                            onLoading = {
                                isLoading = it
                            },
                        )
                    },
                )

                composable(
                    Route.Applications.route,
                    content = {
                        PermissionsScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
                            account = account,
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
                                .verticalScroll(rememberScrollState())
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
                        AccountBackupScreen(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
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
                                    .verticalScroll(rememberScrollState())
                                    .padding(padding)
                                    .padding(horizontal = verticalPadding)
                                    .padding(top = verticalPadding * 1.5f),
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
                        ActiveRelaysScreen(
                            navController = navController,
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
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
                        NewApplicationScreen(
                            account = account,
                            accountStateViewModel = accountStateViewModel,
                            navController = navController,
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
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
                                .verticalScroll(rememberScrollState())
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
                            NewNsecBunkerCreatedScreen(
                                database = database,
                                account = account,
                                key = key,
                                modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
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
                                database = database,
                                key = key,
                                paddingValues = PaddingValues(
                                    top = padding.calculateTopPadding() + (verticalPadding * 1.5f),
                                    bottom = padding.calculateBottomPadding(),
                                    start = padding.calculateStartPadding(LayoutDirection.Ltr) + verticalPadding,
                                    end = padding.calculateEndPadding(LayoutDirection.Ltr) + verticalPadding,
                                ),
                                modifier =
                                Modifier
                                    .fillMaxSize(),
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
                            EditConfigurationScreen(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(padding)
                                    .padding(horizontal = verticalPadding)
                                    .padding(top = verticalPadding * 1.5f),
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
                        SeeDetailsScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
                        )
                    },
                )

                composable(
                    Route.RelaysScreen.route,
                    content = {
                        RelaysScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
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
                        DefaultProfileRelaysScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
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
                        ConnectOrbotScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(padding)
                                .padding(horizontal = verticalPadding)
                                .padding(top = verticalPadding * 1.5f),
                            onPost = {
                                LocalPreferences.updateProxy(context, true, it)
                                scope.launch(Dispatchers.IO) {
                                    NotificationDataSource.stopSync()
                                    NostrSigner.getInstance().checkForNewRelays()
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
                            account = account,
                        )
                    },
                )

                composable(
                    Route.EditProfile.route,
                    arguments = listOf(navArgument("key") { type = NavType.StringType }),
                    content = {
                        it.arguments?.getString("key")?.let { key ->
                            EditProfileScreen(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
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
            }
        }
    }
}
