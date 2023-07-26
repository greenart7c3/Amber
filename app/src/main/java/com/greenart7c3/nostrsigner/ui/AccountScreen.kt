package com.greenart7c3.nostrsigner.ui

import android.annotation.SuppressLint
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Absolute.Center
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.MainActivity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.service.model.Event
import com.greenart7c3.nostrsigner.service.relays.Relay
import com.greenart7c3.nostrsigner.ui.actions.ButtonBorder
import com.greenart7c3.nostrsigner.ui.actions.RelaySelectionDialog
import org.json.JSONObject

@Composable
fun AccountScreen(
    accountStateViewModel: AccountStateViewModel,
    event: IntentData?,
    mainActivity: MainActivity
) {
    val accountState by accountStateViewModel.accountContent.collectAsState()

    Column {
        Crossfade(targetState = accountState, animationSpec = tween(durationMillis = 100)) { state ->
            when (state) {
                is AccountState.LoggedOff -> {
                    LoginPage(accountStateViewModel)
                }
                is AccountState.LoggedIn -> {
                    MainScreen(state.account, event, mainActivity)
                }
            }
        }
    }
}

@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Composable
fun MainScreen(account: Account, json: IntentData?, mainActivity: MainActivity) {
    val event = remember {
        mutableStateOf<Event?>(null)
    }

    val relays = listOf<Relay>().toMutableList()
    var showRelaysDialog by remember {
        mutableStateOf(false)
    }
    val relaysToPost = remember { mutableListOf<Relay>() }

    Scaffold(
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
                        if (relaysToPost.isEmpty()) {
                            relaysToPost.addAll(relays)
                        }

                        if (event.value == null) {
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
