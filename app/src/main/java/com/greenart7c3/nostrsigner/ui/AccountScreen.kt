package com.greenart7c3.nostrsigner.ui

import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.greenart7c3.nostrsigner.service.IntentUtils

@Composable
fun AccountScreen(
    accountStateViewModel: AccountStateViewModel,
    intent: Intent,
    packageName: String?,
    appName: String?
) {
    val accountState by accountStateViewModel.accountContent.collectAsState()

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

                    MainScreen(state.account, accountStateViewModel, intentData, packageName, appName, state.route)
                }
            }
        }
    }
}
