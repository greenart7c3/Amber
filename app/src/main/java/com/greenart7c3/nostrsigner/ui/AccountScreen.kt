package com.greenart7c3.nostrsigner.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.greenart7c3.nostrsigner.models.IntentData

@Composable
fun AccountScreen(
    accountStateViewModel: AccountStateViewModel,
    event: IntentData?
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
                    MainScreen(state.account, accountStateViewModel, event)
                }
            }
        }
    }
}
