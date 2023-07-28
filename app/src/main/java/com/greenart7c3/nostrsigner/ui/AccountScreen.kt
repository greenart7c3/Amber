package com.greenart7c3.nostrsigner.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.DrawerValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.rememberDrawerState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.greenart7c3.nostrsigner.MainActivity
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.toHexKey
import com.greenart7c3.nostrsigner.service.model.Event
import com.greenart7c3.nostrsigner.service.relays.Relay
import com.greenart7c3.nostrsigner.service.toNpub
import com.greenart7c3.nostrsigner.service.toNsec
import com.greenart7c3.nostrsigner.ui.actions.ButtonBorder
import com.greenart7c3.nostrsigner.ui.actions.CloseButton
import com.greenart7c3.nostrsigner.ui.actions.RelaySelectionDialog
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material.MaterialRichText
import com.halilibo.richtext.ui.resolveDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun AccountScreen(
    accountStateViewModel: AccountStateViewModel,
    event: IntentData?,
    mainActivity: MainActivity
) {
    val accountState by accountStateViewModel.accountContent.collectAsState()

    Column {
        Crossfade(
            targetState = accountState,
            animationSpec = tween(durationMillis = 100)
        ) { state ->
            when (state) {
                is AccountState.LoggedOff -> {
                    LoginPage(accountStateViewModel)
                }
                is AccountState.LoggedIn -> {
                    MainScreen(state.account, accountStateViewModel, event, mainActivity)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Composable
fun MainScreen(account: Account, accountStateViewModel: AccountStateViewModel, json: IntentData?, mainActivity: MainActivity) {
    val event = remember {
        mutableStateOf<Event?>(null)
    }

    val relays = listOf<Relay>().toMutableList()
    var showRelaysDialog by remember {
        mutableStateOf(false)
    }
    val relaysToPost = remember { mutableListOf<Relay>() }
    val clipboardManager = LocalClipboardManager.current
    val scaffoldState = rememberScaffoldState(rememberDrawerState(DrawerValue.Closed))
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        scaffoldState = scaffoldState,
        drawerContent = {
            var logoutDialog by remember { mutableStateOf(false) }
            var backupDialogOpen by remember { mutableStateOf(false) }

            if (logoutDialog) {
                AlertDialog(
                    title = {
                        Text(text = "Logout")
                    },
                    text = {
                        Text(text = "Logging out deletes all your local information. Make sure to have your private keys backed up to avoid losing your account. Do you want to continue?")
                    },
                    onDismissRequest = {
                        logoutDialog = false
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                logoutDialog = false
                                accountStateViewModel.logOff(account.keyPair.pubKey.toNpub())
                            }
                        ) {
                            Text(text = "Logout")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                logoutDialog = false
                            }
                        ) {
                            Text(text = "Cancel")
                        }
                    }
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colors.background
            ) {
                Column {
                    Spacer(modifier = Modifier.weight(1f))
                    IconRow(
                        title = "Backup Keys",
                        icon = Icons.Default.Key,
                        tint = MaterialTheme.colors.onBackground,
                        onClick = {
                            coroutineScope.launch {
                                scaffoldState.drawerState.close()
                            }
                            backupDialogOpen = true
                        }
                    )
                    Divider(
                        thickness = 0.25.dp,
                        modifier = Modifier.padding(top = 20.dp)
                    )
                    IconRow(
                        title = "Logout",
                        icon = Icons.Default.ManageAccounts,
                        tint = MaterialTheme.colors.onBackground,
                        onClick = {
                            logoutDialog = true
                        }
                    )
                }
            }
            if (backupDialogOpen) {
                AccountBackupDialog(account, onClose = { backupDialogOpen = false })
            }
            BackHandler(enabled = scaffoldState.drawerState.isOpen) {
                coroutineScope.launch { scaffoldState.drawerState.close() }
            }
        },
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                scaffoldState.drawerState.open()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = null,
                            tint = MaterialTheme.colors.onSurface
                        )
                    }
                },
                backgroundColor = Color.Transparent,
                elevation = 0.dp
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                Arrangement.SpaceBetween
            ) {
                Button(
                    shape = ButtonBorder,
                    onClick = {
                        showRelaysDialog = true
                    }
                ) {
                    Text("Relays")
                }

                Button(
                    shape = ButtonBorder,
                    onClick = {
                        if (event.value == null) {
                            return@Button
                        }

                        if (event.value!!.pubKey != account.keyPair.pubKey.toHexKey()) {
                            coroutineScope.launch {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.event_pubkey_is_not_equal_to_current_logged_in_user),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return@Button
                        }

                        val signedEvent = Event.create(
                            account.keyPair.privKey!!,
                            event.value!!.kind,
                            event.value!!.tags,
                            event.value!!.content,
                            event.value!!.createdAt
                        )
                        val rawJson = signedEvent.toJson()
                        clipboardManager.setText(AnnotatedString(rawJson))
                    }
                ) {
                    Text("Sign")
                }

                Button(
                    shape = ButtonBorder,
                    onClick = {
                        if (event.value == null) {
                            return@Button
                        }

                        if (event.value!!.pubKey != account.keyPair.pubKey.toHexKey()) {
                            coroutineScope.launch {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.event_pubkey_is_not_equal_to_current_logged_in_user),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return@Button
                        }

                        val signedEvent = Event.create(
                            account.keyPair.privKey!!,
                            event.value!!.kind,
                            event.value!!.tags,
                            event.value!!.content,
                            event.value!!.createdAt
                        )
//                        val rawJson = signedEvent.toJson()
//                        val resultIntent = Intent()

                        if (relaysToPost.isEmpty()) {
                            relaysToPost.addAll(relays)
                        }

                        relaysToPost.forEach {
                            it.connectAndRun { relay ->
                                relay.send(signedEvent)
                            }
                        }

//                        resultIntent.putExtra("signed_event", rawJson)
//                        mainActivity.setResult(Activity.RESULT_OK, resultIntent)
                        mainActivity.finish()
                    }
                ) {
                    Text("Post")
                }
            }
        }
    ) {
        if (json == null) {
            Column(
                Modifier.fillMaxSize(),
                Arrangement.Center,
                Alignment.CenterHorizontally
            ) {
                Text("No event to sign")
            }
        } else {
            json.let {
                val data = it.data.replace("nostrsigner:", "")
                event.value = Event.fromJson(data)
                val tempRelays = it.relays
                relays.clear()
                tempRelays.forEach { url ->
                    relays.add(Relay(url))
                }
                Column(
                    Modifier.fillMaxSize()
                ) {
                    Text(
                        modifier = Modifier.padding(5.dp),
                        text = JSONObject(data).toString(2)
                    )
                }
            }

            if (showRelaysDialog) {
                RelaySelectionDialog(
                    list = relays,
                    selectRelays = relaysToPost,
                    onClose = {
                        showRelaysDialog = false
                    },
                    onPost = {
                        relaysToPost.clear()
                        relaysToPost.addAll(it)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconRow(title: String, icon: ImageVector, tint: Color, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 15.dp, horizontal = 25.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                null,
                modifier = Modifier.size(22.dp),
                tint = tint
            )
            Text(
                modifier = Modifier.padding(start = 16.dp),
                text = title,
                fontSize = 18.sp
            )
        }
    }
}

@SuppressWarnings("MissingJvmstatic")
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    // Safely update the current `onBack` lambda when a new one is provided
    val currentOnBack by rememberUpdatedState(onBack)
    // Remember in Composition a back callback that calls the `onBack` lambda
    val backCallback = remember {
        object : OnBackPressedCallback(enabled) {
            override fun handleOnBackPressed() {
                currentOnBack()
            }
        }
    }
    // On every successful composition, update the callback with the `enabled` value
    SideEffect {
        backCallback.isEnabled = enabled
    }
    val backDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current) {
        "No OnBackPressedDispatcherOwner was provided via LocalOnBackPressedDispatcherOwner"
    }.onBackPressedDispatcher
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, backDispatcher) {
        // Add callback to the backDispatcher
        backDispatcher.addCallback(lifecycleOwner, backCallback)
        // When the effect leaves the Composition, remove the callback
        onDispose {
            backCallback.remove()
        }
    }
}

@Composable
fun AccountBackupDialog(account: Account, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colors.background)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloseButton(onCancel = onClose)
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    MaterialRichText(
                        style = RichTextStyle().resolveDefaults()
                    ) {
                        Markdown(
                            content = stringResource(R.string.account_backup_tips_md)
                        )
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    NSecCopyButton(account)
                    NPubCopyButton(account)
                }
            }
        }
    }
}

@Composable
private fun NSecCopyButton(
    account: Account
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                copyNSec(context, scope, account, clipboardManager)
            }
        }

    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = {
            authenticate(
                title = context.getString(R.string.copy_my_secret_key),
                context = context,
                scope = scope,
                keyguardLauncher = keyguardLauncher
            ) {
                copyNSec(context, scope, account, clipboardManager)
            }
        },
        shape = ButtonBorder,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = MaterialTheme.colors.primary
        ),
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)
    ) {
        Icon(
            tint = MaterialTheme.colors.onPrimary,
            imageVector = Icons.Default.Key,
            contentDescription = stringResource(R.string.copies_the_nsec_id_your_password_to_the_clipboard_for_backup),
            modifier = Modifier.padding(end = 5.dp)
        )
        Text(stringResource(id = R.string.copy_my_secret_key), color = MaterialTheme.colors.onPrimary)
    }
}

@Composable
private fun NPubCopyButton(
    account: Account
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = {
            clipboardManager.setText(AnnotatedString(account.keyPair.pubKey.toNpub()))
            scope.launch {
                Toast.makeText(
                    context,
                    context.getString(R.string.public_key_npub_copied_to_clipboard),
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        shape = ButtonBorder,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = MaterialTheme.colors.primary
        ),
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)
    ) {
        Icon(
            tint = MaterialTheme.colors.onPrimary,
            imageVector = Icons.Default.Key,
            contentDescription = stringResource(R.string.copies_the_npub_id_your_user_to_the_clipboard_for_backup),
            modifier = Modifier.padding(end = 5.dp)
        )
        Text(
            stringResource(R.string.copy_my_public_key),
            color = MaterialTheme.colors.onPrimary
        )
    }
}

private fun copyNSec(
    context: Context,
    scope: CoroutineScope,
    account: Account,
    clipboardManager: ClipboardManager
) {
    account.keyPair.privKey?.let {
        clipboardManager.setText(AnnotatedString(it.toNsec()))
        scope.launch {
            Toast.makeText(
                context,
                context.getString(R.string.secret_key_copied_to_clipboard),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

fun Context.getAppCompatActivity(): AppCompatActivity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is AppCompatActivity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

fun authenticate(
    title: String,
    context: Context,
    scope: CoroutineScope,
    keyguardLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    onApproved: () -> Unit
) {
    val fragmentContext = context.getAppCompatActivity()!!
    val keyguardManager =
        fragmentContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    if (!keyguardManager.isDeviceSecure) {
        onApproved()
        return
    }

    @Suppress("DEPRECATION")
    fun keyguardPrompt() {
        val intent = keyguardManager.createConfirmDeviceCredentialIntent(
            context.getString(R.string.app_name),
            title
        )

        keyguardLauncher.launch(intent)
    }

    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        keyguardPrompt()
        return
    }

    val biometricManager = BiometricManager.from(context)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(context.getString(R.string.app_name))
        .setSubtitle(title)
        .setAllowedAuthenticators(authenticators)
        .build()

    val biometricPrompt = BiometricPrompt(
        fragmentContext,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)

                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> keyguardPrompt()
                    BiometricPrompt.ERROR_LOCKOUT -> keyguardPrompt()
                    else ->
                        scope.launch {
                            Toast.makeText(
                                context,
                                "${context.getString(R.string.biometric_error)}: $errString",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                scope.launch {
                    Toast.makeText(
                        context,
                        context.getString(R.string.biometric_authentication_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onApproved()
            }
        }
    )

    when (biometricManager.canAuthenticate(authenticators)) {
        BiometricManager.BIOMETRIC_SUCCESS -> biometricPrompt.authenticate(promptInfo)
        else -> keyguardPrompt()
    }
}
