package com.greenart7c3.nostrsigner.ui

import android.annotation.SuppressLint
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.ui.navigation.Route
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@SuppressLint("StateFlowValueCalledInComposition", "UnrememberedMutableState")
@Composable
fun AccountScreen(
    accountStateViewModel: AccountStateViewModel,
    intent: IntentWrapper,
    packageName: String?,
    appName: String?,
    bunkerRequests: ImmutableList<AmberBunkerRequest>,
    navController: NavHostControllerWrapper,
    isExternalRequest: Boolean = false,
) {
    val accountState by accountStateViewModel.accountContent.collectAsState()
    val context = LocalContext.current

    KeystoreFailureWarning()

    Column {
        Crossfade(
            targetState = accountState,
            animationSpec = tween(durationMillis = 100),
            label = "AccountScreen",
        ) { state ->
            when (state) {
                is AccountState.LoggedOff -> {
                    val newNavController = rememberNavController()
                    MainLoginPage(accountStateViewModel, NavHostControllerWrapper(newNavController))
                }
                is AccountState.LoggedIn -> {
                    val intents by IntentUtils.intents.collectAsState(initial = persistentListOf())
                    LaunchedEffect(intent) {
                        intent.intent?.let {
                            IntentUtils.getIntentData(
                                context,
                                it,
                                packageName,
                                it.getStringExtra("route"),
                                state.account,
                            )?.let { intentData ->
                                IntentUtils.addAll(listOf(intentData))
                            }
                        }
                    }
                    val localRoute = mutableStateOf(
                        intents.firstNotNullOfOrNull { it.route } ?: if (bunkerRequests.isNotEmpty()) {
                            Route.IncomingRequest.route
                        } else {
                            null
                        } ?: state.route,
                    )

                    DisplayErrorMessages()
                    MainScreen(
                        account = state.account,
                        accountStateViewModel = accountStateViewModel,
                        intents = intents,
                        bunkerRequests = bunkerRequests,
                        packageName = packageName,
                        appName = appName,
                        route = localRoute,
                        navController = navController,
                        isExternalRequest = isExternalRequest || (intents.isEmpty() && bunkerRequests.isNotEmpty() && (packageName != null || intent.intent?.getStringExtra("route") == Route.IncomingRequest.route)),
                        onRemoveIntentData = { results, type ->
                            when (type) {
                                IntentResultType.ADD -> {
                                    IntentUtils.addAll(results)
                                }

                                IntentResultType.REMOVE -> {
                                    IntentUtils.removeAll(results)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeystoreFailureWarning() {
    val failedAccounts by Amber.instance.keystoreFailedAccounts.collectAsState()
    var dismissed by remember { mutableStateOf(false) }

    if (failedAccounts.isNotEmpty() && !dismissed) {
        val accountList = failedAccounts.joinToString("\n") { "• ${it.take(12)}…" }
        InformationDialog(
            title = stringResource(R.string.keystore_error_title),
            textContent = stringResource(R.string.keystore_error_message, accountList),
        ) {
            dismissed = true
        }
    }
}

@Composable
private fun DisplayErrorMessages() {
    val openDialogMsg = ToastManager.toasts.collectAsState(null)

    openDialogMsg.value?.let { obj ->
        when (obj) {
            is ResourceToastMsg ->
                if (obj.params != null) {
                    val title = stringResource(obj.titleResId)
                    val content = stringResource(obj.resourceId, *obj.params)
                    InformationDialog(
                        title,
                        content,
                    ) {
                        ToastManager.clearToasts()
                    }
                } else {
                    val title = stringResource(obj.titleResId)
                    val content = stringResource(obj.resourceId)
                    InformationDialog(
                        title,
                        content,
                    ) {
                        ToastManager.clearToasts()
                    }
                }

            is StringToastMsg ->
                InformationDialog(
                    obj.title,
                    obj.msg,
                ) {
                    ToastManager.clearToasts()
                }
            is ConfirmationToastMsg ->
                InformationDialog(
                    obj.title,
                    obj.msg,
                ) {
                    obj.onOk()
                    ToastManager.clearToasts()
                }
            is AcceptRejectToastMsg ->
                InformationDialog(
                    obj.title,
                    obj.msg,
                    onAccept = {
                        obj.onAccept()
                        ToastManager.clearToasts()
                    },
                    onReject = {
                        obj.onReject()
                        ToastManager.clearToasts()
                    },
                )
        }
    }
}

@Composable
fun InformationDialog(
    title: String,
    textContent: String,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text(title) },
        text = { SelectionContainer { Text(textContent) } },
        dismissButton = {
            Button(
                onClick = onReject,
                colors = buttonColors,
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        tint = Color.Black,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.no),
                        color = Color.Black,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = buttonColors,
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Done,
                        contentDescription = null,
                        tint = Color.Black,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.yes),
                        color = Color.Black,
                    )
                }
            }
        },
    )
}

@Composable
fun InformationDialog(
    title: String,
    textContent: String,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { SelectionContainer { Text(textContent) } },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = buttonColors,
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Done,
                        contentDescription = null,
                        tint = Color.Black,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.ok),
                        color = Color.Black,
                    )
                }
            }
        },
    )
}
