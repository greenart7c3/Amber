package com.greenart7c3.nostrsigner.ui

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.nostrsigner
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.coroutines.flow.MutableStateFlow

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun AccountScreen(
    accountStateViewModel: AccountStateViewModel,
    intent: Intent,
    packageName: String?,
    appName: String?,
    flow: MutableStateFlow<List<IntentData>>
) {
    val accountState by accountStateViewModel.accountContent.collectAsState()
    val intents by flow.collectAsState(initial = emptyList())

    Column {
        Crossfade(
            targetState = accountState,
            animationSpec = tween(durationMillis = 100),
            label = "AccountScreen"
        ) { state ->
            when (state) {
                is AccountState.LoggedOff -> {
                    LoginPage(accountStateViewModel)
                }
                is AccountState.LoggedIn -> {
                    val intentData = IntentUtils.getIntentData(intent, packageName)
                    if (intentData != null) {
                        if (intents.none { item -> item.id == intentData.id }) {
                            flow.value = listOf(intentData)
                        }
                    }

                    val newIntents = intents.ifEmpty {
                        if (intentData == null) {
                            listOf()
                        } else {
                            listOf(intentData)
                        }
                    }
                    val database = nostrsigner.instance.getDatabase(state.account.keyPair.pubKey.toNpub())

                    MainScreen(state.account, accountStateViewModel, newIntents, packageName, appName, state.route, database)
                }
            }
        }
    }
}
