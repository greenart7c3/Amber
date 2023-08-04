package com.greenart7c3.nostrsigner.ui

import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.toHexKey
import com.greenart7c3.nostrsigner.service.model.Event
import java.net.URLDecoder

@Composable
fun AccountScreen(
    accountStateViewModel: AccountStateViewModel,
    intent: Intent
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
                    var intentData: IntentData? = null
                    if (intent.data != null) {
                        var data = URLDecoder.decode(intent.data?.toString()?.replace("+", "%2b") ?: "", "utf-8").replace("nostrsigner:", "")
                        val split = data.split(";")
                        var name = ""
                        if (split.isNotEmpty()) {
                            if (split.last().lowercase().contains("name=")) {
                                name = split.last().replace("name=", "")
                                val newList = split.toList().dropLast(1)
                                data = newList.joinToString("")
                            }
                        }
                        var event = Event.fromJson(data)
                        if (event.pubKey.isEmpty()) {
                            event = Event.setPubKeyIfEmpty(event, state.account.keyPair.pubKey.toHexKey())
                        }
                        intentData = IntentData(event, name)
                    }

                    MainScreen(state.account, accountStateViewModel, intentData)
                }
            }
        }
    }
}
