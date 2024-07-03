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
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.relays.AmberListenerSingleton
import com.greenart7c3.nostrsigner.service.ConnectivityService
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.NotificationDataSource
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@SuppressLint("StateFlowValueCalledInComposition", "UnrememberedMutableState")
@Composable
fun AccountScreen(
    accountStateViewModel: AccountStateViewModel,
    intent: Intent,
    packageName: String?,
    appName: String?,
    flow: MutableStateFlow<List<IntentData>>,
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
                    LoginPage(accountStateViewModel)
                }
                is AccountState.LoggedIn -> {
                    var intentData by remember {
                        mutableStateOf<IntentData?>(null)
                    }
                    LaunchedEffect(intent, intents) {
                        IntentUtils.getIntentData(context, intent, packageName, intent.getStringExtra("route"), state.account) {
                            intentData = it
                        }
                        val data =
                            intents.firstOrNull {
                                it.currentAccount.isNotBlank()
                            }

                        data?.bunkerRequest?.let {
                            if (it.currentAccount.isNotBlank()) {
                                if (LocalPreferences.currentAccount(context) != it.currentAccount) {
                                    accountStateViewModel.switchUser(it.currentAccount, null)
                                }
                            }
                        }
                    }

                    if (intentData != null) {
                        if (intents.none { item -> item.id == intentData!!.id }) {
                            val oldIntents = intents.toMutableList()
                            oldIntents.add(intentData!!)
                            flow.value = oldIntents
                        }
                    }

                    val newIntents =
                        intents.ifEmpty {
                            if (intentData == null) {
                                listOf()
                            } else {
                                listOf(intentData)
                            }
                        }.mapNotNull { it }
                    val database = NostrSigner.getInstance().getDatabase(state.account.keyPair.pubKey.toNpub())
                    val localRoute = mutableStateOf(newIntents.firstNotNullOfOrNull { it.route } ?: state.route)
                    val scope = rememberCoroutineScope()

                    SideEffect {
                        scope.launch(Dispatchers.IO) {
                            @Suppress("KotlinConstantConditions")
                            if (LocalPreferences.getNotificationType(context) == NotificationType.DIRECT && BuildConfig.FLAVOR != "offline") {
                                NostrSigner.getInstance().checkForNewRelays()
                                NotificationDataSource.start()
                                delay(5000)
                                NostrSigner.getInstance().applicationContext.startService(
                                    Intent(NostrSigner.getInstance().applicationContext, ConnectivityService::class.java),
                                )
                            }
                        }
                    }

                    AmberListenerSingleton.accountStateViewModel = accountStateViewModel

                    DisplayErrorMessages(accountStateViewModel)
                    MainScreen(state.account, accountStateViewModel, newIntents, packageName, appName, localRoute, database)
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
        }
    }
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
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(stringResource(R.string.ok))
                }
            }
        },
    )
}
