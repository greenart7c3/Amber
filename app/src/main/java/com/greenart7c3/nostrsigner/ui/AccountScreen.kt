package com.greenart7c3.nostrsigner.ui

import android.annotation.SuppressLint
import android.content.Intent
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.relays.AmberListenerSingleton
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.ui.navigation.Route
import kotlinx.coroutines.flow.MutableStateFlow

@SuppressLint("StateFlowValueCalledInComposition", "UnrememberedMutableState")
@Composable
fun AccountScreen(
    accountStateViewModel: AccountStateViewModel,
    intent: Intent?,
    packageName: String?,
    appName: String?,
    flow: MutableStateFlow<List<IntentData>>,
    bunkerRequests: List<AmberBunkerRequest>,
    navController: NavHostController,
) {
    val accountState by accountStateViewModel.accountContent.collectAsState()
    val intents by flow.collectAsState(initial = emptyList())
    val context = LocalContext.current

    Column {
        Crossfade(
            targetState = accountState,
            animationSpec = tween(durationMillis = 100),
            label = "AccountScreen",
        ) { state ->
            when (state) {
                is AccountState.LoggedOff -> {
                    val newNavController = rememberNavController()
                    MainLoginPage(accountStateViewModel, newNavController)
                }
                is AccountState.LoggedIn -> {
                    LaunchedEffect(intent, intents) {
                        intent?.let {
                            IntentUtils.getIntentData(
                                context,
                                intent,
                                packageName,
                                intent.getStringExtra("route"),
                                state.account,
                            )?.let { intentData ->
                                if (intents.none { item -> item.id == intentData.id }) {
                                    val oldIntents = intents.toMutableList()
                                    oldIntents.add(intentData)
                                    flow.value = oldIntents
                                }
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

                    AmberListenerSingleton.accountStateViewModel = accountStateViewModel

                    DisplayErrorMessages(accountStateViewModel)
                    MainScreen(
                        account = state.account,
                        accountStateViewModel = accountStateViewModel,
                        intents = intents,
                        bunkerRequests = bunkerRequests,
                        packageName = packageName,
                        appName = appName,
                        route = localRoute,
                        navController = navController,
                        onRemoveIntentData = { results, type ->
                            val oldIntents = intents.toMutableList()
                            when (type) {
                                IntentResultType.ADD -> {
                                    oldIntents.addAll(results)
                                }

                                IntentResultType.REMOVE -> {
                                    oldIntents.removeAll(results)
                                }
                            }

                            flow.value = oldIntents
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DisplayErrorMessages(accountViewModel: AccountStateViewModel) {
    val context = LocalContext.current
    val openDialogMsg = accountViewModel.toasts.collectAsStateWithLifecycle(null)

    openDialogMsg.value?.let { obj ->
        when (obj) {
            is ResourceToastMsg ->
                if (obj.params != null) {
                    InformationDialog(
                        context.getString(obj.titleResId),
                        context.getString(obj.resourceId, *obj.params),
                    ) {
                        accountViewModel.clearToasts()
                    }
                } else {
                    InformationDialog(
                        context.getString(obj.titleResId),
                        context.getString(obj.resourceId),
                    ) {
                        accountViewModel.clearToasts()
                    }
                }

            is StringToastMsg ->
                InformationDialog(
                    obj.title,
                    obj.msg,
                ) {
                    accountViewModel.clearToasts()
                }
            is ConfirmationToastMsg ->
                InformationDialog(
                    obj.title,
                    obj.msg,
                ) {
                    obj.onOk()
                    accountViewModel.clearToasts()
                }
            is AcceptRejectToastMsg ->
                InformationDialog(
                    obj.title,
                    obj.msg,
                    onAccept = {
                        obj.onAccept()
                        accountViewModel.clearToasts()
                    },
                    onReject = {
                        obj.onReject()
                        accountViewModel.clearToasts()
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
