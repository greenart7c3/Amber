package com.greenart7c3.nostrsigner.ui

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.DrawerValue
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.rememberDrawerState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.toHexKey
import com.greenart7c3.nostrsigner.service.model.Event
import com.greenart7c3.nostrsigner.service.relays.Relay
import com.greenart7c3.nostrsigner.ui.actions.ButtonBorder
import com.greenart7c3.nostrsigner.ui.actions.RelaySelectionDialog
import com.greenart7c3.nostrsigner.ui.components.Drawer
import com.greenart7c3.nostrsigner.ui.components.MainAppBar
import kotlinx.coroutines.launch
import org.json.JSONObject

@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Composable
fun MainScreen(account: Account, accountStateViewModel: AccountStateViewModel, json: IntentData?) {
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
            Drawer(
                accountStateViewModel = accountStateViewModel,
                account = account,
                scaffoldState = scaffoldState
            )
        },
        topBar = {
            MainAppBar(
                scaffoldState,
                accountStateViewModel,
                account
            )
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
                    Spacer(modifier = Modifier.weight(1f))
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

                                if (relaysToPost.isEmpty()) {
                                    relaysToPost.addAll(relays)
                                }

                                relaysToPost.forEach { item ->
                                    item.connectAndRun { relay ->
                                        relay.send(signedEvent)
                                    }
                                }

                                // mainActivity.finish()
                            }
                        ) {
                            Text("Post")
                        }
                    }
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
