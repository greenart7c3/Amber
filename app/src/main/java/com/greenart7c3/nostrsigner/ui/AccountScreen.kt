package com.greenart7c3.nostrsigner.ui

import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import com.greenart7c3.nostrsigner.MainViewModel
import com.greenart7c3.nostrsigner.service.IntentUtils

@Composable
fun AccountScreen(
    accountStateViewModel: AccountStateViewModel,
    intent: Intent,
    packageName: String?,
    appName: String?,
    mainViewModel: MainViewModel
) {
    val accountState by accountStateViewModel.accountContent.collectAsState()
    val intents by mainViewModel.intents.observeAsState(mutableListOf())

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
                    val intentData = IntentUtils.getIntentData(intent)
                    if (intentData != null) {
                        if (intents.none { item -> item.id == intentData.id }) {
                            intents.add(intentData)
                        }
                    }

                    val newIntents = intents.ifEmpty {
                        if (intentData == null) {
                            listOf()
                        } else {
                            listOf(intentData)
                        }
                    }

                    MainScreen(state.account, accountStateViewModel, newIntents, packageName, appName, state.route)
                }
            }
        }
    }
}
